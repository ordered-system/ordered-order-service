package pl.dybcio.ordered.payment.service;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pl.dybcio.ordered.order.entity.Order;
import pl.dybcio.ordered.payment.dto.PaymentResult;
import pl.dybcio.ordered.payment.entity.Payment;
import pl.dybcio.ordered.payment.entity.PaymentStatus;
import pl.dybcio.ordered.payment.repository.PaymentRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class StripePaymentService implements PaymentService {

  private final PaymentRepository paymentRepository;

  @Value("${stripe.api-key}")
  private String stripeApiKey;

  @PostConstruct
  void init() {
    Stripe.apiKey = stripeApiKey;
  }

  @CircuitBreaker(name = "stripePayments")
  @Retry(name = "stripePayments", fallbackMethod = "chargeFallback")
  public PaymentResult charge(Order order) {
    try {
      RequestOptions requestOptions =
          RequestOptions.builder()
              .setIdempotencyKey("order-" + order.getId())
              .setConnectTimeout(3000)
              .setReadTimeout(3000)
              .build();

      PaymentIntentCreateParams params =
          PaymentIntentCreateParams.builder()
              .setAmount(order.getTotalAmount().multiply(BigDecimal.valueOf(100)).longValueExact())
              .setCurrency("pln")
              .setConfirm(true)
              .setPaymentMethod("pm_card_visa")
              .addPaymentMethodType("card")
              .build();

      PaymentIntent intent = PaymentIntent.create(params, requestOptions);

      Payment payment = findOrCreatePayment(order);
      payment.setStripePaymentIntentId(intent.getId());
      payment.setStatus(PaymentStatus.SUCCEEDED);
      payment = paymentRepository.save(payment);

      return PaymentResult.success(payment);
    } catch (StripeException e) {
      log.warn("Stripe charge failed for order {}: {}", order.getId(), e.getMessage());
      throw new PaymentProcessingException(order.getId(), e);
    }
  }

  private PaymentResult chargeFallback(Order order, Throwable t) {
    log.error("Payment fallback triggered for order {}: {}", order.getId(), t.getMessage());

    Payment payment = findOrCreatePayment(order);
    payment.setStatus(PaymentStatus.PENDING_RETRY);
    payment.setRetryCount(payment.getRetryCount() + 1);
    payment = paymentRepository.save(payment);

    return PaymentResult.pendingRetry(payment);
  }

  private Payment findOrCreatePayment(Order order) {
    return paymentRepository
        .findByOrderId(order.getId())
        .orElseGet(() -> Payment.builder().order(order).amount(order.getTotalAmount()).build());
  }
}
