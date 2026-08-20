package pl.dybcio.ordered.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import pl.dybcio.ordered.order.entity.Order;
import pl.dybcio.ordered.payment.dto.PaymentResult;
import pl.dybcio.ordered.payment.entity.Payment;
import pl.dybcio.ordered.payment.entity.PaymentStatus;
import pl.dybcio.ordered.payment.repository.PaymentRepository;

@ExtendWith(MockitoExtension.class)
class StripePaymentServiceTest {

  @Mock private PaymentRepository paymentRepository;

  private StripePaymentService stripePaymentService;

  private final Order order =
      Order.builder().id(1L).buyerId(42L).totalAmount(BigDecimal.valueOf(99.99)).build();

  private StripePaymentService service() {
    if (stripePaymentService == null) {
      stripePaymentService = new StripePaymentService(paymentRepository);
    }
    return stripePaymentService;
  }

  @Test
  void charge_returnsSuccess_andPersistsSucceededPayment_withStripeIntentId() {
    try (MockedStatic<PaymentIntent> mockedIntent = mockStatic(PaymentIntent.class)) {
      PaymentIntent intent = mock(PaymentIntent.class);
      when(intent.getId()).thenReturn("pi_123");
      mockedIntent
          .when(
              () ->
                  PaymentIntent.create(
                      any(PaymentIntentCreateParams.class), any(RequestOptions.class)))
          .thenReturn(intent);

      when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.empty());
      when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

      PaymentResult result = service().charge(order);

      assertThat(result.isSuccess()).isTrue();
      assertThat(result.getPayment().getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
      assertThat(result.getPayment().getStripePaymentIntentId()).isEqualTo("pi_123");
    }
  }

  @Test
  void charge_reusesExistingPaymentRow_insteadOfCreatingANewOne() {
    Payment existing =
        Payment.builder().id(7L).order(order).amount(order.getTotalAmount()).retryCount(1).build();

    try (MockedStatic<PaymentIntent> mockedIntent = mockStatic(PaymentIntent.class)) {
      PaymentIntent intent = mock(PaymentIntent.class);
      when(intent.getId()).thenReturn("pi_456");
      mockedIntent
          .when(
              () ->
                  PaymentIntent.create(
                      any(PaymentIntentCreateParams.class), any(RequestOptions.class)))
          .thenReturn(intent);

      when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.of(existing));
      when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

      PaymentResult result = service().charge(order);

      assertThat(result.getPayment().getId()).isEqualTo(7L);
      assertThat(result.getPayment().getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
    }
  }

  @Test
  void charge_wrapsStripeException_inPaymentProcessingException() {
    StripeException stripeException = mock(StripeException.class);

    try (MockedStatic<PaymentIntent> mockedIntent = mockStatic(PaymentIntent.class)) {
      mockedIntent
          .when(
              () ->
                  PaymentIntent.create(
                      any(PaymentIntentCreateParams.class), any(RequestOptions.class)))
          .thenThrow(stripeException);

      assertThatThrownBy(() -> service().charge(order))
          .isInstanceOf(PaymentProcessingException.class)
          .hasCause(stripeException);
    }

    verifyNoInteractions(paymentRepository);
  }

  @Test
  void chargeFallback_marksPaymentPendingRetry_andIncrementsRetryCount() {
    Payment existing =
        Payment.builder()
            .id(7L)
            .order(order)
            .amount(order.getTotalAmount())
            .retryCount(2)
            .status(PaymentStatus.PENDING_RETRY)
            .build();
    when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.of(existing));
    when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

    PaymentResult result =
        ReflectionTestUtils.invokeMethod(
            service(), "chargeFallback", order, new RuntimeException("stripe unreachable"));

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.getPayment().getStatus()).isEqualTo(PaymentStatus.PENDING_RETRY);
    assertThat(result.getPayment().getRetryCount()).isEqualTo(3);
  }

  @Test
  void chargeFallback_createsNewPendingRetryPayment_whenNoneExistsYet() {
    when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.empty());
    when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

    PaymentResult result =
        ReflectionTestUtils.invokeMethod(
            service(), "chargeFallback", order, new RuntimeException("stripe unreachable"));

    assertThat(result.getPayment().getRetryCount()).isEqualTo(1);
    assertThat(result.getPayment().getStatus()).isEqualTo(PaymentStatus.PENDING_RETRY);
  }
}
