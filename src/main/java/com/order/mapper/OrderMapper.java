package com.order.mapper;

import com.order.dto.*;
import com.order.entity.Order;
import com.order.entity.OrderItem;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrderMapper {

    public OrderResponse toResponse(Order order) {
        return OrderResponse.builder()
                .id(order.getId())
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .items(toItemResponses(order.getItems()))
                .build();
    }

    public List<OrderResponse> toResponseList(List<Order> orders) {
        return orders.stream()
                .map(this::toResponse)
                .toList();
    }

    public Order toEntity(CreateOrderRequest request) {
        Order order = Order.builder().build();

        request.getItems().forEach(itemReq -> {
            OrderItem item = OrderItem.builder()
                    .productName(itemReq.getProductName())
                    .quantity(itemReq.getQuantity())
                    .unitPrice(itemReq.getUnitPrice())
                    .build();
            order.addItem(item);
        });

        order.recalculateTotal();
        return order;
    }

    private List<OrderItemResponse> toItemResponses(List<OrderItem> items) {
        return items.stream()
                .map(this::toItemResponse)
                .toList();
    }

    private OrderItemResponse toItemResponse(OrderItem item) {
        return OrderItemResponse.builder()
                .id(item.getId())
                .productName(item.getProductName())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .subtotal(item.getSubtotal())
                .build();
    }
}
