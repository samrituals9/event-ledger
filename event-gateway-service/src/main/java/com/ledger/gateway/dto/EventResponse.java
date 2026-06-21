package com.ledger.gateway.dto;

import com.ledger.gateway.model.EventEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public class EventResponse {

    private String eventId;
    private String accountId;
    private String type;
    private BigDecimal amount;
    private String currency;
    private Instant eventTimestamp;
    private Map<String, Object> metadata;

    public static EventResponse from(EventEntity e) {
        EventResponse r = new EventResponse();
        r.eventId = e.getEventId();
        r.accountId = e.getAccountId();
        r.type = e.getType();
        r.amount = e.getAmount();
        r.currency = e.getCurrency();
        r.eventTimestamp = e.getEventTimestamp();
        r.metadata = e.getMetadata();
        return r;
    }

    public String getEventId() { return eventId; }
    public String getAccountId() { return accountId; }
    public String getType() { return type; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public Instant getEventTimestamp() { return eventTimestamp; }
    public Map<String, Object> getMetadata() { return metadata; }
}
