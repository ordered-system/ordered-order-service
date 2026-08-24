package pl.dybcio.ordered.order.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.dybcio.ordered.order.dto.PurchaseCheckResponse;
import pl.dybcio.ordered.order.repository.OrderItemRepository;

@RestController
@RequestMapping("/internal/v1/orders")
@RequiredArgsConstructor
public class InternalOrderController {

  private final OrderItemRepository orderItemRepository;

  @GetMapping("/purchases/{buyerId}/{productId}")
  public PurchaseCheckResponse hasPurchased(
      @PathVariable Long buyerId, @PathVariable Long productId) {
    boolean purchased = orderItemRepository.existsPurchaseByBuyerAndProduct(buyerId, productId);
    return new PurchaseCheckResponse(purchased);
  }
}
