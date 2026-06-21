package com.ledger.account.web;

import com.ledger.account.dto.TransactionResponse;
import com.ledger.account.metrics.MetricsService;
import com.ledger.account.service.AccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountController.class)
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AccountService service;

    @MockBean
    private MetricsService metrics;

    @Test
    void validRequestReturns201() throws Exception {
        TransactionResponse response = new TransactionResponse();

        when(service.applyTransaction(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new AccountService.TransactionResult(response, false));

        String body = "{\"eventId\":\"e1\",\"type\":\"CREDIT\",\"amount\":150.00,\"currency\":\"USD\",\"eventTimestamp\":\"2026-05-15T14:00:00Z\"}";

        mockMvc.perform(post("/accounts/acct-1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void negativeAmountReturns400() throws Exception {
        String body = "{\"eventId\":\"e1\",\"type\":\"CREDIT\",\"amount\":-5,\"currency\":\"USD\",\"eventTimestamp\":\"2026-05-15T14:00:00Z\"}";

        mockMvc.perform(post("/accounts/acct-1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingEventIdReturns400() throws Exception {
        String body = "{\"type\":\"CREDIT\",\"amount\":150.00,\"currency\":\"USD\",\"eventTimestamp\":\"2026-05-15T14:00:00Z\"}";

        mockMvc.perform(post("/accounts/acct-1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
