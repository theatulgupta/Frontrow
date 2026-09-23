package com.frontrow.outbox;

import com.frontrow.config.FrontrowProperties;
import com.frontrow.config.KafkaConfig;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxStore store;
    private final TransactionTemplate transactions;
    private final KafkaTemplate<String, String> kafka;
    private final FrontrowProperties properties;

    public OutboxRelay(
            OutboxStore store,
            TransactionTemplate transactions,
            KafkaTemplate<String, String> kafka,
            FrontrowProperties properties,
            MeterRegistry registry) {
        this.store = store;
        this.transactions = transactions;
        this.kafka = kafka;
        this.properties = properties;
        Gauge.builder("frontrow.outbox.pending", store, OutboxStore::countPending).register(registry);
    }

    @Scheduled(cron = "0 0 3 * * *")
    public void purgePublished() {
        if (properties.getOutbox().isRelayEnabled()) {
            store.purgePublished();
        }
    }

    @Scheduled(fixedDelayString = "${frontrow.outbox.poll-interval}")
    public void relay() {
        if (!properties.getOutbox().isRelayEnabled()) {
            return;
        }
        transactions.executeWithoutResult(status -> store.sweepStuck());
        List<OutboxMessage> claimed = transactions.execute(status -> store.claimPending(50));
        if (claimed == null) {
            return;
        }
        for (OutboxMessage message : claimed) {
            try {
                kafka.send(KafkaConfig.PAYMENT_REQUESTED, message.aggregateId().toString(), message.payload())
                        .get(5, TimeUnit.SECONDS);
                transactions.executeWithoutResult(status -> store.markPublished(message.id()));
            } catch (Exception exception) {
                log.warn("outbox_publish_failed id={} bookingId={}", message.id(), message.aggregateId(), exception);
            }
        }
    }
}
