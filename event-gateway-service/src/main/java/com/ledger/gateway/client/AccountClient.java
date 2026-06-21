package com.ledger.gateway.client;

import com.ledger.gateway.trace.TraceContext;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.Map;

@Component
public class AccountClient {

    private static final String TRACE_HEADER = "X-Trace-Id";

    private final RestClient restClient;

    public AccountClient(RestClient accountRestClient) {
        this.restClient = accountRestClient;
    }

    public void applyTransaction(String accountId, Map<String, Object> payload) {
        try {
            restClient.post()
                    .uri("/accounts/{accountId}/transactions", accountId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(TRACE_HEADER, currentTraceId())
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new AccountServiceException("Account Service call failed: " + e.getMessage(), e);
        }
    }

    public BigDecimal getBalance(String accountId) {
        try {
            BalanceDto dto = restClient.get()
                    .uri("/accounts/{accountId}/balance", accountId)
                    .header(TRACE_HEADER, currentTraceId())
                    .retrieve()
                    .body(BalanceDto.class);
            return dto == null ? null : dto.getBalance();
        } catch (RestClientException e) {
            throw new AccountServiceException("Account Service call failed: " + e.getMessage(), e);
        }
    }

    private String currentTraceId() {
        String traceId = TraceContext.get();
        return traceId == null ? "" : traceId;
    }

    public static class BalanceDto {
        private String accountId;
        private BigDecimal balance;

        public String getAccountId() {
            return accountId;
        }

        public void setAccountId(String accountId) {
            this.accountId = accountId;
        }

        public BigDecimal getBalance() {
            return balance;
        }

        public void setBalance(BigDecimal balance) {
            this.balance = balance;
        }
    }
}
