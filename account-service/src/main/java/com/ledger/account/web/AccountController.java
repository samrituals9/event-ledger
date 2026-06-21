package com.ledger.account.web;

import com.ledger.account.dto.*;
import com.ledger.account.metrics.MetricsService;
import com.ledger.account.service.AccountService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/accounts")
public class AccountController {

    private static final Logger log = LoggerFactory.getLogger(AccountController.class);
    private static final String TRACE_HEADER = "X-Trace-Id";

    private final AccountService service;
    private final MetricsService metrics;

    public AccountController(AccountService service, MetricsService metrics) {
        this.service = service;
        this.metrics = metrics;
    }

    @PostMapping("/{accountId}/transactions")
    public ResponseEntity<TransactionResponse> applyTransaction(
            @PathVariable String accountId,
            @Valid @RequestBody TransactionRequest req,
            @RequestHeader(value = TRACE_HEADER, required = false) String traceIdHeader) {

        String traceId = (traceIdHeader != null && !traceIdHeader.isBlank())
                ? traceIdHeader : UUID.randomUUID().toString();

        metrics.increment("requests.transactions");

        AccountService.TransactionResult result = service.applyTransaction(accountId, req, traceId);

        HttpStatus status = result.isDuplicate() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status)
                .header(TRACE_HEADER, traceId)
                .body(result.getTransaction());
    }

    @GetMapping("/{accountId}/balance")
    public ResponseEntity<BalanceResponse> getBalance(
            @PathVariable String accountId,
            @RequestHeader(value = TRACE_HEADER, required = false) String traceIdHeader) {

        String traceId = (traceIdHeader != null && !traceIdHeader.isBlank())
                ? traceIdHeader : UUID.randomUUID().toString();
        metrics.increment("requests.balance");

        BigDecimal balance = service.computeBalance(accountId);
        log.info("{\"service\":\"account-service\",\"traceId\":\"{}\",\"event\":\"balance_query\",\"accountId\":\"{}\",\"balance\":\"{}\"}",
                traceId, accountId, balance);

        return ResponseEntity.ok()
                .header(TRACE_HEADER, traceId)
                .body(new BalanceResponse(accountId, balance));
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<AccountResponse> getAccount(
            @PathVariable String accountId,
            @RequestHeader(value = TRACE_HEADER, required = false) String traceIdHeader) {

        String traceId = (traceIdHeader != null && !traceIdHeader.isBlank())
                ? traceIdHeader : UUID.randomUUID().toString();
        metrics.increment("requests.account");

        BigDecimal balance = service.computeBalance(accountId);
        List<TransactionResponse> txns = service.getTransactions(accountId);

        return ResponseEntity.ok()
                .header(TRACE_HEADER, traceId)
                .body(new AccountResponse(accountId, balance, txns));
    }
}
