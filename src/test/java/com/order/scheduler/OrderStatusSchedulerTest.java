package com.order.scheduler;

import com.order.enums.OrderStatus;
import com.order.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderStatusSchedulerTest {

    @Mock
    private OrderService orderService;

    @InjectMocks
    private OrderStatusScheduler scheduler;

    @Test
    @DisplayName("should advance all three status transitions on schedule")
    void shouldAdvanceAllTransitions() {
        when(orderService.advanceOrders(OrderStatus.PENDING, OrderStatus.PROCESSING)).thenReturn(2);
        when(orderService.advanceOrders(OrderStatus.PROCESSING, OrderStatus.SHIPPING)).thenReturn(1);
        when(orderService.advanceOrders(OrderStatus.SHIPPING, OrderStatus.DELIVERED)).thenReturn(3);

        scheduler.advanceOrderStatuses();

        verify(orderService).advanceOrders(OrderStatus.PENDING, OrderStatus.PROCESSING);
        verify(orderService).advanceOrders(OrderStatus.PROCESSING, OrderStatus.SHIPPING);
        verify(orderService).advanceOrders(OrderStatus.SHIPPING, OrderStatus.DELIVERED);
    }

    @Test
    @DisplayName("should handle zero orders in all transitions")
    void shouldHandleZeroOrders() {
        when(orderService.advanceOrders(OrderStatus.PENDING, OrderStatus.PROCESSING)).thenReturn(0);
        when(orderService.advanceOrders(OrderStatus.PROCESSING, OrderStatus.SHIPPING)).thenReturn(0);
        when(orderService.advanceOrders(OrderStatus.SHIPPING, OrderStatus.DELIVERED)).thenReturn(0);

        scheduler.advanceOrderStatuses();

        verify(orderService).advanceOrders(OrderStatus.PENDING, OrderStatus.PROCESSING);
        verify(orderService).advanceOrders(OrderStatus.PROCESSING, OrderStatus.SHIPPING);
        verify(orderService).advanceOrders(OrderStatus.SHIPPING, OrderStatus.DELIVERED);
    }
}
