package pl.dybcio.ordered.order.service;

import pl.dybcio.ordered.order.entity.OrderStatus;

public class InvalidOrderStatusTransitionException extends RuntimeException {
  public InvalidOrderStatusTransitionException(OrderStatus from, OrderStatus to) {
    super("Cannot transition order from %s to %s".formatted(from, to));
  }
}
