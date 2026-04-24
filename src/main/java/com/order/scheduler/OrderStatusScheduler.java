package com.order.scheduler;

import com.order.enums.OrderStatus;
import com.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderStatusScheduler {

    private final OrderService orderService;

    @Scheduled(fixedRateString = "${order.scheduler.interval-ms:60000}")
    public void advanceOrderStatuses() {
        log.info("Scheduler triggered: advancing order statuses");

        int pendingCount = orderService.advanceOrders(OrderStatus.PENDING, OrderStatus.PROCESSING);
        int processingCount = orderService.advanceOrders(OrderStatus.PROCESSING, OrderStatus.SHIPPING);
        int shippingCount = orderService.advanceOrders(OrderStatus.SHIPPING, OrderStatus.DELIVERED);

        log.info("Scheduler completed: {} PENDING→PROCESSING, {} PROCESSING→SHIPPING, {} SHIPPING→DELIVERED",
                pendingCount, processingCount, shippingCount);
    }
}
