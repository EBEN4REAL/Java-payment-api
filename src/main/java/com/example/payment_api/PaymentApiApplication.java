package com.example.payment_api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PaymentApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(
                PaymentApiApplication.class,
                args
        );
    }
}