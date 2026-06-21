package com.ledger.gateway.client;

import com.ledger.gateway.metrics.MetricsService;
import com.ledger.gateway.trace.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.Map;
import java.util.function.Supplier;

@Component
public class AccountClient {

    private static final Logger log = LoggerFactory.getLogger(AccountClient.class);
    private static final String TRACE_HEADER = "X-Trace-Id";

    private final RestClient restClient;
    private final MetricsService metrics;
    private final int maxAttempts;
    private final long backoffMs;
    private final CircuitBreaker circuitBreaker;

    public AccountClient(RestClient accountRestClient,
                         MetricsService metrics,
                         @Value("${account.client.max-attempts:2}") int maxAttempts,
                         @Value("${account.client.backoff-ms:200}") long backoffMs,
                         @Value("${account.client.cb.failure-threshold:3}") int cbFailureThreshold,
                         @Value("${account.client.cb.open-duration-ms:5000}") long cbOpenDurationMs) {
        this.restClient = accountRestClient;
        this.metrics = metrics;
        this.maxAttempts = maxAttempts;
        this.backoffMs = backoffMs;
        this.circuitBreaker = new CircuitBreaker("account-service", cbFailureThreshold, cbOpenDurationMs);
    }

    public void applyTransaction(String accountId, Map<String, Object> payload) {
        callThroughCircuit("applyTransaction", () -> withRetry("applyTransaction", () -> {
            restClient.post()
                    .uri("/accounts/{accountId}/transactions", accountId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(TRACE_HEADER, currentTraceId())
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();

            return null;
        }));
    }

    public BigDecimal getBalance(String accountId) {
        return callThroughCircuit("getBalance", () -> withRetry("getBalance", () -> {
            BalanceDto dto = restClient.get()
                    .uri("/accounts/{accountId}/balance", accountId)
                    .header(TRACE_HEADER, currentTraceId())
                    .retrieve()
                    .body(BalanceDto.class);

            return dto == null ? null : dto.getBalance();
        }));
    }

    private <T> T callThroughCircuit(String operation, Supplier<T> retryingCall) {
        try {
            return circuitBreaker.execute(retryingCall);
        } catch (CircuitOpenException e) {
            metrics.increment("downstream.circuit_open");

            log.warn("{\"service\":\"event-gateway-service\",\"traceId\":\"{}\",\"event\":\"circuit_open_rejected\",\"operation\":\"{}\"}",
                    currentTraceId(), operation);

            throw new AccountServiceException(e.getMessage(), e);
        } catch (RestClientException e) {
            throw new AccountServiceException(e.getMessage(), e);
        }
    }

    private <T> T withRetry(String operation, Supplier<T> action) {
        RestClientException lastException = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return action.get();
            } catch (RestClientException e) {
                lastException = e;

                log.warn("{\"service\":\"event-gateway-service\",\"traceId\":\"{}\",\"event\":\"account_call_failed\",\"operation\":\"{}\",\"attempt\":{},\"maxAttempts\":{},\"error\":\"{}\"}",
                        currentTraceId(), operation, attempt, maxAttempts, e.getMessage());

                if (attempt < maxAttempts) {
                    sleep(backoffMs * attempt);
                }
            }
        }

        metrics.increment("downstream.failures");

        throw new RestClientException(
                "Account Service call failed after " + maxAttempts + " attempts: "
                        + (lastException == null ? "unknown error" : lastException.getMessage()),
                lastException
        );
    }

    private void sleep(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    private String currentTraceId() {
        String traceId = TraceContext.get();
        return traceId == null ? "" : traceId;
    }

    public CircuitBreaker.State circuitState() {
        return circuitBreaker.getState();
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
