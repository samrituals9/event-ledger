package com.ledger.gateway.client;

import com.ledger.gateway.metrics.MetricsService;
import com.ledger.gateway.trace.TraceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AccountClientTraceTest {

    @AfterEach
    void clearTrace() {
        TraceContext.clear();
    }

    @Test
    void traceIdIsPropagatedAsHeaderToAccountService() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://account-service");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        AccountClient client = new AccountClient(restClient, new MetricsService(), 2, 1);

        server.expect(requestTo("http://account-service/accounts/acct-1/transactions"))
                .andExpect(header("X-Trace-Id", "trace-xyz"))
                .andRespond(withSuccess());

        TraceContext.set("trace-xyz");

        Map<String, Object> payload = new HashMap<>();
        payload.put("eventId", "e1");

        client.applyTransaction("acct-1", payload);

        server.verify();
    }
}
