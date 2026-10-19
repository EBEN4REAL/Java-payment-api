package com.example.payment_api.service;

import com.example.payment_api.dto.LoginRequest;
import com.example.payment_api.dto.RegisterRequest;
import com.example.payment_api.dto.UserResponse;

public interface AuthService {

    /**
     * Creates a user with the USER role.
     *
     * @throws com.example.payment_api.exception.EmailAlreadyRegisteredException if the email is taken
     * @throws com.example.payment_api.exception.InvalidPasswordException if the password is longer than BCrypt supports
     */
    UserResponse register(RegisterRequest request);

    /**
     * Checks the credentials and issues an access token and a new refresh token.
     *
     * @throws com.example.payment_api.exception.InvalidCredentialsException if the email or password is wrong
     */
    AuthTokens login(LoginRequest request);

    /**
     * Exchanges a refresh token for a new token pair. The old refresh token is revoked.
     * Presenting an already-revoked token revokes every active token for that user.
     *
     * @throws com.example.payment_api.exception.InvalidRefreshTokenException if the token is unknown, revoked or expired
     */
    AuthTokens refresh(String rawRefreshToken);

    /** Revokes the refresh token. Unknown or already-revoked tokens are ignored. */
    void logout(String rawRefreshToken);
}
