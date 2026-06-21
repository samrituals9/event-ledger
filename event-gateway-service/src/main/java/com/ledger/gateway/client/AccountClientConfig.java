package com.ledger.gateway.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class AccountClientConfig {

    @Bean
    public RestClient accountRestClient(RestClient.Builder builder,
                                        @Value("${account.service.base-url}") String baseUrl,
                                        @Value("${account.service.timeout-ms:2000}") int timeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);

        return builder
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }
}
