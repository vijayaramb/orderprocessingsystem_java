package com.order.service;

import com.order.dto.CreateOrderRequest;
import com.order.dto.OrderResponse;
import com.order.entity.Order;
import com.order.enums.OrderStatus;
import com.order.exception.InvalidOrderStateException;
import com.order.exception.OrderNotFoundException;
import com.order.mapper.OrderMapper;
import com.order.repository.OrderRepository;
import com.order.statemachine.OrderStateMachine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final OrderStateMachine orderStateMachine;

    @Override
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        Order order = orderMapper.toEntity(request);
        Order savedOrder = orderRepository.save(order);
        log.info("Order created with id: {}", savedOrder.getId());
        return orderMapper.toResponse(savedOrder);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long orderId) {
        Order order = findOrderOrThrow(orderId);
        return orderMapper.toResponse(order);
    }

    @Override
    @Transactional
    public OrderResponse updateOrderStatus(Long orderId, OrderStatus newStatus) {
        Order order = findOrderOrThrow(orderId);
        orderStateMachine.validateTransition(order.getStatus(), newStatus);

        order.setStatus(newStatus);
        Order updatedOrder = orderRepository.save(order);
        log.info("Order {} status updated to {}", orderId, newStatus);
        return orderMapper.toResponse(updatedOrder);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> getAllOrders(OrderStatus statusFilter) {
        List<Order> orders;
        if (statusFilter != null) {
            orders = orderRepository.findByStatus(statusFilter);
        } else {
            orders = orderRepository.findAll();
        }
        return orderMapper.toResponseList(orders);
    }

    @Override
    @Transactional
    public OrderResponse cancelOrder(Long orderId) {
        Order order = findOrderOrThrow(orderId);

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new InvalidOrderStateException(
                    "Only orders in PENDING status can be cancelled. Current status: " + order.getStatus()
            );
        }

        order.setStatus(OrderStatus.CANCELLED);
        Order cancelledOrder = orderRepository.save(order);
        log.info("Order {} has been cancelled", orderId);
        return orderMapper.toResponse(cancelledOrder);
    }

    @Override
    @Transactional
    public int advanceOrders(OrderStatus fromStatus, OrderStatus toStatus) {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(1);
        List<Order> orders = orderRepository.findByStatusAndUpdatedAtBefore(fromStatus, cutoff);

        orders.forEach(order -> {
            order.setStatus(toStatus);
            orderRepository.save(order);
            log.info("Order {} automatically moved from {} to {}", order.getId(), fromStatus, toStatus);
        });

        return orders.size();
    }

    private Order findOrderOrThrow(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }
}
