package pl.dybcio.ordered.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import pl.dybcio.ordered.order.client.CheckoutReservationResponse;
import pl.dybcio.ordered.order.dto.AddressSnapshot;
import pl.dybcio.ordered.order.dto.OrderResponse;
import pl.dybcio.ordered.order.entity.Order;
import pl.dybcio.ordered.order.entity.OrderStatus;
import pl.dybcio.ordered.order.repository.OrderRepository;
import pl.dybcio.ordered.outbox.entity.OutboxEvent;
import pl.dybcio.ordered.outbox.repository.OutboxEventRepository;
import pl.dybcio.ordered.payment.dto.PaymentResult;
import pl.dybcio.ordered.payment.entity.Payment;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

  @Mock private OrderRepository orderRepository;
  @Mock private OutboxEventRepository outboxEventRepository;

  private OrderService orderService;

  private final AddressSnapshot address =
      new AddressSnapshot("Adam D", "+48123456789", "Testowa", "1", null, "Torun", "87-100", "PL");

  @BeforeEach
  void setUp() {
    orderService = new OrderService(orderRepository, outboxEventRepository, new ObjectMapper());
  }

  @Test
  void placeOrderFromReservation_buildsOrderFromReservationLines_andWritesOutboxEvent() {
    var reservation =
        new CheckoutReservationResponse(
            "res-1",
            List.of(
                new CheckoutReservationResponse.ReservedLine(
                    1L, "Keyboard", 2, BigDecimal.valueOf(50), BigDecimal.valueOf(100)),
                new CheckoutReservationResponse.ReservedLine(
                    2L, "Mouse", 1, BigDecimal.valueOf(80), BigDecimal.valueOf(80))),
            BigDecimal.valueOf(180));

    when(orderRepository.save(any(Order.class)))
        .thenAnswer(
            invocation -> {
              Order o = invocation.getArgument(0);
              o.setId(100L);
              return o;
            });

    Order result = orderService.placeOrderFromReservation(42L, address, reservation);

    assertThat(result.getId()).isEqualTo(100L);
    assertThat(result.getBuyerId()).isEqualTo(42L);
    assertThat(result.getStatus()).isEqualTo(OrderStatus.PENDING);
    assertThat(result.getReservationId()).isEqualTo("res-1");
    assertThat(result.getTotalAmount()).isEqualByComparingTo("180");
    assertThat(result.getItems()).hasSize(2);
    assertThat(result.getItems())
        .extracting("productId", "productName", "quantity")
        .containsExactlyInAnyOrder(
            org.assertj.core.groups.Tuple.tuple(1L, "Keyboard", 2),
            org.assertj.core.groups.Tuple.tuple(2L, "Mouse", 1));
    assertThat(result.getItems()).allSatisfy(item -> assertThat(item.getOrder()).isSameAs(result));

    ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
    verify(outboxEventRepository).save(captor.capture());
    OutboxEvent event = captor.getValue();
    assertThat(event.getAggregateType()).isEqualTo("Order");
    assertThat(event.getAggregateId()).isEqualTo("100");
    assertThat(event.getEventType()).isEqualTo("OrderPlaced");
    assertThat(event.getPayload()).contains("\"orderId\":100").contains("\"buyerId\":42");
  }

  @Test
  void getOrderForUser_returnsOrder_whenRequestingUserIsOwner() {
    Order order = Order.builder().id(1L).buyerId(42L).status(OrderStatus.PENDING).build();
    when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

    Order result = orderService.getOrderForUser(1L, 42L, false);

    assertThat(result).isSameAs(order);
  }

  @Test
  void getOrderForUser_returnsOrder_forAdmin_regardlessOfOwnership() {
    Order order = Order.builder().id(1L).buyerId(42L).status(OrderStatus.PENDING).build();
    when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

    Order result = orderService.getOrderForUser(1L, 999L, true);

    assertThat(result).isSameAs(order);
  }

  @Test
  void getOrderForUser_throwsOrderNotFound_whenRequestingUserIsNotOwnerAndNotAdmin() {
    Order order = Order.builder().id(1L).buyerId(42L).status(OrderStatus.PENDING).build();
    when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

    assertThatThrownBy(() -> orderService.getOrderForUser(1L, 999L, false))
        .isInstanceOf(OrderNotFoundException.class);
  }

  @Test
  void getOrderForUser_throwsOrderNotFound_whenOrderDoesNotExist() {
    when(orderRepository.findById(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> orderService.getOrderForUser(1L, 42L, false))
        .isInstanceOf(OrderNotFoundException.class);
  }

  @Test
  void listOrdersForUser_delegatesToRepository_andMapsOrdersToResponsesInsideTheTransaction() {
    Pageable pageable = Pageable.ofSize(20);
    Order order =
        Order.builder()
            .id(1L)
            .buyerId(42L)
            .status(OrderStatus.CONFIRMED)
            .totalAmount(BigDecimal.valueOf(99.99))
            .items(List.of())
            .build();
    Page<Order> page = new PageImpl<>(List.of(order));
    when(orderRepository.findByBuyerId(42L, pageable)).thenReturn(page);

    Page<OrderResponse> result = orderService.listOrdersForUser(42L, pageable);

    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).id()).isEqualTo(1L);
    assertThat(result.getContent().get(0).buyerId()).isEqualTo(42L);
    verify(orderRepository).findByBuyerId(42L, pageable);
  }

  @Test
  void updateStatus_allowsAdminToConfirmPendingOrder() {
    Order order = Order.builder().id(1L).buyerId(42L).status(OrderStatus.PENDING).build();
    when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
    when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

    Order result = orderService.updateStatus(1L, 999L, true, OrderStatus.CONFIRMED);

    assertThat(result.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
  }

  @Test
  void updateStatus_allowsBuyerToCancelTheirOwnPendingOrder() {
    Order order = Order.builder().id(1L).buyerId(42L).status(OrderStatus.PENDING).build();
    when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
    when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

    Order result = orderService.updateStatus(1L, 42L, false, OrderStatus.CANCELLED);

    assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
  }

  @Test
  void updateStatus_rejectsBuyerTryingToShipTheirOwnOrder() {
    Order order = Order.builder().id(1L).buyerId(42L).status(OrderStatus.PENDING).build();
    when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

    assertThatThrownBy(() -> orderService.updateStatus(1L, 42L, false, OrderStatus.SHIPPED))
        .isInstanceOf(OrderStatusChangeNotAllowedException.class);

    verify(orderRepository, never()).save(any());
  }

  @Test
  void updateStatus_rejectsNonOwnerNonAdminEntirely() {
    Order order = Order.builder().id(1L).buyerId(42L).status(OrderStatus.PENDING).build();
    when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

    assertThatThrownBy(() -> orderService.updateStatus(1L, 555L, false, OrderStatus.CANCELLED))
        .isInstanceOf(OrderStatusChangeNotAllowedException.class);
  }

  @Test
  void updateStatus_rejectsInvalidTransition_evenForAdmin() {
    // admin bypasses ownership checks, but not the state machine itself
    Order order = Order.builder().id(1L).buyerId(42L).status(OrderStatus.DELIVERED).build();
    when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

    assertThatThrownBy(() -> orderService.updateStatus(1L, 999L, true, OrderStatus.PENDING))
        .isInstanceOf(InvalidOrderStatusTransitionException.class);

    verify(orderRepository, never()).save(any());
  }

  @Test
  void updateStatus_throwsOrderNotFound_whenOrderMissing() {
    when(orderRepository.findById(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> orderService.updateStatus(1L, 42L, false, OrderStatus.CANCELLED))
        .isInstanceOf(OrderNotFoundException.class);
  }

  @Test
  void applyPaymentResult_confirmsOrder_onSuccessfulPayment() {
    Order order = Order.builder().id(1L).status(OrderStatus.PENDING).build();
    when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
    when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

    Order result =
        orderService.applyPaymentResult(1L, PaymentResult.success(Payment.builder().build()));

    assertThat(result.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
  }

  @Test
  void applyPaymentResult_marksPaymentPending_onFailedPayment() {
    Order order = Order.builder().id(1L).status(OrderStatus.PENDING).build();
    when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
    when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

    Order result =
        orderService.applyPaymentResult(1L, PaymentResult.pendingRetry(Payment.builder().build()));

    assertThat(result.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
  }

  @Test
  void cancelDueToPaymentFailure_cancelsOrder_andPublishesOrderCancelledEvent() {
    Order order =
        Order.builder().id(1L).status(OrderStatus.PAYMENT_PENDING).reservationId("res-1").build();
    when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
    when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

    Order result = orderService.cancelDueToPaymentFailure(1L);

    assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);

    ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
    verify(outboxEventRepository).save(captor.capture());
    assertThat(captor.getValue().getEventType()).isEqualTo("OrderCancelled");
    assertThat(captor.getValue().getPayload()).contains("\"reservationId\":\"res-1\"");
  }

  @Test
  void cancelDueToPaymentFailure_throwsOrderNotFound_whenOrderMissing() {
    when(orderRepository.findById(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> orderService.cancelDueToPaymentFailure(1L))
        .isInstanceOf(OrderNotFoundException.class);

    verifyNoInteractions(outboxEventRepository);
  }
}
