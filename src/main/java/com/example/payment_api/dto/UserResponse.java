package com.example.payment_api.dto;

import java.util.List;

public record UserResponse(
        Long id,
        String email,
        List<String> roles
) {}
