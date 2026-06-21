package com.ledger.gateway.web;

import com.ledger.gateway.client.AccountClient;
import com.ledger.gateway.client.AccountServiceException;
import com.ledger.gateway.repository.EventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class EventControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRepository repository;

    @MockBean
    private AccountClient accountClient;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    private String event(String eventId) {
        return "{\"eventId\":\"" + eventId + "\",\"accountId\":\"acct-1\",\"type\":\"CREDIT\","
                + "\"amount\":100.00,\"currency\":\"USD\",\"eventTimestamp\":\"2026-05-16T09:00:00Z\"}";
    }

    @Test
    void invalidEventReturns400() throws Exception {
        String body = "{\"eventId\":\"evt-bad\",\"type\":\"CREDIT\",\"amount\":100.00,"
                + "\"currency\":\"USD\",\"eventTimestamp\":\"2026-05-16T09:00:00Z\"}";

        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validEventSavesAndCallsAccountService() throws Exception {
        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(event("evt-ok")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventId").value("evt-ok"));

        verify(accountClient, times(1)).applyTransaction(anyString(), any());
    }

    @Test
    void duplicateEventReturnsExistingAndDoesNotCallAccountServiceAgain() throws Exception {
        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(event("evt-dup")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(event("evt-dup")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value("evt-dup"));

        verify(accountClient, times(1)).applyTransaction(anyString(), any());
    }

    @Test
    void accountServiceUnavailableReturns503() throws Exception {
        doThrow(new AccountServiceException("down", null))
                .when(accountClient).applyTransaction(anyString(), any());

        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(event("evt-503")))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void getEventsByAccountWorksWhenAccountServiceUnavailable() throws Exception {
        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(event("evt-read")))
                .andExpect(status().isCreated());

        doThrow(new AccountServiceException("down", null))
                .when(accountClient).applyTransaction(anyString(), any());

        mockMvc.perform(get("/events").param("account", "acct-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventId").value("evt-read"));
    }
}
