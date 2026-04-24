package com.order.service;

import com.order.dto.CreateOrderRequest;
import com.order.dto.OrderResponse;
import com.order.dto.PagedResponse;
import com.order.enums.OrderStatus;

public interface OrderService {

    OrderResponse createOrder(CreateOrderRequest request);

    OrderResponse getOrderById(Long orderId);

    OrderResponse updateOrderStatus(Long orderId, OrderStatus newStatus);

    PagedResponse<OrderResponse> getAllOrders(OrderStatus statusFilter, int page, int size);

    OrderResponse cancelOrder(Long orderId);

    int advanceOrders(OrderStatus fromStatus, OrderStatus toStatus);
}
