package pl.dybcio.ordered.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import pl.dybcio.ordered.order.entity.Order;
import pl.dybcio.ordered.order.repository.OrderRepository;
import pl.dybcio.ordered.order.service.OrderService;
import pl.dybcio.ordered.payment.dto.PaymentResult;
import pl.dybcio.ordered.payment.entity.Payment;
import pl.dybcio.ordered.payment.entity.PaymentStatus;
import pl.dybcio.ordered.payment.repository.PaymentRepository;

@ExtendWith(MockitoExtension.class)
class PaymentRetrySchedulerTest {

  @Mock private PaymentRepository paymentRepository;
  @Mock private OrderRepository orderRepository;
  @Mock private PaymentService paymentService;
  @Mock private OrderService orderService;

  private PaymentRetryScheduler scheduler;

  @BeforeEach
  void setUp() {
    scheduler =
        new PaymentRetryScheduler(paymentRepository, orderRepository, paymentService, orderService);
    ReflectionTestUtils.setField(scheduler, "maxRetryAttempts", 5);
  }

  @Test
  void retriesEachPendingPayment_whenItsOrderStillExists() {
    Order order = Order.builder().id(1L).buyerId(42L).build();
    Payment payment =
        Payment.builder()
            .id(10L)
            .order(order)
            .retryCount(1)
            .status(PaymentStatus.PENDING_RETRY)
            .build();
    when(paymentRepository.findByStatusAndRetryCountLessThan(PaymentStatus.PENDING_RETRY, 5))
        .thenReturn(List.of(payment));
    when(paymentRepository.findByStatusAndRetryCountGreaterThanEqual(
            PaymentStatus.PENDING_RETRY, 5))
        .thenReturn(List.of());
    when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
    PaymentResult retryResult = PaymentResult.pendingRetry(payment);
    when(paymentService.charge(order)).thenReturn(retryResult);

    scheduler.retryPendingPayments();

    verify(paymentService).charge(order);
    verify(orderService).applyPaymentResult(1L, retryResult);
  }

  @Test
  void skipsRetry_whenOrderNoLongerExists() {
    Order phantomOrder = Order.builder().id(99L).build();
    Payment payment =
        Payment.builder()
            .id(11L)
            .order(phantomOrder)
            .retryCount(1)
            .status(PaymentStatus.PENDING_RETRY)
            .build();
    when(paymentRepository.findByStatusAndRetryCountLessThan(PaymentStatus.PENDING_RETRY, 5))
        .thenReturn(List.of(payment));
    when(paymentRepository.findByStatusAndRetryCountGreaterThanEqual(
            PaymentStatus.PENDING_RETRY, 5))
        .thenReturn(List.of());
    when(orderRepository.findById(99L)).thenReturn(Optional.empty());

    scheduler.retryPendingPayments();

    verifyNoInteractions(paymentService);
    verify(orderService, never()).applyPaymentResult(anyLong(), any());
  }

  @Test
  void cancelsOrderAndMarksPaymentFailed_whenRetriesAreExhausted() {
    Order order = Order.builder().id(2L).build();
    Payment exhausted =
        Payment.builder()
            .id(12L)
            .order(order)
            .retryCount(5)
            .status(PaymentStatus.PENDING_RETRY)
            .build();
    when(paymentRepository.findByStatusAndRetryCountLessThan(PaymentStatus.PENDING_RETRY, 5))
        .thenReturn(List.of());
    when(paymentRepository.findByStatusAndRetryCountGreaterThanEqual(
            PaymentStatus.PENDING_RETRY, 5))
        .thenReturn(List.of(exhausted));
    when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

    scheduler.retryPendingPayments();

    verify(orderService).cancelDueToPaymentFailure(2L);
    assertThat(exhausted.getStatus()).isEqualTo(PaymentStatus.FAILED);
    verify(paymentRepository).save(exhausted);
    verifyNoInteractions(paymentService);
  }

  @Test
  void processesRetryableAndExhaustedPaymentsInTheSameRun() {
    Order retryableOrder = Order.builder().id(1L).build();
    Payment retryable =
        Payment.builder()
            .id(10L)
            .order(retryableOrder)
            .retryCount(1)
            .status(PaymentStatus.PENDING_RETRY)
            .build();
    Order exhaustedOrder = Order.builder().id(2L).build();
    Payment exhausted =
        Payment.builder()
            .id(12L)
            .order(exhaustedOrder)
            .retryCount(5)
            .status(PaymentStatus.PENDING_RETRY)
            .build();

    when(paymentRepository.findByStatusAndRetryCountLessThan(PaymentStatus.PENDING_RETRY, 5))
        .thenReturn(List.of(retryable));
    when(paymentRepository.findByStatusAndRetryCountGreaterThanEqual(
            PaymentStatus.PENDING_RETRY, 5))
        .thenReturn(List.of(exhausted));
    when(orderRepository.findById(1L)).thenReturn(Optional.of(retryableOrder));
    when(paymentService.charge(retryableOrder)).thenReturn(PaymentResult.pendingRetry(retryable));
    when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

    scheduler.retryPendingPayments();

    verify(paymentService).charge(retryableOrder);
    verify(orderService).cancelDueToPaymentFailure(2L);
  }
}
