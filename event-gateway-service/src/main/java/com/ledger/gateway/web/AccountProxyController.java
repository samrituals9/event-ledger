package com.ledger.gateway.web;

import com.ledger.gateway.client.AccountClient;
import com.ledger.gateway.metrics.MetricsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class AccountProxyController {

    private final AccountClient accountClient;
    private final MetricsService metrics;

    public AccountProxyController(AccountClient accountClient, MetricsService metrics) {
        this.accountClient = accountClient;
        this.metrics = metrics;
    }

    @GetMapping("/accounts/{accountId}/balance")
    public ResponseEntity<Map<String, Object>> getBalance(@PathVariable String accountId) {
        metrics.increment("requests.balance");

        BigDecimal balance = accountClient.getBalance(accountId);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("accountId", accountId);
        body.put("balance", balance);

        return ResponseEntity.ok(body);
    }
}
