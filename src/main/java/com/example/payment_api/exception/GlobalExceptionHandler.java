
package com.example.payment_api.exception;

import com.example.payment_api.security.RefreshTokenCookies;

import java.util.Arrays;
import java.util.stream.Collectors;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private final RefreshTokenCookies refreshTokenCookies;

    public GlobalExceptionHandler(RefreshTokenCookies refreshTokenCookies) {
        this.refreshTokenCookies = refreshTokenCookies;
    }

    @ExceptionHandler(PaymentNotFoundException.class)
    public ProblemDetail handlePaymentNotFound(
            PaymentNotFoundException ex) {

        return ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                ex.getMessage()
        );
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ProblemDetail handleMissingHeader(
            MissingRequestHeaderException ex) {

        return ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                ex.getHeaderName() + " header is required"
        );
    }

    @ExceptionHandler(InvalidIdempotencyKeyException.class)
    public ProblemDetail handleInvalidIdempotencyKey(
            InvalidIdempotencyKeyException ex) {

        return ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                ex.getMessage()
        );
    }

    @ExceptionHandler(IdempotencyKeyMismatchException.class)
    public ProblemDetail handleIdempotencyKeyMismatch(
            IdempotencyKeyMismatchException ex) {

        return ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_CONTENT,
                ex.getMessage()
        );
    }

    @ExceptionHandler(IdempotencyKeyConflictException.class)
    public ProblemDetail handleIdempotencyKeyConflict(
            IdempotencyKeyConflictException ex) {

        return ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                ex.getMessage()
        );
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ProblemDetail handleEmailAlreadyRegistered(
            EmailAlreadyRegisteredException ex) {

        return ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                ex.getMessage()
        );
    }

    @ExceptionHandler(InvalidPasswordException.class)
    public ProblemDetail handleInvalidPassword(
            InvalidPasswordException ex) {

        return ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                ex.getMessage()
        );
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ProblemDetail handleInvalidCredentials(
            InvalidCredentialsException ex) {

        return ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED,
                ex.getMessage()
        );
    }

    // Also deletes the cookie, so the browser stops sending a token that no longer works.
    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ProblemDetail> handleInvalidRefreshToken(
            InvalidRefreshTokenException ex) {

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookies.clear().toString())
                .body(ProblemDetail.forStatusAndDetail(
                        HttpStatus.UNAUTHORIZED,
                        ex.getMessage()
                ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationError(
            MethodArgumentNotValidException ex) {

        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("Invalid request");

        return ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                message
        );
    }

    // Constraint failures on query parameters, e.g. ?size=500.
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ProblemDetail handleParameterValidationError(
            HandlerMethodValidationException ex) {

        String message = ex.getParameterValidationResults()
                .stream()
                .findFirst()
                .flatMap(result -> result.getResolvableErrors()
                        .stream()
                        .findFirst()
                        .map(error -> result.getMethodParameter().getParameterName()
                                + " " + error.getDefaultMessage()))
                .orElse("Invalid request");

        return ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                message
        );
    }

    // Values that can't be converted, e.g. ?status=UNKNOWN or ?page=abc.
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(
            MethodArgumentTypeMismatchException ex) {

        String message = "Invalid value '" + ex.getValue() + "' for " + ex.getName();

        Class<?> requiredType = ex.getRequiredType();
        if (requiredType != null && requiredType.isEnum()) {
            message += ". Allowed values: " + Arrays.stream(requiredType.getEnumConstants())
                    .map(Object::toString)
                    .collect(Collectors.joining(", "));
        }

        return ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                message
        );
    }
}
