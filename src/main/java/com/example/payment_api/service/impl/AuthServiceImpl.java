package com.example.payment_api.service.impl;

import com.example.payment_api.service.AuthService;
import com.example.payment_api.service.AuthTokens;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.payment_api.config.JwtProperties;
import com.example.payment_api.dto.LoginRequest;
import com.example.payment_api.dto.RegisterRequest;
import com.example.payment_api.dto.UserResponse;
import com.example.payment_api.entity.RefreshToken;
import com.example.payment_api.entity.Role;
import com.example.payment_api.enums.RoleName;
import com.example.payment_api.entity.User;
import com.example.payment_api.exception.EmailAlreadyRegisteredException;
import com.example.payment_api.exception.InvalidCredentialsException;
import com.example.payment_api.exception.InvalidPasswordException;
import com.example.payment_api.exception.InvalidRefreshTokenException;
import com.example.payment_api.repository.RefreshTokenRepository;
import com.example.payment_api.repository.RoleRepository;
import com.example.payment_api.repository.UserRepository;
import com.example.payment_api.security.TokenService;
import com.example.payment_api.util.ConstraintViolations;
import com.example.payment_api.util.Hashing;

@Service
public class AuthServiceImpl implements AuthService {

    private static final int MAX_PASSWORD_BYTES = 72;
    private static final int REFRESH_TOKEN_BYTES = 32;
    private static final String EMAIL_CONSTRAINT = "uk_users_email";

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final JwtProperties jwtProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    // Checked when the email doesn't exist, so a login for an unknown email takes
    // as long as one with a wrong password and can't be used to discover accounts.
    private final String dummyPasswordHash;

    public AuthServiceImpl(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            TokenService tokenService,
            JwtProperties jwtProperties) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.jwtProperties = jwtProperties;
        this.dummyPasswordHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    @Transactional
    @Override
    public UserResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());

        // @Size counts characters, but BCrypt's limit is in bytes (non-ASCII characters use more than one).
        if (exceedsBcryptLimit(request.password())) {
            throw new InvalidPasswordException(
                    "Password must be at most " + MAX_PASSWORD_BYTES + " bytes");
        }

        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException();
        }

        // Seeded by Liquibase changeset 006-1; missing means the database is misconfigured.
        Role userRole = roleRepository.findByName(RoleName.USER)
                .orElseThrow(() -> new IllegalStateException("Role USER is missing from the roles table"));

        User user = new User(email, passwordEncoder.encode(request.password()), userRole);

        try {
            User savedUser = userRepository.saveAndFlush(user);

            return new UserResponse(
                    savedUser.getId(),
                    savedUser.getEmail(),
                    savedUser.getRoles().stream().map(role -> role.getName().name()).sorted().toList()
            );
        } catch (DataIntegrityViolationException ex) {
            // Another registration with the same email was saved between the check and the insert.
            // Any other constraint failure is a real error and must not be reported as a duplicate.
            if (ConstraintViolations.isViolationOf(ex, EMAIL_CONSTRAINT)) {
                throw new EmailAlreadyRegisteredException();
            }
            throw ex;
        }
    }

    @Transactional
    @Override
    public AuthTokens login(LoginRequest request) {
        if (exceedsBcryptLimit(request.password())) {
            throw new InvalidCredentialsException();
        }

        User user = userRepository.findByEmail(normalizeEmail(request.email()))
                .orElse(null);

        if (user == null) {
            passwordEncoder.matches(request.password(), dummyPasswordHash);
            throw new InvalidCredentialsException();
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        return issueTokens(user);
    }

    // noRollbackFor keeps the "revoke all tokens" below even though we then throw.
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    @Override
    public AuthTokens refresh(String rawRefreshToken) {
        Instant now = Instant.now();

        RefreshToken refreshToken = refreshTokenRepository
                .findByTokenHash(Hashing.sha256Hex(rawRefreshToken))
                .orElseThrow(InvalidRefreshTokenException::new);

        // A revoked token being used again means it was probably stolen: the
        // legitimate client already swapped it for a new one. Log the user out everywhere.
        if (refreshToken.isRevoked()) {
            refreshTokenRepository.revokeAllActiveForUser(refreshToken.getUser().getId(), now);
            throw new InvalidRefreshTokenException();
        }

        if (refreshToken.isExpired(now)) {
            throw new InvalidRefreshTokenException();
        }

        // Rotation: each refresh token works once and is replaced by a new one.
        refreshToken.revoke(now);

        return issueTokens(refreshToken.getUser());
    }

    // Always succeeds, so callers can't use logout to check whether a token exists.
    @Transactional
    @Override
    public void logout(String rawRefreshToken) {
        refreshTokenRepository.findByTokenHash(Hashing.sha256Hex(rawRefreshToken))
                .filter(refreshToken -> !refreshToken.isRevoked())
                .ifPresent(refreshToken -> refreshToken.revoke(Instant.now()));
    }

    private AuthTokens issueTokens(User user) {
        String accessToken = tokenService.createAccessToken(user);
        String rawRefreshToken = generateRefreshToken();

        refreshTokenRepository.save(new RefreshToken(
                user,
                Hashing.sha256Hex(rawRefreshToken),
                Instant.now().plus(jwtProperties.refreshTokenTtl())
        ));

        return new AuthTokens(
                accessToken,
                rawRefreshToken,
                jwtProperties.accessTokenTtl().toSeconds()
        );
    }

    private String generateRefreshToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);

        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private boolean exceedsBcryptLimit(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES;
    }
}
