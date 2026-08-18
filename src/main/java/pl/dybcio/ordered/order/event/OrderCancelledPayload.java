package pl.dybcio.ordered.order.event;

import java.time.Instant;
import pl.dybcio.ordered.order.entity.Order;

public record OrderCancelledPayload(Long orderId, String reservationId, Instant cancelledAt) {

  public static OrderCancelledPayload from(Order order) {
    return new OrderCancelledPayload(order.getId(), order.getReservationId(), Instant.now());
  }
}
