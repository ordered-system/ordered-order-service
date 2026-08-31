package pl.dybcio.ordered.order.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Orders", description = "Placing orders and tracking their status")
public class OrderController {

  private final OrderPlacementOrchestrator orchestrator;
  private final OrderService orderService;

  @PostMapping
  @Operation(
      summary = "Place an order",
      description =
          "Reserves stock, persists the order, then charges via Stripe. If payment fails, the"
              + " stock reservation is released asynchronously via the order-cancelled outbox"
              + " event.")
  public ResponseEntity<OrderResponse> placeOrder(
      @AuthenticationPrincipal AuthenticatedUser user,
      @Valid @RequestBody PlaceOrderRequest request) {
    Order order = orchestrator.placeOrderWithPayment(user.userId(), request.deliveryAddress());
    return ResponseEntity.status(HttpStatus.CREATED).body(OrderResponse.from(order));
  }

  @GetMapping("/{orderId}")
  @Operation(
      summary = "Get an order by id",
      description = "Buyers can only fetch their own orders; admins can fetch any order.")
  public OrderResponse getOrder(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long orderId) {
    Order order = orderService.getOrderForUser(orderId, user.userId(), user.isAdmin());
    return OrderResponse.from(order);
  }

  @GetMapping
  @Operation(summary = "List the authenticated user's own orders, paginated")
  public PageResponse<OrderResponse> listMyOrders(
      @AuthenticationPrincipal AuthenticatedUser user, Pageable pageable) {
    return PageResponse.from(orderService.listOrdersForUser(user.userId(), pageable));
  }

  @PatchMapping("/{orderId}/status")
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "Update an order's status",
      description =
          "Transitioning to DELIVERED publishes the order-delivered event that"
              + " engagement-service consumes to unlock reviews for the buyer.")
  public OrderResponse updateStatus(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable Long orderId,
      @Valid @RequestBody UpdateOrderStatusRequest request) {
    Order order =
        orderService.updateStatus(orderId, user.userId(), user.isAdmin(), request.status());
    return OrderResponse.from(order);
  }
}
