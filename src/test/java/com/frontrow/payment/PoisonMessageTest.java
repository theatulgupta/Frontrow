package com.frontrow.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.frontrow.config.KafkaConfig;
import com.frontrow.support.IntegrationTestBase;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;

class PoisonMessageTest extends IntegrationTestBase {

    @Autowired
    private KafkaTemplate<String, String> kafka;

    @Autowired
    private ConsumerFactory<String, String> consumerFactory;

    @Test
    void malformedPayloadLandsOnTheDeadLetterTopic() throws Exception {
        kafka.send(KafkaConfig.PAYMENT_REQUESTED, "poison", "{not-json").get(5, TimeUnit.SECONDS);
        try (Consumer<String, String> consumer = consumerFactory.createConsumer("poison-check-" + System.nanoTime(), "poison")) {
            consumer.subscribe(java.util.List.of(KafkaConfig.PAYMENT_REQUESTED_DLT));
            await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(400));
                assertThat(records.count()).isPositive();
            });
        }
    }
}
