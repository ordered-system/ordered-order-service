package pl.dybcio.ordered.order.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import pl.dybcio.ordered.order.entity.Order;
import pl.dybcio.ordered.order.entity.OrderStatus;

public record OrderResponse(
    Long id,
    Long buyerId,
    OrderStatus status,
    BigDecimal totalAmount,
    List<OrderItemResponse> items,
    Instant createdAt) {

  public static OrderResponse from(Order order) {
    return new OrderResponse(
        order.getId(),
        order.getBuyerId(),
        order.getStatus(),
        order.getTotalAmount(),
        order.getItems().stream().map(OrderItemResponse::from).toList(),
        order.getCreatedAt());
  }
}
