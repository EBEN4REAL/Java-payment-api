package com.example.payment_api.config;

import java.net.http.HttpClient;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class BankClientConfig {

    // Built from Spring's RestClient.Builder so calls get metrics and trace propagation.
    // Both timeouts are set: without them a hung bank would block a consumer thread forever.
    @Bean
    public RestClient bankRestClient(RestClient.Builder restClientBuilder, BankProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                // The JDK client defaults to HTTP/2 and, on plain http://, tries an h2c upgrade
                // that some servers cancel for requests with a body. HTTP/1.1 works everywhere.
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());

        return restClientBuilder
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
