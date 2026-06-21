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
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class GatewayAccountFlowIntegrationTest {

    private static WireMockServer wireMock;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRepository repository;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(options().port(18081));
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
        registry.add("account.service.base-url", () -> "http://localhost:18081");
        registry.add("account.service.timeout-ms", () -> "1000");
        registry.add("account.client.max-attempts", () -> "2");
        registry.add("account.client.backoff-ms", () -> "10");
    }

    private String event(String eventId) {
        return "{\"eventId\":\"" + eventId + "\",\"accountId\":\"acct-int\",\"type\":\"CREDIT\","
                + "\"amount\":100.00,\"currency\":\"USD\",\"eventTimestamp\":\"2026-05-16T09:00:00Z\"}";
    }

    @Test
    void fullFlowCallsAccountServiceOverHttpAndPropagatesTraceId() throws Exception {
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock
                .post(urlPathEqualTo("/accounts/acct-int/transactions"))
                .willReturn(aResponse().withStatus(200)));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", "trace-int-1")
                        .content(event("evt-int-1")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventId").value("evt-int-1"));

        wireMock.verify(postRequestedFor(urlPathEqualTo("/accounts/acct-int/transactions"))
                .withHeader("X-Trace-Id", equalTo("trace-int-1")));
    }

    @Test
    void gatewayReturns503WhenAccountServiceFailsOverHttp() throws Exception {
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock
                .post(urlPathEqualTo("/accounts/acct-int/transactions"))
                .willReturn(aResponse().withStatus(500)));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(event("evt-int-2")))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void readEndpointWorksAfterSuccessfulWriteWithoutCallingAccountServiceAgain() throws Exception {
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock
                .post(urlPathEqualTo("/accounts/acct-int/transactions"))
                .willReturn(aResponse().withStatus(200)));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(event("evt-int-3")))
                .andExpect(status().isCreated());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/events")
                        .param("account", "acct-int"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventId").value("evt-int-3"));
    }
}
