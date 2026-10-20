package com.example.payment_api.config;

import java.util.List;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Stops startup with a clear message when a required secret is missing.
 *
 * Checks the final settings, so it works whether a value is written in
 * application.properties or supplied with a ${...} placeholder. A placeholder that
 * points at nothing is treated as missing: otherwise the app would fail later with a
 * confusing error (e.g. trying to log in to MySQL as the user "${DB_USERNAME}").
 *
 * Runs after the config files have been loaded, but before any bean (such as the
 * database connection) is created.
*/
public class RequiredSecretsValidator implements EnvironmentPostProcessor, Ordered {

    static final List<String> REQUIRED = List.of(
            "spring.datasource.username",
            "spring.datasource.password",
            "security.jwt.secret");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        List<String> missing = REQUIRED.stream()
                .filter(name -> isMissing(environment, name))
                .toList();

        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "Missing required configuration: " + String.join(", ", missing)
                            + ". Set them in src/main/resources/application.properties"
                            + " (see application.properties.example).");
        }
    }

    private static boolean isMissing(ConfigurableEnvironment environment, String name) {
        try {
            String value = environment.getProperty(name);
            return value == null || value.isBlank();
        } catch (IllegalArgumentException ex) {
            // Thrown for a placeholder such as ${DB_USERNAME} that points at nothing.
            return true;
        }
    }

    // After ConfigDataEnvironmentPostProcessor, which loads application.properties.
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
