package pl.dybcio.ordered.order.service;

import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.dybcio.ordered.order.client.CheckoutReservationResponse;
import pl.dybcio.ordered.order.dto.AddressSnapshot;
import pl.dybcio.ordered.order.dto.OrderResponse;
import pl.dybcio.ordered.order.entity.Order;
import pl.dybcio.ordered.order.entity.OrderItem;
import pl.dybcio.ordered.order.entity.OrderStatus;
import pl.dybcio.ordered.order.event.OrderCancelledPayload;
import pl.dybcio.ordered.order.event.OrderPlacedPayload;
import pl.dybcio.ordered.order.repository.OrderRepository;
import pl.dybcio.ordered.outbox.entity.OutboxEvent;
import pl.dybcio.ordered.outbox.repository.OutboxEventRepository;
import pl.dybcio.ordered.payment.dto.PaymentResult;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class OrderService {

  private final OrderRepository orderRepository;
  private final OutboxEventRepository outboxEventRepository;
  private final ObjectMapper objectMapper;

  @Transactional
  public Order placeOrderFromReservation(
      Long buyerId, AddressSnapshot address, CheckoutReservationResponse reservation) {

    Order order =
        Order.builder()
            .buyerId(buyerId)
            .status(OrderStatus.PENDING)
            .reservationId(reservation.reservationId())
            .deliveryAddress(address.toEmbeddable())
            .totalAmount(reservation.totalAmount())
            .build();

    for (CheckoutReservationResponse.ReservedLine line : reservation.lines()) {
      order.addItem(
          OrderItem.builder()
              .productId(line.productId())
              .productName(line.productName())
              .quantity(line.quantity())
              .unitPrice(line.unitPrice())
              .subtotal(line.subtotal())
              .build());
    }

    Order savedOrder = orderRepository.save(order);

    OrderPlacedPayload payload = OrderPlacedPayload.from(savedOrder);
    outboxEventRepository.save(
        OutboxEvent.builder()
            .aggregateType("Order")
            .aggregateId(savedOrder.getId().toString())
            .eventType("OrderPlaced")
            .payload(objectMapper.writeValueAsString(payload))
            .build());

    Hibernate.initialize(savedOrder.getItems());
    return savedOrder;
  }

  @Transactional(readOnly = true)
  public Order getOrderForUser(Long orderId, Long requestingUserId, boolean isAdmin) {
    Order order =
        orderRepository.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));

    if (!isAdmin && !order.getBuyerId().equals(requestingUserId)) {
      throw new OrderNotFoundException(orderId);
    }

    Hibernate.initialize(order.getItems());
    return order;
  }

  @Transactional(readOnly = true)
  public Page<OrderResponse> listOrdersForUser(Long buyerId, Pageable pageable) {
    return orderRepository.findByBuyerId(buyerId, pageable).map(OrderResponse::from);
  }

  private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS =
      Map.of(
          OrderStatus.PENDING,
          Set.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED, OrderStatus.PAYMENT_PENDING),
          OrderStatus.PAYMENT_PENDING,
          Set.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED),
          OrderStatus.CONFIRMED,
          Set.of(OrderStatus.SHIPPED, OrderStatus.CANCELLED),
          OrderStatus.SHIPPED,
          Set.of(OrderStatus.DELIVERED),
          OrderStatus.DELIVERED,
          Set.of(),
          OrderStatus.CANCELLED,
          Set.of());

  @Transactional
  public Order updateStatus(
      Long orderId, Long actingUserId, boolean isAdmin, OrderStatus newStatus) {
    Order order =
        orderRepository.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));

    authorizeStatusChange(order, actingUserId, isAdmin, newStatus);

    Set<OrderStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(order.getStatus(), Set.of());
    if (!allowed.contains(newStatus)) {
      throw new InvalidOrderStatusTransitionException(order.getStatus(), newStatus);
    }

    order.setStatus(newStatus);
    Order saved = orderRepository.save(order);
    Hibernate.initialize(saved.getItems());
    return saved;
  }

  private void authorizeStatusChange(
      Order order, Long actingUserId, boolean isAdmin, OrderStatus newStatus) {
    if (isAdmin) {
      return;
    }

    boolean isBuyer = order.getBuyerId().equals(actingUserId);
    if (isBuyer && newStatus == OrderStatus.CANCELLED) {
      return;
    }

    throw new OrderStatusChangeNotAllowedException(
        "User %d is not allowed to change order %d to %s"
            .formatted(actingUserId, order.getId(), newStatus));
  }

  @Transactional
  public Order applyPaymentResult(Long orderId, PaymentResult result) {
    Order order =
        orderRepository.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
    order.setStatus(result.isSuccess() ? OrderStatus.CONFIRMED : OrderStatus.PAYMENT_PENDING);
    Order saved = orderRepository.save(order);
    Hibernate.initialize(saved.getItems());
    return saved;
  }

  @Transactional
  public Order cancelDueToPaymentFailure(Long orderId) {
    Order order =
        orderRepository.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
    order.setStatus(OrderStatus.CANCELLED);
    Order saved = orderRepository.save(order);

    OrderCancelledPayload payload = OrderCancelledPayload.from(saved);
    outboxEventRepository.save(
        OutboxEvent.builder()
            .aggregateType("Order")
            .aggregateId(saved.getId().toString())
            .eventType("OrderCancelled")
            .payload(objectMapper.writeValueAsString(payload))
            .build());

    return saved;
  }
}
