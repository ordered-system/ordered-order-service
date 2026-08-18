package pl.dybcio.ordered.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.dybcio.ordered.order.client.CheckoutReservationResponse;
import pl.dybcio.ordered.order.client.ProductServiceClient;
import pl.dybcio.ordered.order.dto.AddressSnapshot;
import pl.dybcio.ordered.order.entity.Order;
import pl.dybcio.ordered.payment.dto.PaymentResult;
import pl.dybcio.ordered.payment.service.PaymentService;

@Service
@RequiredArgsConstructor
public class OrderPlacementOrchestrator {

  private final ProductServiceClient productServiceClient;
  private final OrderService orderService;
  private final PaymentService paymentService;

  public Order placeOrderWithPayment(Long buyerId, AddressSnapshot deliveryAddress) {
    CheckoutReservationResponse reservation = productServiceClient.reserveCartForCheckout(buyerId);

    Order order = orderService.placeOrderFromReservation(buyerId, deliveryAddress, reservation);

    PaymentResult result = paymentService.charge(order);
    return orderService.applyPaymentResult(order.getId(), result);
  }
}
