package com.ledger.gateway.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class AccountClientConfig {

    @Bean
    public RestClient accountRestClient(RestClient.Builder builder,
                                        @Value("${account.service.base-url}") String baseUrl) {
        return builder.baseUrl(baseUrl).build();
    }
}
