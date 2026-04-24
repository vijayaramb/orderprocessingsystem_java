package com.order.service;

import com.order.config.OrderSchedulerProperties;
import com.order.dto.CreateOrderRequest;
import com.order.dto.OrderResponse;
import com.order.dto.PagedResponse;
import com.order.entity.Order;
import com.order.entity.OrderStatusHistory;
import com.order.enums.OrderStatus;
import com.order.exception.InvalidOrderStateException;
import com.order.exception.OrderNotFoundException;
import com.order.mapper.OrderMapper;
import com.order.metrics.OrderMetrics;
import com.order.repository.OrderRepository;
import com.order.repository.OrderStatusHistoryRepository;
import com.order.statemachine.OrderStateMachine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository statusHistoryRepository;
    private final OrderMapper orderMapper;
    private final OrderStateMachine orderStateMachine;
    private final OrderMetrics orderMetrics;
    private final OrderSchedulerProperties schedulerProperties;

    @Override
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        Order order = orderMapper.toEntity(request);
        Order savedOrder = orderRepository.save(order);
        orderMetrics.incrementOrdersCreated();
        log.info("Order created with id: {}", savedOrder.getId());
        return orderMapper.toResponse(savedOrder);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "orders", key = "#orderId")
    public OrderResponse getOrderById(Long orderId) {
        Order order = findOrderOrThrow(orderId);
        return orderMapper.toResponse(order);
    }

    @Override
    @Transactional
    @CacheEvict(value = "orders", key = "#orderId")
    public OrderResponse updateOrderStatus(Long orderId, OrderStatus newStatus) {
        Order order = findOrderOrThrow(orderId);
        OrderStatus previousStatus = order.getStatus();
        orderStateMachine.validateTransition(previousStatus, newStatus);

        order.setStatus(newStatus);
        Order updatedOrder = orderRepository.save(order);
        recordStatusChange(order, previousStatus, newStatus, "API");

        orderMetrics.recordStatusTransition(previousStatus, newStatus, 1);
        log.info("Order {} status updated: {} -> {}", orderId, previousStatus, newStatus);
        return orderMapper.toResponse(updatedOrder);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<OrderResponse> getAllOrders(OrderStatus statusFilter, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Order> orderPage;

        if (statusFilter != null) {
            orderPage = orderRepository.findByStatus(statusFilter, pageable);
        } else {
            orderPage = orderRepository.findAll(pageable);
        }

        List<OrderResponse> responses = orderMapper.toResponseList(orderPage.getContent());

        return PagedResponse.<OrderResponse>builder()
                .content(responses)
                .page(orderPage.getNumber())
                .size(orderPage.getSize())
                .totalElements(orderPage.getTotalElements())
                .totalPages(orderPage.getTotalPages())
                .last(orderPage.isLast())
                .build();
    }

    @Override
    @Transactional
    @CacheEvict(value = "orders", key = "#orderId")
    public OrderResponse cancelOrder(Long orderId) {
        Order order = findOrderOrThrow(orderId);

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new InvalidOrderStateException(
                    "Only orders in PENDING status can be cancelled. Current status: " + order.getStatus()
            );
        }

        OrderStatus previousStatus = order.getStatus();
        order.setStatus(OrderStatus.CANCELLED);
        Order cancelledOrder = orderRepository.save(order);
        recordStatusChange(order, previousStatus, OrderStatus.CANCELLED, "API");

        orderMetrics.incrementOrdersCancelled();
        log.info("Order {} has been cancelled", orderId);
        return orderMapper.toResponse(cancelledOrder);
    }

    @Override
    @Transactional
    public int advanceOrders(OrderStatus fromStatus, OrderStatus toStatus) {
        LocalDateTime cutoff = LocalDateTime.now()
                .minusMinutes(schedulerProperties.getAdvanceThresholdMinutes());
        List<Order> orders = orderRepository.findByStatusAndUpdatedAtBefore(fromStatus, cutoff);

        orders.forEach(order -> {
            order.setStatus(toStatus);
            orderRepository.save(order);
            recordStatusChange(order, fromStatus, toStatus, "SCHEDULER");
            log.info("Order {} automatically moved from {} to {}", order.getId(), fromStatus, toStatus);
        });

        if (!orders.isEmpty()) {
            orderMetrics.recordStatusTransition(fromStatus, toStatus, orders.size());
        }
        return orders.size();
    }

    private Order findOrderOrThrow(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    private void recordStatusChange(Order order, OrderStatus from, OrderStatus to, String changedBy) {
        OrderStatusHistory history = OrderStatusHistory.builder()
                .order(order)
                .fromStatus(from)
                .toStatus(to)
                .changedBy(changedBy)
                .build();
        statusHistoryRepository.save(history);
    }
}
