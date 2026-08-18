package pl.dybcio.ordered.order.service;

public class OrderNotFoundException extends RuntimeException {
  public OrderNotFoundException(Long orderId) {
    super("Order not found: " + orderId);
  }
}
