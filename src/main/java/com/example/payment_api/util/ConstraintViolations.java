package com.example.payment_api.util;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

public final class ConstraintViolations {

    private ConstraintViolations() {}

    /**
     * Returns true if the exception was caused by the named database constraint.
     *
     * Uses the constraint name Hibernate extracts from the driver error, not the
     * message text, so it doesn't depend on the database's message wording.
     */
    public static boolean isViolationOf(DataIntegrityViolationException ex, String constraintName) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                return matches(violation.getConstraintName(), constraintName);
            }
        }
        return false;
    }

    // MySQL 8 reports the name with the table in front, e.g. "payments.uk_payments_idempotency_key".
    private static boolean matches(String reportedName, String constraintName) {
        if (reportedName == null) {
            return false;
        }
        return reportedName.equalsIgnoreCase(constraintName)
                || reportedName.toLowerCase().endsWith("." + constraintName.toLowerCase());
    }
}
