package com.order.statemachine;

import com.order.enums.OrderStatus;
import com.order.exception.InvalidOrderStateException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Implements the State pattern for order status transitions.
 * Enforces valid state transitions and prevents illegal status changes.
 */
@Component
public class OrderStateMachine {

    private static final Map<OrderStatus, Set<OrderStatus>> VALID_TRANSITIONS = Map.of(
            OrderStatus.PENDING, Set.of(OrderStatus.PROCESSING, OrderStatus.CANCELLED),
            OrderStatus.PROCESSING, Set.of(OrderStatus.SHIPPING),
            OrderStatus.SHIPPING, Set.of(OrderStatus.DELIVERED),
            OrderStatus.DELIVERED, Set.of(),
            OrderStatus.CANCELLED, Set.of()
    );

    public void validateTransition(OrderStatus currentStatus, OrderStatus targetStatus) {
        Set<OrderStatus> allowedTargets = VALID_TRANSITIONS.get(currentStatus);

        if (allowedTargets == null || !allowedTargets.contains(targetStatus)) {
            throw new InvalidOrderStateException(
                    String.format("Cannot transition from %s to %s", currentStatus, targetStatus)
            );
        }
    }

    public boolean canTransition(OrderStatus currentStatus, OrderStatus targetStatus) {
        Set<OrderStatus> allowedTargets = VALID_TRANSITIONS.get(currentStatus);
        return allowedTargets != null && allowedTargets.contains(targetStatus);
    }
}
