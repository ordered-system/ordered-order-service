package pl.dybcio.ordered.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pl.dybcio.ordered.order.client.CheckoutReservationResponse;
import pl.dybcio.ordered.order.client.ProductServiceClient;
import pl.dybcio.ordered.order.dto.AddressSnapshot;
import pl.dybcio.ordered.order.entity.Order;
import pl.dybcio.ordered.order.entity.OrderStatus;
import pl.dybcio.ordered.order.repository.OrderRepository;
import pl.dybcio.ordered.order.service.OrderPlacementOrchestrator;
import pl.dybcio.ordered.outbox.entity.OutboxEvent;
import pl.dybcio.ordered.outbox.repository.OutboxEventRepository;
import pl.dybcio.ordered.payment.entity.Payment;
import pl.dybcio.ordered.payment.entity.PaymentStatus;
import pl.dybcio.ordered.payment.repository.PaymentRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class StripePaymentIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void disableEureka(DynamicPropertyRegistry registry) {
    registry.add("eureka.client.enabled", () -> "false");
    registry.add("app.jwt.secret", () -> "test-only-signing-secret-not-used-for-any-real-auth");
  }

  @Autowired private OrderPlacementOrchestrator orchestrator;
  @Autowired private OrderRepository orderRepository;
  @Autowired private PaymentRepository paymentRepository;
  @Autowired private OutboxEventRepository outboxEventRepository;
  @Autowired private CircuitBreakerRegistry circuitBreakerRegistry;

  @MockitoBean private ProductServiceClient productServiceClient;

  private final AddressSnapshot address =
      new AddressSnapshot("Adam D", "+48123456789", "Testowa", "1", null, "Torun", "87-100", "PL");

  @BeforeEach
  void resetCircuitBreakerBetweenTests() {
    circuitBreakerRegistry.circuitBreaker("stripePayments").reset();
    when(productServiceClient.reserveCartForCheckout(eq(42L))).thenReturn(sampleReservation());
  }

  private CheckoutReservationResponse sampleReservation() {
    return new CheckoutReservationResponse(
        "res-1",
        List.of(
            new CheckoutReservationResponse.ReservedLine(
                1L, "Keyboard", 1, BigDecimal.valueOf(199.99), BigDecimal.valueOf(199.99))),
        BigDecimal.valueOf(199.99));
  }

  @Test
  void placesOrderAndConfirmsIt_whenStripeSucceeds() {
    try (MockedStatic<PaymentIntent> mockedIntent = mockStatic(PaymentIntent.class)) {
      PaymentIntent intent = mock(PaymentIntent.class);
      when(intent.getId()).thenReturn("pi_success");
      mockedIntent
          .when(
              () ->
                  PaymentIntent.create(
                      any(PaymentIntentCreateParams.class), any(RequestOptions.class)))
          .thenReturn(intent);

      Order order = orchestrator.placeOrderWithPayment(42L, address);

      assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
      assertThat(order.getItems()).hasSize(1);

      Order persisted = orderRepository.findById(order.getId()).orElseThrow();
      assertThat(persisted.getStatus()).isEqualTo(OrderStatus.CONFIRMED);

      Payment payment = paymentRepository.findByOrderId(order.getId()).orElseThrow();
      assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
      assertThat(payment.getStripePaymentIntentId()).isEqualTo("pi_success");

      List<OutboxEvent> events = outboxEventRepository.findAll();
      assertThat(events)
          .anySatisfy(
              e -> {
                assertThat(e.getEventType()).isEqualTo("OrderPlaced");
                assertThat(e.getAggregateId()).isEqualTo(order.getId().toString());
              });
    }
  }

  @Test
  void ordersStayPaymentPending_whenStripeKeepsFailing() {
    try (MockedStatic<PaymentIntent> mockedIntent = mockStatic(PaymentIntent.class)) {
      StripeException failure = mock(StripeException.class);
      mockedIntent
          .when(
              () ->
                  PaymentIntent.create(
                      any(PaymentIntentCreateParams.class), any(RequestOptions.class)))
          .thenThrow(failure);

      Order order = orchestrator.placeOrderWithPayment(42L, address);

      assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);

      Payment payment = paymentRepository.findByOrderId(order.getId()).orElseThrow();
      assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING_RETRY);
      assertThat(payment.getRetryCount()).isGreaterThanOrEqualTo(1);
    }
  }

  @Test
  void circuitBreakerOpens_afterEnoughConsecutiveStripeFailures() {
    try (MockedStatic<PaymentIntent> mockedIntent = mockStatic(PaymentIntent.class)) {
      StripeException failure = mock(StripeException.class);
      mockedIntent
          .when(
              () ->
                  PaymentIntent.create(
                      any(PaymentIntentCreateParams.class), any(RequestOptions.class)))
          .thenThrow(failure);

      for (int i = 0; i < 10; i++) {
        orchestrator.placeOrderWithPayment(42L, address);
      }

      CircuitBreaker breaker = circuitBreakerRegistry.circuitBreaker("stripePayments");
      assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }
  }

  @Test
  void reservationFailure_neverPersistsAnOrder() {
    when(productServiceClient.reserveCartForCheckout(eq(42L)))
        .thenThrow(
            new pl.dybcio.ordered.order.service.CheckoutReservationException(42L, "cart is empty"));

    long ordersBefore = orderRepository.count();

    org.junit.jupiter.api.Assertions.assertThrows(
        pl.dybcio.ordered.order.service.CheckoutReservationException.class,
        () -> orchestrator.placeOrderWithPayment(42L, address));

    assertThat(orderRepository.count()).isEqualTo(ordersBefore);
  }
}
