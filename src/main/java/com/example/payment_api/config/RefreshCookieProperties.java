package com.example.payment_api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.refresh-cookie")
public record RefreshCookieProperties(
        String name,
        String path,
        boolean secure,
        String sameSite
) {}
