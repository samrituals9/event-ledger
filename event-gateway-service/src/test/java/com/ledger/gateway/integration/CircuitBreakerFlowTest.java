package com.ledger.gateway.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.ledger.gateway.repository.EventRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CircuitBreakerFlowTest {

    private static WireMockServer wireMock;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRepository repository;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(options().port(18082));
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMock != null) {
            wireMock.stop();
        }
    }

    @BeforeEach
    void clean() {
        repository.deleteAll();
        wireMock.resetAll();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("account.service.base-url", () -> "http://localhost:18082");
        registry.add("account.service.timeout-ms", () -> "1000");
        registry.add("account.client.max-attempts", () -> "2");
        registry.add("account.client.backoff-ms", () -> "5");
        registry.add("account.client.cb.failure-threshold", () -> "2");
        registry.add("account.client.cb.open-duration-ms", () -> "10000");
    }

    private String event(String eventId) {
        return "{\"eventId\":\"" + eventId + "\",\"accountId\":\"acct-cb\",\"type\":\"CREDIT\","
                + "\"amount\":100.00,\"currency\":\"USD\",\"eventTimestamp\":\"2026-05-16T09:00:00Z\"}";
    }

    private void postEventExpect503(String eventId) throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(event(eventId)))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void repeatedFailuresOpenCircuitAndReadsStillWork() throws Exception {
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock
                .post(urlPathEqualTo("/accounts/acct-cb/transactions"))
                .willReturn(aResponse().withStatus(500)));

        postEventExpect503("evt-cb-1");
        postEventExpect503("evt-cb-2");

        int callsAfterBreakerOpened = wireMock.getAllServeEvents().size();

        postEventExpect503("evt-cb-3");
        postEventExpect503("evt-cb-4");

        int callsAfterFailFastRequests = wireMock.getAllServeEvents().size();

        assertThat(callsAfterBreakerOpened).isEqualTo(4);
        assertThat(callsAfterFailFastRequests).isEqualTo(4);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/events/evt-cb-1"))
                .andExpect(status().isNotFound());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/events")
                        .param("account", "acct-cb"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
