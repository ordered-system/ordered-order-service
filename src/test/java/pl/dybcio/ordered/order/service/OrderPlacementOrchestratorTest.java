package pl.dybcio.ordered.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.dybcio.ordered.order.client.CheckoutReservationResponse;
import pl.dybcio.ordered.order.client.ProductServiceClient;
import pl.dybcio.ordered.order.dto.AddressSnapshot;
import pl.dybcio.ordered.order.entity.Order;
import pl.dybcio.ordered.order.entity.OrderStatus;
import pl.dybcio.ordered.payment.dto.PaymentResult;
import pl.dybcio.ordered.payment.entity.Payment;
import pl.dybcio.ordered.payment.service.PaymentService;

@ExtendWith(MockitoExtension.class)
class OrderPlacementOrchestratorTest {

  @Mock private ProductServiceClient productServiceClient;
  @Mock private OrderService orderService;
  @Mock private PaymentService paymentService;

  @InjectMocks private OrderPlacementOrchestrator orchestrator;

  private final AddressSnapshot address =
      new AddressSnapshot("Adam D", "+48123456789", "Testowa", "1", null, "Torun", "87-100", "PL");

  @Test
  void reservesBeforePersisting_thenCharges_inThatOrder() {
    var reservation =
        new CheckoutReservationResponse(
            "res-1",
            List.of(
                new CheckoutReservationResponse.ReservedLine(
                    1L, "Keyboard", 2, BigDecimal.valueOf(50), BigDecimal.valueOf(100))),
            BigDecimal.valueOf(100));
    Order placedOrder = Order.builder().id(1L).buyerId(42L).status(OrderStatus.PENDING).build();
    Order confirmedOrder =
        Order.builder().id(1L).buyerId(42L).status(OrderStatus.CONFIRMED).build();
    Payment payment = Payment.builder().order(placedOrder).build();
    PaymentResult successResult = PaymentResult.success(payment);

    when(productServiceClient.reserveCartForCheckout(42L)).thenReturn(reservation);
    when(orderService.placeOrderFromReservation(42L, address, reservation)).thenReturn(placedOrder);
    when(paymentService.charge(placedOrder)).thenReturn(successResult);
    when(orderService.applyPaymentResult(1L, successResult)).thenReturn(confirmedOrder);

    Order result = orchestrator.placeOrderWithPayment(42L, address);

    assertThat(result.getStatus()).isEqualTo(OrderStatus.CONFIRMED);

    InOrder callOrder = Mockito.inOrder(productServiceClient, orderService, paymentService);
    callOrder.verify(productServiceClient).reserveCartForCheckout(42L);
    callOrder.verify(orderService).placeOrderFromReservation(42L, address, reservation);
    callOrder.verify(paymentService).charge(placedOrder);
    callOrder.verify(orderService).applyPaymentResult(1L, successResult);
  }

  @Test
  void neverPersistsOrCharges_whenReservationFails() {
    when(productServiceClient.reserveCartForCheckout(42L))
        .thenThrow(new CheckoutReservationException(42L, "cart is empty"));

    assertThatThrownBy(() -> orchestrator.placeOrderWithPayment(42L, address))
        .isInstanceOf(CheckoutReservationException.class);

    verifyNoInteractions(orderService);
    verifyNoInteractions(paymentService);
  }
}
