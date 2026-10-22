package com.example.payment_api.util;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConstraintViolationsTest {

    @Test
    void shouldMatchNameWithTablePrefix() {
        assertTrue(ConstraintViolations.isViolationOf(
                violation("payments.uk_payments_idempotency_key"), "uk_payments_idempotency_key"));
    }

    @Test
    void shouldMatchNameWithoutTablePrefix() {
        assertTrue(ConstraintViolations.isViolationOf(
                violation("uk_users_email"), "uk_users_email"));
    }

    @Test
    void shouldNotMatchConstraintWhoseNameOnlyEndsTheSame() {
        // "xuk_users_email" must not count as "uk_users_email".
        assertFalse(ConstraintViolations.isViolationOf(
                violation("users.xuk_users_email"), "uk_users_email"));
    }

    @Test
    void shouldReturnFalseWhenConstraintNameIsUnknown() {
        assertFalse(ConstraintViolations.isViolationOf(violation(null), "uk_users_email"));
    }

    @Test
    void shouldReturnFalseWhenCauseIsNotAConstraintViolation() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "Data too long", new SQLException("Data too long for column", "22001", 1406));

        assertFalse(ConstraintViolations.isViolationOf(ex, "uk_users_email"));
    }

    static DataIntegrityViolationException violation(String constraintName) {
        return new DataIntegrityViolationException(
                "could not execute statement",
                new ConstraintViolationException(
                        "Duplicate entry",
                        new SQLException("Duplicate entry", "23000", 1062),
                        constraintName));
    }
}
