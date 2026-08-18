package pl.dybcio.ordered.order.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import pl.dybcio.ordered.order.entity.Order;

public record OrderPlacedPayload(
    Long orderId, Long buyerId, List<Item> items, BigDecimal totalAmount, Instant placedAt) {

  public record Item(Long productId, int quantity, BigDecimal unitPrice) {}

  public static OrderPlacedPayload from(Order order) {
    List<Item> items =
        order.getItems().stream()
            .map(i -> new Item(i.getProductId(), i.getQuantity(), i.getUnitPrice()))
            .toList();
    return new OrderPlacedPayload(
        order.getId(), order.getBuyerId(), items, order.getTotalAmount(), Instant.now());
  }
}
