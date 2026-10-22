package com.example.payment_api.util;

import com.example.payment_api.entity.Payment;
import com.example.payment_api.entity.Role;
import com.example.payment_api.enums.RoleName;
import com.example.payment_api.repository.RoleRepository;
import com.example.payment_api.entity.User;
import com.example.payment_api.repository.PaymentRepository;
import com.example.payment_api.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Triggers real constraint violations in MySQL, so the check is verified against
 * the constraint names Hibernate actually reports. Each test rolls back.
 */
@SpringBootTest
@Transactional
class ConstraintViolationsIntegrationTest {

    private static final String EMAIL_CONSTRAINT = "uk_users_email";
    private static final String IDEMPOTENCY_KEY_CONSTRAINT = "uk_payments_idempotency_key";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Test
    void shouldIdentifyDuplicateEmailViolation() {
        String email = "dup-" + UUID.randomUUID() + "@example.com";
        Role userRole = roleRepository.findByName(RoleName.USER).orElseThrow();
        userRepository.saveAndFlush(new User(email, "{noop}hash", userRole));

        DataIntegrityViolationException ex = assertThrows(
                DataIntegrityViolationException.class,
                () -> userRepository.saveAndFlush(new User(email, "{noop}hash", userRole))
        );

        assertTrue(ConstraintViolations.isViolationOf(ex, EMAIL_CONSTRAINT));
        assertFalse(ConstraintViolations.isViolationOf(ex, IDEMPOTENCY_KEY_CONSTRAINT));
    }

    @Test
    void shouldIdentifyDuplicateIdempotencyKeyViolation() {
        String key = UUID.randomUUID().toString();
        paymentRepository.saveAndFlush(payment(new BigDecimal("100.00"), key));

        DataIntegrityViolationException ex = assertThrows(
                DataIntegrityViolationException.class,
                () -> paymentRepository.saveAndFlush(payment(new BigDecimal("100.00"), key))
        );

        assertTrue(ConstraintViolations.isViolationOf(ex, IDEMPOTENCY_KEY_CONSTRAINT));
        assertFalse(ConstraintViolations.isViolationOf(ex, EMAIL_CONSTRAINT));
    }

    @Test
    void shouldNotMistakeOtherConstraintViolationsForIdempotencyKey() {
        // Breaks the CHECK (amount > 0) constraint, not the idempotency key.
        DataIntegrityViolationException ex = assertThrows(
                DataIntegrityViolationException.class,
                () -> paymentRepository.saveAndFlush(
                        payment(new BigDecimal("-1.00"), UUID.randomUUID().toString()))
        );

        assertFalse(ConstraintViolations.isViolationOf(ex, IDEMPOTENCY_KEY_CONSTRAINT));
    }

    private static Payment payment(BigDecimal amount, String idempotencyKey) {
        return new Payment(
                "1234567890",
                "0987654321",
                amount,
                "NGN",
                "PENDING",
                idempotencyKey,
                "0".repeat(64)
        );
    }
}
