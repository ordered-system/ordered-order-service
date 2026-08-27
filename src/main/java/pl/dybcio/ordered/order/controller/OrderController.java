package pl.dybcio.ordered.order.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import pl.dybcio.ordered.commons.dto.PageResponse;
import pl.dybcio.ordered.commons.security.AuthenticatedUser;
import pl.dybcio.ordered.order.dto.OrderResponse;
import pl.dybcio.ordered.order.dto.PlaceOrderRequest;
import pl.dybcio.ordered.order.dto.UpdateOrderStatusRequest;
import pl.dybcio.ordered.order.entity.Order;
import pl.dybcio.ordered.order.service.OrderPlacementOrchestrator;
import pl.dybcio.ordered.order.service.OrderService;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

  private final OrderPlacementOrchestrator orchestrator;
  private final OrderService orderService;

  @PostMapping
  public ResponseEntity<OrderResponse> placeOrder(
      @AuthenticationPrincipal AuthenticatedUser user,
      @Valid @RequestBody PlaceOrderRequest request) {
    Order order = orchestrator.placeOrderWithPayment(user.userId(), request.deliveryAddress());
    return ResponseEntity.status(HttpStatus.CREATED).body(OrderResponse.from(order));
  }

  @GetMapping("/{orderId}")
  public OrderResponse getOrder(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long orderId) {
    Order order = orderService.getOrderForUser(orderId, user.userId(), user.isAdmin());
    return OrderResponse.from(order);
  }

  @GetMapping
  public PageResponse<OrderResponse> listMyOrders(
      @AuthenticationPrincipal AuthenticatedUser user, Pageable pageable) {
    return PageResponse.from(orderService.listOrdersForUser(user.userId(), pageable));
  }

  @PatchMapping("/{orderId}/status")
  @PreAuthorize("isAuthenticated()")
  public OrderResponse updateStatus(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable Long orderId,
      @Valid @RequestBody UpdateOrderStatusRequest request) {
    Order order =
        orderService.updateStatus(orderId, user.userId(), user.isAdmin(), request.status());
    return OrderResponse.from(order);
  }
}
