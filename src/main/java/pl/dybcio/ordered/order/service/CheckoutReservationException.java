package pl.dybcio.ordered.order.service;

public class CheckoutReservationException extends RuntimeException {
  public CheckoutReservationException(Long buyerId, String detail) {
    super("Could not reserve cart for buyer %d: %s".formatted(buyerId, detail));
  }
}
