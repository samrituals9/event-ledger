package com.ledger.gateway.client;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@Component
public class AccountClient {

    private final RestClient restClient;

    public AccountClient(RestClient accountRestClient) {
        this.restClient = accountRestClient;
    }

    public void applyTransaction(String accountId, Map<String, Object> payload) {
        try {
            restClient.post()
                    .uri("/accounts/{accountId}/transactions", accountId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new AccountServiceException("Account Service call failed: " + e.getMessage(), e);
        }
    }
}
