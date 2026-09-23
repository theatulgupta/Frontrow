package com.frontrow.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    public static final String PAYMENT_REQUESTED = "frontrow.booking.payment-requested";

    @Bean
    public NewTopic paymentRequestedTopic() {
        return TopicBuilder.name(PAYMENT_REQUESTED).partitions(3).replicas(1).build();
    }
}
