package pl.dybcio.ordered.payment.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pl.dybcio.ordered.order.repository.OrderRepository;
import pl.dybcio.ordered.order.service.OrderService;
import pl.dybcio.ordered.payment.dto.PaymentResult;
import pl.dybcio.ordered.payment.entity.Payment;
import pl.dybcio.ordered.payment.entity.PaymentStatus;
import pl.dybcio.ordered.payment.repository.PaymentRepository;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentRetryScheduler {

  private final PaymentRepository paymentRepository;
  private final OrderRepository orderRepository;
  private final PaymentService paymentService;
  private final OrderService orderService;

  @Value("${app.payment.max-retry-attempts:5}")
  private int maxRetryAttempts;

  @Scheduled(fixedDelayString = "${app.payment.retry-poll-interval-ms:300000}")
  public void retryPendingPayments() {
    List<Payment> retryable =
        paymentRepository.findByStatusAndRetryCountLessThan(
            PaymentStatus.PENDING_RETRY, maxRetryAttempts);

    for (Payment payment : retryable) {
      retrySingle(payment);
    }

    List<Payment> exhausted =
        paymentRepository.findByStatusAndRetryCountGreaterThanEqual(
            PaymentStatus.PENDING_RETRY, maxRetryAttempts);

    for (Payment payment : exhausted) {
      giveUp(payment);
    }
  }

  private void retrySingle(Payment payment) {
    Long orderId = payment.getOrder().getId();
    orderRepository
        .findById(orderId)
        .ifPresentOrElse(
            order -> {
              log.info(
                  "Retrying payment for order {} (attempt {})",
                  orderId,
                  payment.getRetryCount() + 1);
              PaymentResult result = paymentService.charge(order);
              orderService.applyPaymentResult(orderId, result);
            },
            () ->
                log.warn(
                    "Order {} referenced by payment {} no longer exists",
                    orderId,
                    payment.getId()));
  }

  private void giveUp(Payment payment) {
    Long orderId = payment.getOrder().getId();
    log.warn(
        "Payment retries exhausted for order {}, cancelling and releasing reservation", orderId);
    orderService.cancelDueToPaymentFailure(orderId);

    payment.setStatus(PaymentStatus.FAILED);
    paymentRepository.save(payment);
  }
}
