package pl.dybcio.ordered.payment.service;

public class PaymentProcessingException extends RuntimeException {
  public PaymentProcessingException(Long orderId, Throwable cause) {
    super("Payment processing failed for order " + orderId, cause);
  }
}
