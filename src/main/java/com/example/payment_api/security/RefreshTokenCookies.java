package com.example.payment_api.security;

import java.time.Duration;
import java.util.Optional;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

import com.example.payment_api.config.JwtProperties;
import com.example.payment_api.config.RefreshCookieProperties;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Builds and reads the refresh token cookie.
 *
 * HttpOnly keeps it away from JavaScript (so XSS can't steal it), Secure limits it
 * to HTTPS, SameSite=Strict stops other sites from triggering refresh or logout
 * with it, and the path limits it to the auth endpoints.
 */
@Component
public class RefreshTokenCookies {

    private final RefreshCookieProperties cookieProperties;
    private final JwtProperties jwtProperties;

    public RefreshTokenCookies(RefreshCookieProperties cookieProperties, JwtProperties jwtProperties) {
        this.cookieProperties = cookieProperties;
        this.jwtProperties = jwtProperties;
    }

    public ResponseCookie create(String refreshToken) {
        return baseCookie(refreshToken)
                .maxAge(jwtProperties.refreshTokenTtl())
                .build();
    }

    // Tells the browser to delete the cookie.
    public ResponseCookie clear() {
        return baseCookie("")
                .maxAge(Duration.ZERO)
                .build();
    }

    @SuppressWarnings("null")
    public Optional<String> read(HttpServletRequest request) {
        return Optional.ofNullable(WebUtils.getCookie(request, cookieProperties.name()))
                .map(Cookie::getValue)
                .filter(value -> !value.isBlank());
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(cookieProperties.name(), value)
                .httpOnly(true)
                .secure(cookieProperties.secure())
                .sameSite(cookieProperties.sameSite())
                .path(cookieProperties.path());
    }
}
