package pl.dybcio.ordered.order.client;

import java.math.BigDecimal;
import java.util.List;

public record CheckoutReservationResponse(
    String reservationId, List<ReservedLine> lines, BigDecimal totalAmount) {

  public record ReservedLine(
      Long productId,
      String productName,
      int quantity,
      BigDecimal unitPrice,
      BigDecimal subtotal) {}
}
