package com.ledger.account.metrics;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class MetricsService {

    private final Map<String, AtomicLong> counters = new ConcurrentHashMap<>();

    public void increment(String name) {
        counters.computeIfAbsent(name, k -> new AtomicLong()).incrementAndGet();
    }

    public Map<String, Long> snapshot() {
        Map<String, Long> out = new ConcurrentHashMap<>();
        counters.forEach((k, v) -> out.put(k, v.get()));
        return out;
    }
}
