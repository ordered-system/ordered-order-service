package pl.dybcio.ordered.payment.dto;

import lombok.Getter;
import pl.dybcio.ordered.payment.entity.Payment;

@Getter
public class PaymentResult {

  private final boolean success;
  private final Payment payment;

  private PaymentResult(boolean success, Payment payment) {
    this.success = success;
    this.payment = payment;
  }

  public static PaymentResult success(Payment payment) {
    return new PaymentResult(true, payment);
  }

  public static PaymentResult pendingRetry(Payment payment) {
    return new PaymentResult(false, payment);
  }
}
