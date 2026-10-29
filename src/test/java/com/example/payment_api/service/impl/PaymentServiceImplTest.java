
package com.example.payment_api.service.impl;

import com.example.payment_api.dto.PageResponse;
import com.example.payment_api.dto.PaymentRequest;
import com.example.payment_api.dto.PaymentResponse;
import com.example.payment_api.entity.Payment;
import com.example.payment_api.enums.PaymentStatus;
import com.example.payment_api.event.PaymentCreatedEvent;
import com.example.payment_api.exception.IdempotencyKeyConflictException;
import com.example.payment_api.exception.IdempotencyKeyMismatchException;
import com.example.payment_api.exception.InvalidIdempotencyKeyException;
import com.example.payment_api.exception.PaymentNotFoundException;
import com.example.payment_api.repository.PaymentRepository;
import com.example.payment_api.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentServiceImpl(paymentRepository, eventPublisher);
    }

    @Test
    void shouldCreatePaymentSuccessfully() {
        // Arrange
        PaymentRequest request = new PaymentRequest(
                "1234567890",
                "0987654321",
                new BigDecimal("5000.00"),
                "NGN"
        );

        // Simulate the database assigning an id on save.
        when(paymentRepository.saveAndFlush(any(Payment.class)))
                .thenAnswer(invocation -> {
                    Payment payment = invocation.getArgument(0);
                    ReflectionTestUtils.setField(payment, "id", 1L);
                    return payment;
                });

        // Act
        PaymentResponse response =
                paymentService.createPayment(request, "key-1");

        // Assert
        assertEquals(1L, response.id());
        assertEquals("1234567890", response.sourceAccount());
        assertEquals("0987654321", response.destinationAccount());
        assertEquals(new BigDecimal("5000.00"), response.amount());
        assertEquals("NGN", response.currency());
        assertEquals(PaymentStatus.PENDING, response.status());
        assertNotNull(response.createdAt());

        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).saveAndFlush(saved.capture());
        assertEquals(PaymentStatus.PENDING, saved.getValue().getStatus());
        assertEquals("key-1", saved.getValue().getIdempotencyKey());
        assertNotNull(saved.getValue().getIdempotencyRequestHash());

        // One event describing the saved payment, sent to Kafka after commit.
        ArgumentCaptor<PaymentCreatedEvent> event = ArgumentCaptor.forClass(PaymentCreatedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertNotNull(event.getValue().eventId());
        assertEquals(1L, event.getValue().paymentId());
        assertEquals(new BigDecimal("5000.00"), event.getValue().amount());
        assertEquals("NGN", event.getValue().currency());
        assertEquals(PaymentStatus.PENDING, event.getValue().status());
        assertEquals(response.createdAt(), event.getValue().occurredAt());
    }

    @Test
    void shouldReturnExistingPaymentWhenIdempotencyKeyIsReused() {
        // Arrange: first call creates the payment.
        Payment created = createPaymentWithKey("key-1", new BigDecimal("5000.00"));

        when(paymentRepository.findByIdempotencyKey("key-1"))
                .thenReturn(Optional.of(created));

        // 5000 and 5000.00 are the same amount, so this is a genuine retry.
        PaymentRequest retry = new PaymentRequest(
                "1234567890",
                "0987654321",
                new BigDecimal("5000"),
                "NGN"
        );

        // Act
        PaymentResponse response = paymentService.createPayment(retry, "key-1");

        // Assert: same payment returned, nothing new saved, no second event.
        assertEquals(1L, response.id());
        verify(paymentRepository, times(1)).saveAndFlush(any(Payment.class));
        verify(eventPublisher, times(1)).publishEvent(any(PaymentCreatedEvent.class));
    }

    @Test
    void shouldRejectReusedIdempotencyKeyWithDifferentRequest() {
        // Arrange
        Payment created = createPaymentWithKey("key-1", new BigDecimal("5000.00"));

        when(paymentRepository.findByIdempotencyKey("key-1"))
                .thenReturn(Optional.of(created));

        PaymentRequest differentRequest = new PaymentRequest(
                "1234567890",
                "0987654321",
                new BigDecimal("9999.00"),
                "NGN"
        );

        // Act and Assert
        assertThrows(
                IdempotencyKeyMismatchException.class,
                () -> paymentService.createPayment(differentRequest, "key-1")
        );
        verify(paymentRepository, times(1)).saveAndFlush(any(Payment.class));
    }

    @Test
    void shouldRejectBlankIdempotencyKey() {
        // Arrange
        PaymentRequest request = new PaymentRequest(
                "1234567890",
                "0987654321",
                new BigDecimal("5000.00"),
                "NGN"
        );

        // Act and Assert
        assertThrows(
                InvalidIdempotencyKeyException.class,
                () -> paymentService.createPayment(request, "  ")
        );
        verify(paymentRepository, never()).saveAndFlush(any(Payment.class));
    }

    @Test
    void shouldReturnConflictWhenConcurrentRequestUsedSameKey() {
        // Arrange: the lookup finds nothing, but another request inserts first.
        PaymentRequest request = new PaymentRequest(
                "1234567890",
                "0987654321",
                new BigDecimal("5000.00"),
                "NGN"
        );

        when(paymentRepository.saveAndFlush(any(Payment.class)))
                .thenThrow(constraintViolation("payments.uk_payments_idempotency_key"));

        // Act and Assert
        assertThrows(
                IdempotencyKeyConflictException.class,
                () -> paymentService.createPayment(request, "key-1")
        );
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void shouldRethrowOtherConstraintViolationsOnCreate() {
        // Arrange: a different constraint fails, e.g. the amount CHECK.
        PaymentRequest request = new PaymentRequest(
                "1234567890",
                "0987654321",
                new BigDecimal("5000.00"),
                "NGN"
        );

        DataIntegrityViolationException otherViolation =
                constraintViolation("chk_payment_amount");
        when(paymentRepository.saveAndFlush(any(Payment.class)))
                .thenThrow(otherViolation);

        // Act and Assert: not disguised as an idempotency conflict.
        DataIntegrityViolationException thrown = assertThrows(
                DataIntegrityViolationException.class,
                () -> paymentService.createPayment(request, "key-1")
        );
        assertSame(otherViolation, thrown);
    }

    private static DataIntegrityViolationException constraintViolation(String constraintName) {
        return new DataIntegrityViolationException(
                "could not execute statement",
                new ConstraintViolationException(
                        "constraint violated",
                        new SQLException("constraint violated", "23000"),
                        constraintName));
    }

    private Payment createPaymentWithKey(String idempotencyKey, BigDecimal amount) {
        PaymentRequest request = new PaymentRequest(
                "1234567890",
                "0987654321",
                amount,
                "NGN"
        );

        when(paymentRepository.saveAndFlush(any(Payment.class)))
                .thenAnswer(invocation -> {
                    Payment payment = invocation.getArgument(0);
                    ReflectionTestUtils.setField(payment, "id", 1L);
                    return payment;
                });

        paymentService.createPayment(request, idempotencyKey);

        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).saveAndFlush(saved.capture());

        return saved.getValue();
    }

    @Test
    void shouldReturnPaymentWhenItExists() {
        // Arrange
        Payment payment = new Payment(
                "1234567890",
                "0987654321",
                new BigDecimal("5000.00"),
                "NGN",
                null,
                null
        );
        ReflectionTestUtils.setField(payment, "id", 42L);

        when(paymentRepository.findByIdAndDeletedFalse(42L))
                .thenReturn(Optional.of(payment));

        // Act
        PaymentResponse response = paymentService.getPayment(42L);

        // Assert
        assertEquals(42L, response.id());
        assertEquals("1234567890", response.sourceAccount());
        assertEquals(PaymentStatus.PENDING, response.status());
    }

    @Test
    void shouldThrowExceptionWhenPaymentDoesNotExist() {
        // Arrange
        when(paymentRepository.findByIdAndDeletedFalse(999L))
                .thenReturn(Optional.empty());

        // Act and Assert
        assertThrows(
                PaymentNotFoundException.class,
                () -> paymentService.getPayment(999L)
        );
    }

    @Test
    void shouldUpdatePaymentSuccessfully() {
        // Arrange
        Payment existing = new Payment(
                "1234567890",
                "0987654321",
                new BigDecimal("5000.00"),
                "NGN",
                null,
                null
        );
        ReflectionTestUtils.setField(existing, "id", 42L);

        PaymentRequest request = new PaymentRequest(
                "1111111111",
                "2222222222",
                new BigDecimal("9000.00"),
                "NGN"
        );

        when(paymentRepository.findByIdAndDeletedFalse(42L))
                .thenReturn(Optional.of(existing));
        when(paymentRepository.saveAndFlush(existing))
                .thenReturn(existing);

        // Act
        PaymentResponse response = paymentService.updatePayment(42L, request);

        // Assert
        assertEquals(42L, response.id());
        assertEquals("1111111111", response.sourceAccount());
        assertEquals("2222222222", response.destinationAccount());
        assertEquals(new BigDecimal("9000.00"), response.amount());
        assertEquals(PaymentStatus.PENDING, response.status());
        assertEquals(existing.getCreatedAt(), response.createdAt());
    }

    @Test
    void shouldThrowExceptionWhenUpdatingMissingPayment() {
        // Arrange
        PaymentRequest request = new PaymentRequest(
                "1111111111",
                "2222222222",
                new BigDecimal("9000.00"),
                "NGN"
        );

        when(paymentRepository.findByIdAndDeletedFalse(999L))
                .thenReturn(Optional.empty());

        // Act and Assert
        assertThrows(
                PaymentNotFoundException.class,
                () -> paymentService.updatePayment(999L, request)
        );
        verify(paymentRepository, never()).saveAndFlush(any(Payment.class));
    }

    @Test
    void shouldDeletePaymentSuccessfully() {
        // Arrange
        Payment existing = new Payment(
                "1234567890",
                "0987654321",
                new BigDecimal("5000.00"),
                "NGN",
                null,
                null
        );

        when(paymentRepository.findByIdAndDeletedFalse(42L))
                .thenReturn(Optional.of(existing));

        // Act
        paymentService.deletePayment(42L);

        // Assert: the row is flagged, not removed.
        assertTrue(existing.isDeleted());
        verify(paymentRepository).saveAndFlush(existing);
        verify(paymentRepository, never()).delete(any(Payment.class));
    }

    @Test
    void shouldThrowExceptionWhenDeletingMissingPayment() {
        // Arrange
        when(paymentRepository.findByIdAndDeletedFalse(999L))
                .thenReturn(Optional.empty());

        // Act and Assert
        assertThrows(
                PaymentNotFoundException.class,
                () -> paymentService.deletePayment(999L)
        );
        verify(paymentRepository, never()).delete(any(Payment.class));
    }

    @Test
    void shouldReturnPageOfNonDeletedPaymentsNewestFirst() {
        // Arrange
        Payment newer = payment(2L);
        Payment older = payment(1L);
        Pageable expectedPageable = PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "id"));

        // 2 of 5 payments on the first page.
        when(paymentRepository.findAllByDeletedFalse(expectedPageable))
                .thenReturn(new PageImpl<>(List.of(newer, older), expectedPageable, 5));

        // Act
        PageResponse<PaymentResponse> response = paymentService.listPayments(null, 0, 2);

        // Assert
        assertEquals(List.of(2L, 1L), response.content().stream().map(PaymentResponse::id).toList());
        assertEquals(0, response.page());
        assertEquals(2, response.size());
        assertEquals(5, response.totalElements());
        assertEquals(3, response.totalPages());
        verify(paymentRepository, never()).findAllByDeletedFalseAndStatus(any(), any());
    }

    @Test
    void shouldFilterPaymentsByStatus() {
        // Arrange
        Payment pending = payment(7L);
        Pageable expectedPageable = PageRequest.of(1, 20, Sort.by(Sort.Direction.DESC, "id"));
        Page<Payment> page = new PageImpl<>(List.of(pending), expectedPageable, 21);

        when(paymentRepository.findAllByDeletedFalseAndStatus(PaymentStatus.PENDING, expectedPageable))
                .thenReturn(page);

        // Act
        PageResponse<PaymentResponse> response =
                paymentService.listPayments(PaymentStatus.PENDING, 1, 20);

        // Assert
        assertEquals(1, response.content().size());
        assertEquals(PaymentStatus.PENDING, response.content().get(0).status());
        assertEquals(1, response.page());
        assertEquals(21, response.totalElements());
        verify(paymentRepository, never()).findAllByDeletedFalse(any());
    }

    private static Payment payment(Long id) {
        Payment payment = new Payment(
                "1234567890",
                "0987654321",
                new BigDecimal("5000.00"),
                "NGN",
                null,
                null
        );
        ReflectionTestUtils.setField(payment, "id", id);
        return payment;
    }
}
