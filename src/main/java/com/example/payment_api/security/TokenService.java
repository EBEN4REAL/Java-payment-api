package com.example.payment_api.security;

import com.example.payment_api.entity.User;

public interface TokenService {

    /** Claim holding the user's roles, e.g. ["USER"]. Mapped to ROLE_ authorities by SecurityConfig. */
    String ROLES_CLAIM = "roles";

    /** Issues a signed, short-lived access token for the user. */
    String createAccessToken(User user);
}
