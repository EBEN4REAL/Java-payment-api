
package com.example.payment_api.service;

import com.example.payment_api.config.PaymentProperties;
import com.example.payment_api.dto.PaymentRequest;
import com.example.payment_api.dto.PaymentResponse;
import com.example.payment_api.exception.PaymentNotFoundException;
import com.example.payment_api.model.Payment;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class PaymentService {

    private final PaymentProperties paymentProperties;

    public PaymentService(PaymentProperties paymentProperties) {
        this.paymentProperties = paymentProperties;
    }

    private final ConcurrentHashMap<Long, Payment> payments =
            new ConcurrentHashMap<>();

    private final AtomicLong idGenerator = new AtomicLong(0);

    public PaymentResponse createPayment(PaymentRequest request) {
        long id = idGenerator.incrementAndGet();

        Payment payment = new Payment(
                id,
                request.sourceAccount(),
                request.destinationAccount(),
                request.amount(),
                request.currency(),
                paymentProperties.defaultStatus(),
                Instant.now()
        );

        payments.put(id, payment);

        return new PaymentResponse(
                payment.id(),
                payment.sourceAccount(),
                payment.destinationAccount(),
                payment.amount(),
                payment.currency(),
                payment.status(),
                payment.createdAt()
        );
    }

    public PaymentResponse getPayment(Long id) {
        Payment payment = payments.get(id);

        if (payment == null) {
            throw new PaymentNotFoundException(id);
        }

        return new PaymentResponse(
                payment.id(),
                payment.sourceAccount(),
                payment.destinationAccount(),
                payment.amount(),
                payment.currency(),
                payment.status(),
                payment.createdAt()
        );
    }
}