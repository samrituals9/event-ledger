package com.ledger.gateway.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

public class CircuitBreaker {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreaker.class);

    public enum State {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    private final String name;
    private final int failureThreshold;
    private final long openDurationMs;

    private State state = State.CLOSED;
    private int consecutiveFailures = 0;
    private long openedAtMs = 0L;

    public CircuitBreaker(String name, int failureThreshold, long openDurationMs) {
        this.name = name;
        this.failureThreshold = failureThreshold;
        this.openDurationMs = openDurationMs;
    }

    public <T> T execute(Supplier<T> action) {
        if (!allowRequest()) {
            throw new CircuitOpenException(
                    "Circuit '" + name + "' is OPEN; rejecting call to Account Service"
            );
        }

        try {
            T result = action.get();
            onSuccess();
            return result;
        } catch (RuntimeException e) {
            onFailure();
            throw e;
        }
    }

    private synchronized boolean allowRequest() {
        if (state == State.OPEN) {
            if (System.currentTimeMillis() - openedAtMs >= openDurationMs) {
                state = State.HALF_OPEN;
                log.warn("{\"service\":\"event-gateway-service\",\"event\":\"circuit_half_open\",\"circuit\":\"{}\"}", name);
                return true;
            }
            return false;
        }

        return true;
    }

    private synchronized void onSuccess() {
        consecutiveFailures = 0;

        if (state != State.CLOSED) {
            log.warn("{\"service\":\"event-gateway-service\",\"event\":\"circuit_closed\",\"circuit\":\"{}\"}", name);
        }

        state = State.CLOSED;
    }

    private synchronized void onFailure() {
        consecutiveFailures++;

        if (state == State.HALF_OPEN || consecutiveFailures >= failureThreshold) {
            state = State.OPEN;
            openedAtMs = System.currentTimeMillis();

            log.warn("{\"service\":\"event-gateway-service\",\"event\":\"circuit_open\",\"circuit\":\"{}\",\"consecutiveFailures\":{}}",
                    name, consecutiveFailures);
        }
    }

    public synchronized State getState() {
        return state;
    }
}
