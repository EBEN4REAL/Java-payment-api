package com.example.payment_api.dto;

// The refresh token is not in the body: it is sent as an HttpOnly cookie.
public record AccessTokenResponse(
        String accessToken,
        String tokenType,
        long expiresIn
) {}
