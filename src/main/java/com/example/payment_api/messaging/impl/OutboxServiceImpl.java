package com.example.payment_api.messaging.impl;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.example.payment_api.config.PaymentEventsProperties;
import com.example.payment_api.entity.OutboxEvent;
import com.example.payment_api.event.PaymentEvent;
import com.example.payment_api.messaging.OutboxService;
import com.example.payment_api.messaging.TracePropagation;
import com.example.payment_api.repository.OutboxEventRepository;

import tools.jackson.databind.json.JsonMapper;

@Service
public class OutboxServiceImpl implements OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final PaymentEventsProperties eventsProperties;
    private final JsonMapper jsonMapper;
    private final TracePropagation tracePropagation;

    public OutboxServiceImpl(
            OutboxEventRepository outboxEventRepository,
            PaymentEventsProperties eventsProperties,
            JsonMapper jsonMapper,
            TracePropagation tracePropagation) {
        this.outboxEventRepository = outboxEventRepository;
        this.eventsProperties = eventsProperties;
        this.jsonMapper = jsonMapper;
        this.tracePropagation = tracePropagation;
    }

    // MANDATORY: writing an event outside the business transaction would defeat the outbox.
    @Transactional(propagation = Propagation.MANDATORY)
    @Override
    public void enqueue(PaymentEvent event) {
        // Saved so the relay can continue this trace when it publishes the event.
        Map<String, String> traceContext = tracePropagation.captureCurrentContext();

        outboxEventRepository.save(new OutboxEvent(
                event.eventId().toString(),
                event.eventType(),
                eventsProperties.topic(),
                String.valueOf(event.paymentId()),
                jsonMapper.writeValueAsString(event),
                traceContext.isEmpty() ? null : jsonMapper.writeValueAsString(traceContext)
        ));
    }
}
