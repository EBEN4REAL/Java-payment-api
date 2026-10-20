package com.example.payment_api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.payment_api.entity.User;
import com.example.payment_api.enums.RoleName;
import com.example.payment_api.repository.RoleRepository;
import com.example.payment_api.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.http.Cookie;

@SpringBootTest
@AutoConfigureMockMvc
class AuthIntegrationTest {

    private static final String PASSWORD = "correct-horse-battery";
    private static final String REFRESH_COOKIE = "refresh_token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void shouldRegisterUser() throws Exception {
        String email = uniqueEmail();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentialsJson(email, PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.roles.length()").value(1))
                .andExpect(jsonPath("$.roles[0]").value("USER"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void shouldReturn409WhenEmailIsAlreadyRegistered() throws Exception {
        String email = uniqueEmail();
        register(email);

        // Emails are case-insensitive.
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentialsJson(email.toUpperCase(), PASSWORD)))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldReturn400WhenPasswordIsTooShort() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentialsJson(uniqueEmail(), "short")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail")
                        .value("Password must be between 12 and 72 characters"));
    }

    @Test
    void shouldLoginAndAccessPaymentsWithAccessToken() throws Exception {
        String email = uniqueEmail();
        register(email);

        String accessToken = accessToken(login(email, PASSWORD));

        mockMvc.perform(get("/api/payments")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    @Test
    void shouldReturnSame401ForWrongPasswordAndUnknownEmail() throws Exception {
        String email = uniqueEmail();
        register(email);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentialsJson(email, "wrong-password-123")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentialsJson(uniqueEmail(), PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }

    @Test
    void shouldPutUserRolesFromRolesTableIntoAccessToken() throws Exception {
        String email = uniqueEmail();
        register(email);

        Jwt token = jwtDecoder.decode(accessToken(login(email, PASSWORD)));

        assertEquals(List.of("USER"), token.getClaimAsStringList("roles"));
    }

    @Test
    void shouldIncludeEveryRoleWhenUserHasSeveral() throws Exception {
        String email = uniqueEmail();
        register(email);

        // Grant ADMIN as well, through the user_roles join table.
        transactionTemplate.executeWithoutResult(status -> {
            User user = userRepository.findByEmail(email).orElseThrow();
            user.addRole(roleRepository.findByName(RoleName.ADMIN).orElseThrow());
        });

        Jwt token = jwtDecoder.decode(accessToken(login(email, PASSWORD)));

        assertEquals(List.of("ADMIN", "USER"), token.getClaimAsStringList("roles"));
    }

    @Test
    void shouldRejectInvalidAccessTokens() throws Exception {
        mockMvc.perform(get("/api/payments")
                        .header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/payments"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectExpiredAccessToken() throws Exception {
        Instant past = Instant.now().minusSeconds(3600);

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("payment-api")
                .issuedAt(past.minusSeconds(900))
                .expiresAt(past)
                .subject("1")
                .claim("roles", List.of("USER"))
                .build();

        String expiredToken = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();

        mockMvc.perform(get("/api/payments")
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldSetRefreshTokenAsSecureHttpOnlyCookie() throws Exception {
        String email = uniqueEmail();
        register(email);

        MvcResult result = login(email, PASSWORD);

        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertNotNull(setCookie);
        assertTrue(setCookie.startsWith(REFRESH_COOKIE + "="));
        assertTrue(setCookie.contains("HttpOnly"));
        assertTrue(setCookie.contains("Secure"));
        assertTrue(setCookie.contains("SameSite=Strict"));
        assertTrue(setCookie.contains("Path=/api/auth"));

        // The refresh token must not be readable from the response body.
        String body = result.getResponse().getContentAsString();
        assertFalse(body.contains("refreshToken"));
    }

    @Test
    void shouldRotateRefreshToken() throws Exception {
        String email = uniqueEmail();
        register(email);

        Cookie oldRefreshCookie = refreshCookie(login(email, PASSWORD));

        MvcResult refreshed = mockMvc.perform(post("/api/auth/refresh")
                        .cookie(oldRefreshCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andReturn();

        String newAccessToken = JsonPath.read(refreshed.getResponse().getContentAsString(), "$.accessToken");
        Cookie newRefreshCookie = refreshCookie(refreshed);
        assertNotEquals(oldRefreshCookie.getValue(), newRefreshCookie.getValue());

        mockMvc.perform(get("/api/payments")
                        .header("Authorization", "Bearer " + newAccessToken))
                .andExpect(status().isOk());

        // The old refresh token can't be used twice.
        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(oldRefreshCookie))
                .andExpect(status().isUnauthorized());

        // Reusing it looks like theft, so the new token is revoked too.
        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(newRefreshCookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturn401AndClearCookieWhenRefreshCookieIsMissingOrInvalid() throws Exception {
        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized());

        MvcResult result = mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie(REFRESH_COOKIE, "not-a-real-token")))
                .andExpect(status().isUnauthorized())
                .andReturn();

        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertNotNull(setCookie);
        assertTrue(setCookie.contains("Max-Age=0"));
    }

    @Test
    void shouldRevokeRefreshTokenAndClearCookieOnLogout() throws Exception {
        String email = uniqueEmail();
        register(email);

        Cookie refreshCookie = refreshCookie(login(email, PASSWORD));

        MvcResult logout = mockMvc.perform(post("/api/auth/logout")
                        .cookie(refreshCookie))
                .andExpect(status().isNoContent())
                .andReturn();

        String setCookie = logout.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertNotNull(setCookie);
        assertTrue(setCookie.contains("Max-Age=0"));

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(refreshCookie))
                .andExpect(status().isUnauthorized());
    }

    private void register(String email) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentialsJson(email, PASSWORD)))
                .andExpect(status().isCreated());
    }

    private MvcResult login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentialsJson(email, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andReturn();
    }

    private static String accessToken(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private static Cookie refreshCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie(REFRESH_COOKIE);
        assertNotNull(cookie, "Expected a refresh token cookie");
        return cookie;
    }

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private static String credentialsJson(String email, String password) {
        return """
                {"email": "%s", "password": "%s"}
                """.formatted(email, password);
    }

}
