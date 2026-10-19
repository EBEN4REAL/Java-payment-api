package com.example.payment_api.service;

// Internal result of login/refresh. The controller sends the refresh token
// as a cookie and only the access token in the response body.
public record AuthTokens(
        String accessToken,
        String refreshToken,
        long accessTokenExpiresIn
) {}
