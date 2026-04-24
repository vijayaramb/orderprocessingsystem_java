package com.order.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.order.dto.*;
import com.order.enums.OrderStatus;
import com.order.exception.GlobalExceptionHandler;
import com.order.exception.InvalidOrderStateException;
import com.order.exception.OrderNotFoundException;
import com.order.filter.CorrelationIdFilter;
import com.order.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = OrderController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = CorrelationIdFilter.class))
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderService orderService;

    private OrderResponse buildSampleResponse() {
        return OrderResponse.builder()
                .id(1L)
                .status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("29.98"))
                .createdAt(LocalDateTime.of(2026, 4, 22, 10, 0))
                .updatedAt(LocalDateTime.of(2026, 4, 22, 10, 0))
                .items(List.of(
                        OrderItemResponse.builder()
                                .id(1L)
                                .productName("Widget")
                                .quantity(2)
                                .unitPrice(new BigDecimal("14.99"))
                                .subtotal(new BigDecimal("29.98"))
                                .build()
                ))
                .build();
    }

    private PagedResponse<OrderResponse> buildPagedResponse(List<OrderResponse> content) {
        return PagedResponse.<OrderResponse>builder()
                .content(content)
                .page(0)
                .size(20)
                .totalElements(content.size())
                .totalPages(1)
                .last(true)
                .build();
    }

    @Nested
    @DisplayName("POST /api/v1/orders")
    class CreateOrderEndpoint {

        @Test
        @DisplayName("should create order and return 201 with ApiResponse wrapper")
        void shouldCreateOrderAndReturn201() throws Exception {
            CreateOrderRequest request = CreateOrderRequest.builder()
                    .items(List.of(
                            OrderItemRequest.builder()
                                    .productName("Widget")
                                    .quantity(2)
                                    .unitPrice(new BigDecimal("14.99"))
                                    .build()
                    ))
                    .build();

            when(orderService.createOrder(any(CreateOrderRequest.class)))
                    .thenReturn(buildSampleResponse());

            mockMvc.perform(post("/api/v1/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.status").value("PENDING"))
                    .andExpect(jsonPath("$.data.items", hasSize(1)));
        }

        @Test
        @DisplayName("should return 400 for empty items")
        void shouldReturn400ForEmptyItems() throws Exception {
            CreateOrderRequest request = CreateOrderRequest.builder()
                    .items(List.of())
                    .build();

            mockMvc.perform(post("/api/v1/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 for missing product name")
        void shouldReturn400ForMissingProductName() throws Exception {
            CreateOrderRequest request = CreateOrderRequest.builder()
                    .items(List.of(
                            OrderItemRequest.builder()
                                    .productName("")
                                    .quantity(1)
                                    .unitPrice(new BigDecimal("10.00"))
                                    .build()
                    ))
                    .build();

            mockMvc.perform(post("/api/v1/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/orders/{id}")
    class GetOrderEndpoint {

        @Test
        @DisplayName("should return order by id wrapped in ApiResponse")
        void shouldReturnOrderById() throws Exception {
            when(orderService.getOrderById(1L)).thenReturn(buildSampleResponse());

            mockMvc.perform(get("/api/v1/orders/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.totalAmount").value(29.98));
        }

        @Test
        @DisplayName("should return 404 when order not found")
        void shouldReturn404WhenOrderNotFound() throws Exception {
            when(orderService.getOrderById(99L)).thenThrow(new OrderNotFoundException(99L));

            mockMvc.perform(get("/api/v1/orders/99"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value(containsString("99")));
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/orders/{id}/status")
    class UpdateStatusEndpoint {

        @Test
        @DisplayName("should update order status")
        void shouldUpdateOrderStatus() throws Exception {
            OrderResponse response = buildSampleResponse();
            response.setStatus(OrderStatus.PROCESSING);

            UpdateStatusRequest request = UpdateStatusRequest.builder()
                    .status(OrderStatus.PROCESSING)
                    .build();

            when(orderService.updateOrderStatus(eq(1L), eq(OrderStatus.PROCESSING)))
                    .thenReturn(response);

            mockMvc.perform(patch("/api/v1/orders/1/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.status").value("PROCESSING"));
        }

        @Test
        @DisplayName("should return 409 for invalid state transition")
        void shouldReturn409ForInvalidTransition() throws Exception {
            UpdateStatusRequest request = UpdateStatusRequest.builder()
                    .status(OrderStatus.DELIVERED)
                    .build();

            when(orderService.updateOrderStatus(eq(1L), eq(OrderStatus.DELIVERED)))
                    .thenThrow(new InvalidOrderStateException("Cannot transition from PENDING to DELIVERED"));

            mockMvc.perform(patch("/api/v1/orders/1/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(containsString("Cannot transition")));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/orders")
    class ListOrdersEndpoint {

        @Test
        @DisplayName("should return paginated orders")
        void shouldReturnPaginatedOrders() throws Exception {
            PagedResponse<OrderResponse> pagedResponse = buildPagedResponse(List.of(buildSampleResponse()));

            when(orderService.getAllOrders(null, 0, 20)).thenReturn(pagedResponse);

            mockMvc.perform(get("/api/v1/orders"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content", hasSize(1)))
                    .andExpect(jsonPath("$.data.totalElements").value(1))
                    .andExpect(jsonPath("$.data.page").value(0));
        }

        @Test
        @DisplayName("should filter orders by status with pagination")
        void shouldFilterOrdersByStatus() throws Exception {
            PagedResponse<OrderResponse> pagedResponse = buildPagedResponse(List.of(buildSampleResponse()));

            when(orderService.getAllOrders(OrderStatus.PENDING, 0, 20)).thenReturn(pagedResponse);

            mockMvc.perform(get("/api/v1/orders").param("status", "PENDING"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[0].status").value("PENDING"));
        }

        @Test
        @DisplayName("should return empty page when no orders")
        void shouldReturnEmptyPageWhenNoOrders() throws Exception {
            PagedResponse<OrderResponse> emptyPage = buildPagedResponse(List.of());

            when(orderService.getAllOrders(null, 0, 20)).thenReturn(emptyPage);

            mockMvc.perform(get("/api/v1/orders"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content", hasSize(0)));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/orders/{id}/cancel")
    class CancelOrderEndpoint {

        @Test
        @DisplayName("should cancel pending order")
        void shouldCancelPendingOrder() throws Exception {
            OrderResponse response = buildSampleResponse();
            response.setStatus(OrderStatus.CANCELLED);

            when(orderService.cancelOrder(1L)).thenReturn(response);

            mockMvc.perform(post("/api/v1/orders/1/cancel"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.status").value("CANCELLED"));
        }

        @Test
        @DisplayName("should return 409 when cancelling non-pending order")
        void shouldReturn409WhenCancellingNonPendingOrder() throws Exception {
            when(orderService.cancelOrder(1L))
                    .thenThrow(new InvalidOrderStateException("Only orders in PENDING status can be cancelled"));

            mockMvc.perform(post("/api/v1/orders/1/cancel"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(containsString("PENDING")));
        }
    }
}
