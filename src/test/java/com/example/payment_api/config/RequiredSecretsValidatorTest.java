package com.example.payment_api.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

class RequiredSecretsValidatorTest {

    private final RequiredSecretsValidator validator = new RequiredSecretsValidator();
    private final SpringApplication application = new SpringApplication();

    @Test
    void shouldPassWhenAllSecretsAreSet() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.datasource.username", "app")
                .withProperty("spring.datasource.password", "secret")
                .withProperty("security.jwt.secret", "x".repeat(64));

        assertDoesNotThrow(() -> validator.postProcessEnvironment(environment, application));
    }

    @Test
    void shouldListEveryMissingBlankOrUnresolvedSecret() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.datasource.username", "${DB_USERNAME}")
                .withProperty("spring.datasource.password", "   ");

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> validator.postProcessEnvironment(environment, application)
        );

        assertTrue(ex.getMessage().contains(
                "spring.datasource.username, spring.datasource.password, security.jwt.secret"));
    }
}
