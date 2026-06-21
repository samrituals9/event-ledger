package com.ledger.gateway.client;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CircuitBreakerTest {

    private RuntimeException boom() {
        return new RuntimeException("downstream failure");
    }

    @Test
    void opensAfterReachingFailureThreshold() {
        CircuitBreaker circuitBreaker = new CircuitBreaker("test", 3, 10_000);

        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> circuitBreaker.execute(() -> {
                throw boom();
            })).isInstanceOf(RuntimeException.class);
        }

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    void whenOpenRejectsImmediatelyWithCircuitOpenException() {
        CircuitBreaker circuitBreaker = new CircuitBreaker("test", 2, 10_000);

        for (int i = 0; i < 2; i++) {
            try {
                circuitBreaker.execute(() -> {
                    throw boom();
                });
            } catch (RuntimeException ignored) {
            }
        }

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        boolean[] executed = {false};

        assertThatThrownBy(() -> circuitBreaker.execute(() -> {
            executed[0] = true;
            return "x";
        })).isInstanceOf(CircuitOpenException.class);

        assertThat(executed[0]).isFalse();
    }

    @Test
    void halfOpenTrialSuccessClosesCircuit() throws InterruptedException {
        CircuitBreaker circuitBreaker = new CircuitBreaker("test", 1, 50);

        try {
            circuitBreaker.execute(() -> {
                throw boom();
            });
        } catch (RuntimeException ignored) {
        }

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        Thread.sleep(80);

        String result = circuitBreaker.execute(() -> "recovered");

        assertThat(result).isEqualTo("recovered");
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void successResetsConsecutiveFailureCount() {
        CircuitBreaker circuitBreaker = new CircuitBreaker("test", 3, 10_000);

        try {
            circuitBreaker.execute(() -> {
                throw boom();
            });
        } catch (RuntimeException ignored) {
        }

        try {
            circuitBreaker.execute(() -> {
                throw boom();
            });
        } catch (RuntimeException ignored) {
        }

        circuitBreaker.execute(() -> "ok");

        try {
            circuitBreaker.execute(() -> {
                throw boom();
            });
        } catch (RuntimeException ignored) {
        }

        try {
            circuitBreaker.execute(() -> {
                throw boom();
            });
        } catch (RuntimeException ignored) {
        }

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }
}
