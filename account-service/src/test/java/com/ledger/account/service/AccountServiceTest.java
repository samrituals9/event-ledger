package com.ledger.account.service;

import com.ledger.account.dto.TransactionRequest;
import com.ledger.account.dto.TransactionResponse;
import com.ledger.account.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AccountServiceTest {

    @Autowired
    private AccountService service;

    @Autowired
    private TransactionRepository repository;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    private TransactionRequest req(String eventId, String type, String amount, String timestamp) {
        TransactionRequest r = new TransactionRequest();
        r.setEventId(eventId);
        r.setType(type);
        r.setAmount(new BigDecimal(amount));
        r.setCurrency("USD");
        r.setEventTimestamp(Instant.parse(timestamp));
        return r;
    }

    @Test
    void creditAndDebitProduceCorrectBalance() {
        service.applyTransaction("acct-1", req("e1", "CREDIT", "150.00", "2026-05-15T14:00:00Z"), "trace-1");
        service.applyTransaction("acct-1", req("e2", "DEBIT", "50.00", "2026-05-15T15:00:00Z"), "trace-1");

        BigDecimal balance = service.computeBalance("acct-1");

        assertThat(balance).isEqualByComparingTo("100.00");
    }

    @Test
    void duplicateEventIdDoesNotDoubleCount() {
        service.applyTransaction("acct-1", req("e1", "CREDIT", "150.00", "2026-05-15T14:00:00Z"), "trace-1");

        AccountService.TransactionResult second =
                service.applyTransaction("acct-1", req("e1", "CREDIT", "150.00", "2026-05-15T14:00:00Z"), "trace-1");

        assertThat(second.isDuplicate()).isTrue();
        assertThat(service.computeBalance("acct-1")).isEqualByComparingTo("150.00");
        assertThat(repository.findAll()).hasSize(1);
    }

    @Test
    void unknownTypeIsRejected() {
        try {
            service.applyTransaction("acct-1", req("e1", "TRANSFER", "10.00", "2026-05-15T14:00:00Z"), "trace-1");
            assertThat(false).as("expected IllegalArgumentException").isTrue();
        } catch (IllegalArgumentException expected) {
            assertThat(expected.getMessage()).contains("CREDIT or DEBIT");
        }
    }

    @Test
    void transactionsSortedByEventTimestampRegardlessOfArrivalOrder() {
        service.applyTransaction("acct-1", req("e2", "CREDIT", "20.00", "2026-05-15T16:00:00Z"), "trace-1");
        service.applyTransaction("acct-1", req("e1", "CREDIT", "10.00", "2026-05-15T14:00:00Z"), "trace-1");
        service.applyTransaction("acct-1", req("e3", "CREDIT", "30.00", "2026-05-15T18:00:00Z"), "trace-1");

        List<TransactionResponse> txns = service.getTransactions("acct-1");

        assertThat(txns).extracting(TransactionResponse::getEventId)
                .containsExactly("e1", "e2", "e3");
        assertThat(txns.get(0).getEventTimestamp()).isEqualTo(Instant.parse("2026-05-15T14:00:00Z"));
        assertThat(txns.get(2).getEventTimestamp()).isEqualTo(Instant.parse("2026-05-15T18:00:00Z"));
    }
}
