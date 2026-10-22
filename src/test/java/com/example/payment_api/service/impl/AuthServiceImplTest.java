package com.example.payment_api.service.impl;

import java.sql.SQLException;
import java.time.Duration;
import java.util.Optional;

import org.hibernate.exception.ConstraintViolationException;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.payment_api.config.JwtProperties;
import com.example.payment_api.dto.RegisterRequest;
import com.example.payment_api.entity.Role;
import com.example.payment_api.entity.User;
import com.example.payment_api.enums.RoleName;
import com.example.payment_api.exception.EmailAlreadyRegisteredException;
import com.example.payment_api.repository.RefreshTokenRepository;
import com.example.payment_api.repository.RoleRepository;
import com.example.payment_api.repository.UserRepository;
import com.example.payment_api.security.TokenService;
import com.example.payment_api.service.AuthService;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TokenService tokenService;

    private AuthService authService;

    private final RegisterRequest request =
            new RegisterRequest("new.user@example.com", "correct-horse-battery");

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode(anyString())).thenReturn("{bcrypt}hash");

        JwtProperties jwtProperties = new JwtProperties(
                "unused-in-these-tests-unused-in-these-tests",
                "payment-api",
                Duration.ofMinutes(15),
                Duration.ofDays(7));

        authService = new AuthServiceImpl(
                userRepository,
                refreshTokenRepository,
                roleRepository,
                passwordEncoder,
                tokenService,
                jwtProperties);
    }

    @Test
    void shouldReportDuplicateEmailWhenConcurrentRegistrationWins() {
        // Arrange: the existence check passes, but another request inserts the email first.
        givenUserRoleExists();
        when(userRepository.existsByEmail("new.user@example.com")).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(constraintViolation("users.uk_users_email"));

        // Act and Assert
        assertThrows(
                EmailAlreadyRegisteredException.class,
                () -> authService.register(request)
        );
    } 

    @Test
    void shouldRethrowOtherConstraintViolationsOnRegister() {
        // Arrange: some other constraint fails. It must not be reported as a duplicate email.
        DataIntegrityViolationException otherViolation = constraintViolation("users.some_other_constraint");

        givenUserRoleExists();
        when(userRepository.existsByEmail("new.user@example.com")).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(otherViolation);

        // Act and Assert
        DataIntegrityViolationException thrown = assertThrows(
                DataIntegrityViolationException.class,
                () -> authService.register(request)
        );
        assertSame(otherViolation, thrown);
    }

    @Test
    void shouldFailClearlyWhenUserRoleIsMissingFromDatabase() {
        // Arrange: the roles table wasn't seeded.
        when(userRepository.existsByEmail("new.user@example.com")).thenReturn(false);
        when(roleRepository.findByName(RoleName.USER)).thenReturn(Optional.empty());

        // Act and Assert
        assertThrows(IllegalStateException.class, () -> authService.register(request));
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    private void givenUserRoleExists() {
        when(roleRepository.findByName(RoleName.USER))
                .thenReturn(Optional.of(new Role(RoleName.USER)));
    }

    private static DataIntegrityViolationException constraintViolation(String constraintName) {
        return new DataIntegrityViolationException(
                "could not execute statement",
                new ConstraintViolationException(
                        "constraint violated",
                        new SQLException("constraint violated", "23000"),
                        constraintName));
    }
}
