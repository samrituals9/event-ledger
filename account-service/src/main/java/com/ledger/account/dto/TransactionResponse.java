package com.ledger.account.dto;

import com.ledger.account.model.Transaction;
import java.math.BigDecimal;
import java.time.Instant;

public class TransactionResponse {

    private String eventId;
    private String accountId;
    private String type;
    private BigDecimal amount;
    private String currency;
    private Instant eventTimestamp;

    public static TransactionResponse from(Transaction t) {
        TransactionResponse r = new TransactionResponse();
        r.eventId = t.getEventId();
        r.accountId = t.getAccountId();
        r.type = t.getType();
        r.amount = t.getAmount();
        r.currency = t.getCurrency();
        r.eventTimestamp = t.getEventTimestamp();
        return r;
    }

    public String getEventId() { return eventId; }
    public String getAccountId() { return accountId; }
    public String getType() { return type; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public Instant getEventTimestamp() { return eventTimestamp; }
}
