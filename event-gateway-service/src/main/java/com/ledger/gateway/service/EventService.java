package com.ledger.gateway.service;

import com.ledger.gateway.client.AccountClient;
import com.ledger.gateway.dto.EventRequest;
import com.ledger.gateway.dto.EventResponse;
import com.ledger.gateway.model.EventEntity;
import com.ledger.gateway.repository.EventRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class EventService {

    private final EventRepository repository;
    private final AccountClient accountClient;

    public EventService(EventRepository repository, AccountClient accountClient) {
        this.repository = repository;
        this.accountClient = accountClient;
    }

    public SubmitResult submit(EventRequest req) {
        String type = req.getType() == null ? null : req.getType().trim().toUpperCase();
        if (!"CREDIT".equals(type) && !"DEBIT".equals(type)) {
            throw new IllegalArgumentException("type must be CREDIT or DEBIT");
        }

        Optional<EventEntity> existing = repository.findById(req.getEventId());
        if (existing.isPresent()) {
            return new SubmitResult(EventResponse.from(existing.get()), true);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", req.getEventId());
        payload.put("type", type);
        payload.put("amount", req.getAmount());
        payload.put("currency", req.getCurrency());
        payload.put("eventTimestamp", req.getEventTimestamp());

        accountClient.applyTransaction(req.getAccountId(), payload);

        EventEntity e = new EventEntity();
        e.setEventId(req.getEventId());
        e.setAccountId(req.getAccountId());
        e.setType(type);
        e.setAmount(req.getAmount());
        e.setCurrency(req.getCurrency());
        e.setEventTimestamp(req.getEventTimestamp());
        e.setMetadata(req.getMetadata());
        e.setReceivedAt(Instant.now());
        repository.save(e);

        return new SubmitResult(EventResponse.from(e), false);
    }

    public Optional<EventResponse> getById(String eventId) {
        return repository.findById(eventId).map(EventResponse::from);
    }

    public List<EventResponse> getByAccount(String accountId) {
        return repository.findByAccountIdOrderByEventTimestampAsc(accountId)
                .stream()
                .map(EventResponse::from)
                .toList();
    }

    public static class SubmitResult {
        private final EventResponse event;
        private final boolean duplicate;

        public SubmitResult(EventResponse event, boolean duplicate) {
            this.event = event;
            this.duplicate = duplicate;
        }

        public EventResponse getEvent() { return event; }
        public boolean isDuplicate() { return duplicate; }
    }
}
