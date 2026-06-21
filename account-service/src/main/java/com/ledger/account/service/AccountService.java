package com.ledger.account.service;

import com.ledger.account.dto.*;
import com.ledger.account.metrics.MetricsService;
import com.ledger.account.model.Transaction;
import com.ledger.account.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final TransactionRepository repository;
    private final MetricsService metrics;

    public AccountService(TransactionRepository repository, MetricsService metrics) {
        this.repository = repository;
        this.metrics = metrics;
    }

    public TransactionResult applyTransaction(String accountId, TransactionRequest req, String traceId) {
        String type = req.getType() == null ? null : req.getType().trim().toUpperCase();
        if (!"CREDIT".equals(type) && !"DEBIT".equals(type)) {
            metrics.increment("transactions.rejected");
            throw new IllegalArgumentException("type must be CREDIT or DEBIT");
        }

        Optional<Transaction> existing = repository.findByEventId(req.getEventId());
        if (existing.isPresent()) {
            metrics.increment("transactions.duplicate");
            log.info("{\"service\":\"account-service\",\"traceId\":\"{}\",\"event\":\"duplicate_transaction\",\"eventId\":\"{}\",\"accountId\":\"{}\"}",
                    traceId, req.getEventId(), accountId);
            return new TransactionResult(TransactionResponse.from(existing.get()), true);
        }

        Transaction t = new Transaction();
        t.setEventId(req.getEventId());
        t.setAccountId(accountId);
        t.setType(type);
        t.setAmount(req.getAmount());
        t.setCurrency(req.getCurrency());
        t.setEventTimestamp(req.getEventTimestamp());
        t.setReceivedAt(Instant.now());

        try {
            repository.save(t);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            metrics.increment("transactions.duplicate");
            Transaction now = repository.findByEventId(req.getEventId()).orElseThrow();
            return new TransactionResult(TransactionResponse.from(now), true);
        }

        metrics.increment("transactions.applied");
        log.info("{\"service\":\"account-service\",\"traceId\":\"{}\",\"event\":\"transaction_applied\",\"eventId\":\"{}\",\"accountId\":\"{}\",\"type\":\"{}\",\"amount\":\"{}\"}",
                traceId, req.getEventId(), accountId, type, req.getAmount());

        return new TransactionResult(TransactionResponse.from(t), false);
    }

    public BigDecimal computeBalance(String accountId) {
        List<Transaction> txns = repository.findByAccountIdOrderByEventTimestampAsc(accountId);
        BigDecimal balance = BigDecimal.ZERO;
        for (Transaction t : txns) {
            if ("CREDIT".equals(t.getType())) {
                balance = balance.add(t.getAmount());
            } else {
                balance = balance.subtract(t.getAmount());
            }
        }
        return balance;
    }

    public List<TransactionResponse> getTransactions(String accountId) {
        return repository.findByAccountIdOrderByEventTimestampAsc(accountId)
                .stream()
                .map(TransactionResponse::from)
                .toList();
    }

    public static class TransactionResult {
        private final TransactionResponse transaction;
        private final boolean duplicate;

        public TransactionResult(TransactionResponse transaction, boolean duplicate) {
            this.transaction = transaction;
            this.duplicate = duplicate;
        }

        public TransactionResponse getTransaction() { return transaction; }
        public boolean isDuplicate() { return duplicate; }
    }
}
