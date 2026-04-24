package com.order.service;

import com.order.dto.CreateOrderRequest;
import com.order.dto.OrderResponse;
import com.order.enums.OrderStatus;

import java.util.List;

public interface OrderService {

    OrderResponse createOrder(CreateOrderRequest request);

    OrderResponse getOrderById(Long orderId);

    OrderResponse updateOrderStatus(Long orderId, OrderStatus newStatus);

    List<OrderResponse> getAllOrders(OrderStatus statusFilter);

    OrderResponse cancelOrder(Long orderId);

    int advanceOrders(OrderStatus fromStatus, OrderStatus toStatus);
}
