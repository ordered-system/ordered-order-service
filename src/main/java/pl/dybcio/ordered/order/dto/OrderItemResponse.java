package pl.dybcio.ordered.order.dto;

import java.math.BigDecimal;
import pl.dybcio.ordered.order.entity.OrderItem;

public record OrderItemResponse(
    Long productId, String productName, int quantity, BigDecimal unitPrice, BigDecimal subtotal) {

  public static OrderItemResponse from(OrderItem item) {
    return new OrderItemResponse(
        item.getProductId(),
        item.getProductName(),
        item.getQuantity(),
        item.getUnitPrice(),
        item.getSubtotal());
  }
}
