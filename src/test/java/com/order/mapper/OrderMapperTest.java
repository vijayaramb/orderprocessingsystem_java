package com.order.mapper;

import com.order.dto.CreateOrderRequest;
import com.order.dto.OrderItemRequest;
import com.order.dto.OrderResponse;
import com.order.entity.Order;
import com.order.entity.OrderItem;
import com.order.enums.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderMapperTest {

    private OrderMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new OrderMapper();
    }

    @Test
    @DisplayName("should map Order entity to OrderResponse")
    void shouldMapEntityToResponse() {
        Order order = Order.builder()
                .id(1L)
                .status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("49.98"))
                .createdAt(LocalDateTime.of(2026, 4, 22, 10, 0))
                .updatedAt(LocalDateTime.of(2026, 4, 22, 10, 0))
                .build();

        OrderItem item = OrderItem.builder()
                .id(1L)
                .productName("Gadget")
                .quantity(2)
                .unitPrice(new BigDecimal("24.99"))
                .order(order)
                .build();
        order.getItems().add(item);

        OrderResponse response = mapper.toResponse(order);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.getTotalAmount()).isEqualByComparingTo("49.98");
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getProductName()).isEqualTo("Gadget");
        assertThat(response.getItems().get(0).getSubtotal()).isEqualByComparingTo("49.98");
    }

    @Test
    @DisplayName("should map CreateOrderRequest to Order entity")
    void shouldMapRequestToEntity() {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .items(List.of(
                        OrderItemRequest.builder()
                                .productName("Widget")
                                .quantity(3)
                                .unitPrice(new BigDecimal("10.00"))
                                .build(),
                        OrderItemRequest.builder()
                                .productName("Gadget")
                                .quantity(1)
                                .unitPrice(new BigDecimal("25.00"))
                                .build()
                ))
                .build();

        Order order = mapper.toEntity(request);

        assertThat(order.getItems()).hasSize(2);
        assertThat(order.getTotalAmount()).isEqualByComparingTo("55.00");
        assertThat(order.getItems().get(0).getOrder()).isEqualTo(order);
        assertThat(order.getItems().get(1).getOrder()).isEqualTo(order);
    }

    @Test
    @DisplayName("should map list of orders to response list")
    void shouldMapOrderListToResponseList() {
        Order order1 = Order.builder()
                .id(1L).status(OrderStatus.PENDING)
                .totalAmount(BigDecimal.TEN)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
        Order order2 = Order.builder()
                .id(2L).status(OrderStatus.PROCESSING)
                .totalAmount(BigDecimal.valueOf(20))
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();

        List<OrderResponse> responses = mapper.toResponseList(List.of(order1, order2));

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).getId()).isEqualTo(1L);
        assertThat(responses.get(1).getId()).isEqualTo(2L);
    }
}
