package com.example.payment_api.messaging;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;

/**
 * Carries a trace across the outbox.
 *
 * <p>The relay publishes events later, on its own thread, so the trace of the request that
 * created an event would normally end at the database. The request's trace context is saved
 * with the event, and restored when the event is sent, so Kafka's producer and consumer spans
 * join the original trace.
 */
@Component
public class TracePropagation {

    private final Tracer tracer;
    private final Propagator propagator;

    public TracePropagation(Tracer tracer, Propagator propagator) {
        this.tracer = tracer;
        this.propagator = propagator;
    }

    // The current trace context as headers (W3C traceparent), or empty outside a trace.
    public Map<String, String> captureCurrentContext() {
        TraceContext context = tracer.currentTraceContext().context();
        if (context == null) {
            return Map.of();
        }
        Map<String, String> headers = new LinkedHashMap<>();
        propagator.inject(context, headers, Map::put);
        return headers;
    }

    // Runs the action in a new span whose parent is the saved context.
    public <T> T callInContext(Map<String, String> savedContext, String spanName, Supplier<T> action) {
        if (savedContext.isEmpty()) {
            return action.get();
        }
        Span span = propagator.extract(savedContext, Map::get).name(spanName).start();
        try (Tracer.SpanInScope scope = tracer.withSpan(span)) {
            return action.get();
        } finally {
            span.end();
        }
    }
}
