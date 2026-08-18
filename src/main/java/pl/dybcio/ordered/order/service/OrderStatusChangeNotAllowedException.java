package pl.dybcio.ordered.order.service;

public class OrderStatusChangeNotAllowedException extends RuntimeException {
  public OrderStatusChangeNotAllowedException(String message) {
    super(message);
  }
}
