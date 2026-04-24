package com.order.metrics;

import com.order.enums.OrderStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class OrderMetrics {

    private final MeterRegistry meterRegistry;
    private final Counter ordersCreatedCounter;
    private final Counter ordersCancelledCounter;
    private final Timer orderProcessingTimer;
    private final Map<String, Counter> statusTransitionCounters = new ConcurrentHashMap<>();

    public OrderMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.ordersCreatedCounter = Counter.builder("orders.created.total")
                .description("Total orders created")
                .register(meterRegistry);
        this.ordersCancelledCounter = Counter.builder("orders.cancelled.total")
                .description("Total orders cancelled")
                .register(meterRegistry);
        this.orderProcessingTimer = Timer.builder("orders.processing.duration")
                .description("Time to process orders")
                .register(meterRegistry);
    }

    public void incrementOrdersCreated() {
        ordersCreatedCounter.increment();
    }

    public void incrementOrdersCancelled() {
        ordersCancelledCounter.increment();
    }

    public void recordStatusTransition(OrderStatus from, OrderStatus to, int count) {
        String key = from.name() + "_to_" + to.name();
        statusTransitionCounters.computeIfAbsent(key, k ->
                Counter.builder("orders.status.transitions")
                        .tag("from", from.name())
                        .tag("to", to.name())
                        .description("Order status transitions")
                        .register(meterRegistry)
        ).increment(count);
    }

    public Timer getOrderProcessingTimer() {
        return orderProcessingTimer;
    }
}
