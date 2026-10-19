package com.example.payment_api.controller;

import com.example.payment_api.dto.AccessTokenResponse;
import com.example.payment_api.dto.LoginRequest;
import com.example.payment_api.dto.RegisterRequest;
import com.example.payment_api.dto.UserResponse;
import com.example.payment_api.exception.InvalidRefreshTokenException;
import com.example.payment_api.security.RefreshTokenCookies;
import com.example.payment_api.service.AuthService;
import com.example.payment_api.service.AuthTokens;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenCookies refreshTokenCookies;

    public AuthController(AuthService authService, RefreshTokenCookies refreshTokenCookies) {
        this.authService = authService;
        this.refreshTokenCookies = refreshTokenCookies;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public ResponseEntity<AccessTokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return withRefreshCookie(authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AccessTokenResponse> refresh(HttpServletRequest request) {
        String refreshToken = refreshTokenCookies.read(request)
                .orElseThrow(InvalidRefreshTokenException::new);

        return withRefreshCookie(authService.refresh(refreshToken));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        refreshTokenCookies.read(request).ifPresent(authService::logout);

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookies.clear().toString())
                .build();
    }

    private ResponseEntity<AccessTokenResponse> withRefreshCookie(AuthTokens tokens) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookies.create(tokens.refreshToken()).toString())
                .body(new AccessTokenResponse(
                        tokens.accessToken(),
                        "Bearer",
                        tokens.accessTokenExpiresIn()
                ));
    }
}
