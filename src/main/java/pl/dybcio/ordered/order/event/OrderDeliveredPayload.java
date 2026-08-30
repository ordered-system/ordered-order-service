package pl.dybcio.ordered.order.event;

import java.time.Instant;
import java.util.List;
import pl.dybcio.ordered.order.entity.Order;

public record OrderDeliveredPayload(
    Long orderId, Long buyerId, List<Long> productIds, Instant deliveredAt) {

  public static OrderDeliveredPayload from(Order order) {
    List<Long> productIds = order.getItems().stream().map(item -> item.getProductId()).toList();
    return new OrderDeliveredPayload(order.getId(), order.getBuyerId(), productIds, Instant.now());
  }
}
