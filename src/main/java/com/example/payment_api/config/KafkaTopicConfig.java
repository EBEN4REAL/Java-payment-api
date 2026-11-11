package com.example.payment_api.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

// Spring's KafkaAdmin creates these topics on startup if they don't exist.
// The broker has topic auto-creation disabled, so this is the only place topics come from.
@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic paymentEventsTopic(PaymentEventsProperties properties) {
        return TopicBuilder.name(properties.topic())
                .partitions(properties.partitions())
                .replicas(properties.replicationFactor())
                .build();
    }

    // Same partition count: a failed record goes to the same partition number it came from.
    @Bean
    public NewTopic paymentEventsDeadLetterTopic(PaymentEventsProperties properties) {
        return TopicBuilder.name(properties.deadLetterTopic())
                .partitions(properties.partitions())
                .replicas(properties.replicationFactor())
                .build();
    }
}
