package com.order.service;

import com.order.config.OrderSchedulerProperties;
import com.order.dto.*;
import com.order.entity.Order;
import com.order.enums.OrderStatus;
import com.order.exception.InvalidOrderStateException;
import com.order.exception.OrderNotFoundException;
import com.order.mapper.OrderMapper;
import com.order.metrics.OrderMetrics;
import com.order.repository.OrderRepository;
import com.order.repository.OrderStatusHistoryRepository;
import com.order.statemachine.OrderStateMachine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderStatusHistoryRepository statusHistoryRepository;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private OrderStateMachine orderStateMachine;

    @Mock
    private OrderMetrics orderMetrics;

    @Mock
    private OrderSchedulerProperties schedulerProperties;

    @InjectMocks
    private OrderServiceImpl orderService;

    private Order sampleOrder;
    private OrderResponse sampleResponse;
    private CreateOrderRequest createRequest;

    @BeforeEach
    void setUp() {
        sampleOrder = Order.builder()
                .id(1L)
                .status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("29.98"))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        sampleResponse = OrderResponse.builder()
                .id(1L)
                .status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("29.98"))
                .build();

        createRequest = CreateOrderRequest.builder()
                .items(List.of(
                        OrderItemRequest.builder()
                                .productName("Widget")
                                .quantity(2)
                                .unitPrice(new BigDecimal("14.99"))
                                .build()
                ))
                .build();
    }

    @Nested
    @DisplayName("Create Order")
    class CreateOrderTests {

        @Test
        @DisplayName("should create order and track metric")
        void shouldCreateOrderSuccessfully() {
            when(orderMapper.toEntity(createRequest)).thenReturn(sampleOrder);
            when(orderRepository.save(sampleOrder)).thenReturn(sampleOrder);
            when(orderMapper.toResponse(sampleOrder)).thenReturn(sampleResponse);

            OrderResponse result = orderService.createOrder(createRequest);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getStatus()).isEqualTo(OrderStatus.PENDING);
            verify(orderRepository).save(sampleOrder);
            verify(orderMetrics).incrementOrdersCreated();
        }
    }

    @Nested
    @DisplayName("Get Order By ID")
    class GetOrderByIdTests {

        @Test
        @DisplayName("should return order when found")
        void shouldReturnOrderWhenFound() {
            when(orderRepository.findById(1L)).thenReturn(Optional.of(sampleOrder));
            when(orderMapper.toResponse(sampleOrder)).thenReturn(sampleResponse);

            OrderResponse result = orderService.getOrderById(1L);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("should throw exception when order not found")
        void shouldThrowExceptionWhenOrderNotFound() {
            when(orderRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.getOrderById(99L))
                    .isInstanceOf(OrderNotFoundException.class)
                    .hasMessageContaining("99");
        }
    }

    @Nested
    @DisplayName("Update Order Status")
    class UpdateOrderStatusTests {

        @Test
        @DisplayName("should update status, record audit, and track metric")
        void shouldUpdateStatusWithValidTransition() {
            OrderResponse processingResponse = OrderResponse.builder()
                    .id(1L)
                    .status(OrderStatus.PROCESSING)
                    .build();

            when(orderRepository.findById(1L)).thenReturn(Optional.of(sampleOrder));
            doNothing().when(orderStateMachine).validateTransition(OrderStatus.PENDING, OrderStatus.PROCESSING);
            when(orderRepository.save(sampleOrder)).thenReturn(sampleOrder);
            when(orderMapper.toResponse(sampleOrder)).thenReturn(processingResponse);

            OrderResponse result = orderService.updateOrderStatus(1L, OrderStatus.PROCESSING);

            assertThat(result.getStatus()).isEqualTo(OrderStatus.PROCESSING);
            verify(orderStateMachine).validateTransition(OrderStatus.PENDING, OrderStatus.PROCESSING);
            verify(statusHistoryRepository).save(any());
            verify(orderMetrics).recordStatusTransition(OrderStatus.PENDING, OrderStatus.PROCESSING, 1);
        }

        @Test
        @DisplayName("should throw when invalid transition")
        void shouldThrowWhenInvalidTransition() {
            when(orderRepository.findById(1L)).thenReturn(Optional.of(sampleOrder));
            doThrow(new InvalidOrderStateException("Cannot transition from PENDING to DELIVERED"))
                    .when(orderStateMachine).validateTransition(OrderStatus.PENDING, OrderStatus.DELIVERED);

            assertThatThrownBy(() -> orderService.updateOrderStatus(1L, OrderStatus.DELIVERED))
                    .isInstanceOf(InvalidOrderStateException.class)
                    .hasMessageContaining("Cannot transition");
        }

        @Test
        @DisplayName("should throw when order not found for status update")
        void shouldThrowWhenOrderNotFoundForStatusUpdate() {
            when(orderRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.updateOrderStatus(99L, OrderStatus.PROCESSING))
                    .isInstanceOf(OrderNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Get All Orders - Paginated")
    class GetAllOrdersTests {

        @Test
        @DisplayName("should return paginated orders when no filter")
        void shouldReturnPaginatedOrdersWhenNoFilter() {
            Page<Order> page = new PageImpl<>(List.of(sampleOrder), PageRequest.of(0, 20), 1);

            when(orderRepository.findAll(any(Pageable.class))).thenReturn(page);
            when(orderMapper.toResponseList(List.of(sampleOrder))).thenReturn(List.of(sampleResponse));

            PagedResponse<OrderResponse> result = orderService.getAllOrders(null, 0, 20);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getPage()).isZero();
            verify(orderRepository).findAll(any(Pageable.class));
        }

        @Test
        @DisplayName("should filter orders by status with pagination")
        void shouldFilterOrdersByStatusWithPagination() {
            Page<Order> page = new PageImpl<>(List.of(sampleOrder), PageRequest.of(0, 20), 1);

            when(orderRepository.findByStatus(eq(OrderStatus.PENDING), any(Pageable.class))).thenReturn(page);
            when(orderMapper.toResponseList(List.of(sampleOrder))).thenReturn(List.of(sampleResponse));

            PagedResponse<OrderResponse> result = orderService.getAllOrders(OrderStatus.PENDING, 0, 20);

            assertThat(result.getContent()).hasSize(1);
            verify(orderRepository).findByStatus(eq(OrderStatus.PENDING), any(Pageable.class));
        }

        @Test
        @DisplayName("should return empty page when no orders match")
        void shouldReturnEmptyPageWhenNoOrders() {
            Page<Order> emptyPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 20), 0);

            when(orderRepository.findByStatus(eq(OrderStatus.SHIPPING), any(Pageable.class))).thenReturn(emptyPage);
            when(orderMapper.toResponseList(Collections.emptyList())).thenReturn(Collections.emptyList());

            PagedResponse<OrderResponse> result = orderService.getAllOrders(OrderStatus.SHIPPING, 0, 20);

            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isZero();
        }
    }

    @Nested
    @DisplayName("Cancel Order")
    class CancelOrderTests {

        @Test
        @DisplayName("should cancel pending order, record audit, and track metric")
        void shouldCancelPendingOrderSuccessfully() {
            OrderResponse cancelledResponse = OrderResponse.builder()
                    .id(1L)
                    .status(OrderStatus.CANCELLED)
                    .build();

            when(orderRepository.findById(1L)).thenReturn(Optional.of(sampleOrder));
            when(orderRepository.save(sampleOrder)).thenReturn(sampleOrder);
            when(orderMapper.toResponse(sampleOrder)).thenReturn(cancelledResponse);

            OrderResponse result = orderService.cancelOrder(1L);

            assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            verify(orderMetrics).incrementOrdersCancelled();
            verify(statusHistoryRepository).save(any());
        }

        @Test
        @DisplayName("should throw when cancelling non-pending order")
        void shouldThrowWhenCancellingNonPendingOrder() {
            sampleOrder.setStatus(OrderStatus.PROCESSING);
            when(orderRepository.findById(1L)).thenReturn(Optional.of(sampleOrder));

            assertThatThrownBy(() -> orderService.cancelOrder(1L))
                    .isInstanceOf(InvalidOrderStateException.class)
                    .hasMessageContaining("PENDING");
        }

        @Test
        @DisplayName("should throw when cancelling shipped order")
        void shouldThrowWhenCancellingShippedOrder() {
            sampleOrder.setStatus(OrderStatus.SHIPPING);
            when(orderRepository.findById(1L)).thenReturn(Optional.of(sampleOrder));

            assertThatThrownBy(() -> orderService.cancelOrder(1L))
                    .isInstanceOf(InvalidOrderStateException.class);
        }

        @Test
        @DisplayName("should throw when cancelling delivered order")
        void shouldThrowWhenCancellingDeliveredOrder() {
            sampleOrder.setStatus(OrderStatus.DELIVERED);
            when(orderRepository.findById(1L)).thenReturn(Optional.of(sampleOrder));

            assertThatThrownBy(() -> orderService.cancelOrder(1L))
                    .isInstanceOf(InvalidOrderStateException.class);
        }

        @Test
        @DisplayName("should throw when order not found for cancel")
        void shouldThrowWhenOrderNotFoundForCancel() {
            when(orderRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.cancelOrder(99L))
                    .isInstanceOf(OrderNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Advance Orders")
    class AdvanceOrdersTests {

        @Test
        @DisplayName("should advance pending orders and record audit trail")
        void shouldAdvancePendingToProcessing() {
            Order order1 = Order.builder().id(1L).status(OrderStatus.PENDING).build();
            Order order2 = Order.builder().id(2L).status(OrderStatus.PENDING).build();

            when(schedulerProperties.getAdvanceThresholdMinutes()).thenReturn(1L);
            when(orderRepository.findByStatusAndUpdatedAtBefore(eq(OrderStatus.PENDING), any(LocalDateTime.class)))
                    .thenReturn(List.of(order1, order2));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            int count = orderService.advanceOrders(OrderStatus.PENDING, OrderStatus.PROCESSING);

            assertThat(count).isEqualTo(2);
            assertThat(order1.getStatus()).isEqualTo(OrderStatus.PROCESSING);
            assertThat(order2.getStatus()).isEqualTo(OrderStatus.PROCESSING);
            verify(orderRepository, times(2)).save(any(Order.class));
            verify(statusHistoryRepository, times(2)).save(any());
            verify(orderMetrics).recordStatusTransition(OrderStatus.PENDING, OrderStatus.PROCESSING, 2);
        }

        @Test
        @DisplayName("should advance processing orders to shipping")
        void shouldAdvanceProcessingToShipping() {
            Order order1 = Order.builder().id(1L).status(OrderStatus.PROCESSING).build();

            when(schedulerProperties.getAdvanceThresholdMinutes()).thenReturn(1L);
            when(orderRepository.findByStatusAndUpdatedAtBefore(eq(OrderStatus.PROCESSING), any(LocalDateTime.class)))
                    .thenReturn(List.of(order1));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            int count = orderService.advanceOrders(OrderStatus.PROCESSING, OrderStatus.SHIPPING);

            assertThat(count).isEqualTo(1);
            assertThat(order1.getStatus()).isEqualTo(OrderStatus.SHIPPING);
        }

        @Test
        @DisplayName("should advance shipping orders to delivered")
        void shouldAdvanceShippingToDelivered() {
            Order order1 = Order.builder().id(1L).status(OrderStatus.SHIPPING).build();

            when(schedulerProperties.getAdvanceThresholdMinutes()).thenReturn(1L);
            when(orderRepository.findByStatusAndUpdatedAtBefore(eq(OrderStatus.SHIPPING), any(LocalDateTime.class)))
                    .thenReturn(List.of(order1));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            int count = orderService.advanceOrders(OrderStatus.SHIPPING, OrderStatus.DELIVERED);

            assertThat(count).isEqualTo(1);
            assertThat(order1.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        }

        @Test
        @DisplayName("should not advance recently updated orders")
        void shouldNotAdvanceRecentlyUpdatedOrders() {
            when(schedulerProperties.getAdvanceThresholdMinutes()).thenReturn(1L);
            when(orderRepository.findByStatusAndUpdatedAtBefore(eq(OrderStatus.PENDING), any(LocalDateTime.class)))
                    .thenReturn(Collections.emptyList());

            int count = orderService.advanceOrders(OrderStatus.PENDING, OrderStatus.PROCESSING);

            assertThat(count).isZero();
            verify(orderRepository, never()).save(any(Order.class));
        }

        @Test
        @DisplayName("should return zero when no orders to advance")
        void shouldReturnZeroWhenNoOrdersToAdvance() {
            when(schedulerProperties.getAdvanceThresholdMinutes()).thenReturn(1L);
            when(orderRepository.findByStatusAndUpdatedAtBefore(eq(OrderStatus.PENDING), any(LocalDateTime.class)))
                    .thenReturn(Collections.emptyList());

            int count = orderService.advanceOrders(OrderStatus.PENDING, OrderStatus.PROCESSING);

            assertThat(count).isZero();
            verify(orderRepository, never()).save(any(Order.class));
        }
    }
}
