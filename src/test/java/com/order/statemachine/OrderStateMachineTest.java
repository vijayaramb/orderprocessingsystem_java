package com.order.statemachine;

import com.order.enums.OrderStatus;
import com.order.exception.InvalidOrderStateException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderStateMachineTest {

    private OrderStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new OrderStateMachine();
    }

    @ParameterizedTest
    @DisplayName("should allow valid transitions")
    @CsvSource({
            "PENDING, PROCESSING",
            "PENDING, CANCELLED",
            "PROCESSING, SHIPPING",
            "SHIPPING, DELIVERED"
    })
    void shouldAllowValidTransitions(OrderStatus from, OrderStatus to) {
        stateMachine.validateTransition(from, to);
        assertThat(stateMachine.canTransition(from, to)).isTrue();
    }

    @ParameterizedTest
    @DisplayName("should reject invalid transitions")
    @CsvSource({
            "PENDING, DELIVERED",
            "PENDING, SHIPPING",
            "PROCESSING, PENDING",
            "PROCESSING, CANCELLED",
            "PROCESSING, DELIVERED",
            "SHIPPING, PENDING",
            "SHIPPING, PROCESSING",
            "SHIPPING, CANCELLED",
            "DELIVERED, PENDING",
            "DELIVERED, PROCESSING",
            "DELIVERED, SHIPPING",
            "DELIVERED, CANCELLED",
            "CANCELLED, PENDING",
            "CANCELLED, PROCESSING",
            "CANCELLED, SHIPPING",
            "CANCELLED, DELIVERED"
    })
    void shouldRejectInvalidTransitions(OrderStatus from, OrderStatus to) {
        assertThatThrownBy(() -> stateMachine.validateTransition(from, to))
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessageContaining(from.name())
                .hasMessageContaining(to.name());
    }

    @Test
    @DisplayName("canTransition should return false for invalid transitions")
    void canTransitionShouldReturnFalseForInvalid() {
        assertThat(stateMachine.canTransition(OrderStatus.DELIVERED, OrderStatus.PENDING)).isFalse();
        assertThat(stateMachine.canTransition(OrderStatus.CANCELLED, OrderStatus.PROCESSING)).isFalse();
    }
}
