package com.ledger.gateway.web;

import com.ledger.gateway.dto.EventRequest;
import com.ledger.gateway.dto.EventResponse;
import com.ledger.gateway.service.EventService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class EventController {

    private final EventService service;

    public EventController(EventService service) {
        this.service = service;
    }

    @PostMapping("/events")
    public ResponseEntity<EventResponse> submit(@Valid @RequestBody EventRequest req) {
        EventService.SubmitResult result = service.submit(req);
        HttpStatus status = result.isDuplicate() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(result.getEvent());
    }

    @GetMapping("/events/{id}")
    public ResponseEntity<EventResponse> getById(@PathVariable String id) {
        return service.getById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/events")
    public List<EventResponse> getByAccount(@RequestParam("account") String account) {
        return service.getByAccount(account);
    }
}
