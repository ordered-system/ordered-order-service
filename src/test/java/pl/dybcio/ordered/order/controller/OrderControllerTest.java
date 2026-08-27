package pl.dybcio.ordered.order.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import pl.dybcio.ordered.common.exception.GlobalExceptionHandler;
import pl.dybcio.ordered.commons.exception.CommonExceptionHandler;
import pl.dybcio.ordered.commons.security.AuthenticatedUser;
import pl.dybcio.ordered.order.dto.OrderResponse;
import pl.dybcio.ordered.order.entity.Order;
import pl.dybcio.ordered.order.entity.OrderStatus;
import pl.dybcio.ordered.order.service.InvalidOrderStatusTransitionException;
import pl.dybcio.ordered.order.service.OrderNotFoundException;
import pl.dybcio.ordered.order.service.OrderPlacementOrchestrator;
import pl.dybcio.ordered.order.service.OrderService;
import pl.dybcio.ordered.order.service.OrderStatusChangeNotAllowedException;

@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

  @Mock private OrderPlacementOrchestrator orchestrator;
  @Mock private OrderService orderService;

  private MockMvc mockMvc;

  private final AuthenticatedUser buyer =
      new AuthenticatedUser(42L, "adam@example.com", List.of("ROLE_USER"));
  private final AuthenticatedUser admin =
      new AuthenticatedUser(99L, "admin@example.com", List.of("ROLE_ADMIN"));

  @BeforeEach
  void setUp() {
    OrderController controller = new OrderController(orchestrator, orderService);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler(), new CommonExceptionHandler())
            .setCustomArgumentResolvers(
                new AuthenticationPrincipalArgumentResolver(),
                new PageableHandlerMethodArgumentResolver())
            .build();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  private void authenticateAs(AuthenticatedUser user) {
    var authorities = user.roles().stream().map(SimpleGrantedAuthority::new).toList();
    var token = new UsernamePasswordAuthenticationToken(user, null, authorities);
    SecurityContextHolder.getContext().setAuthentication(token);
  }

  private Order sampleOrder(Long id, Long buyerId, OrderStatus status) {
    return Order.builder()
        .id(id)
        .buyerId(buyerId)
        .status(status)
        .totalAmount(BigDecimal.valueOf(199.99))
        .items(List.of())
        .createdAt(Instant.parse("2026-08-19T10:00:00Z"))
        .build();
  }

  private static final String VALID_ADDRESS_JSON =
      """
            {
              "deliveryAddress": {
                "recipientName": "Adam D",
                "phone": "+48123456789",
                "street": "Testowa",
                "buildingNumber": "1",
                "city": "Torun",
                "postalCode": "87-100",
                "country": "PL"
              }
            }
            """;

  @Test
  void placeOrder_returns201WithOrderBody_onSuccess() throws Exception {
    authenticateAs(buyer);
    Order confirmed = sampleOrder(1L, 42L, OrderStatus.CONFIRMED);
    when(orchestrator.placeOrderWithPayment(eq(42L), any())).thenReturn(confirmed);

    mockMvc
        .perform(
            post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_ADDRESS_JSON))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(1))
        .andExpect(jsonPath("$.status").value("CONFIRMED"));
  }

  @Test
  void placeOrder_returns400_whenAddressIsIncomplete() throws Exception {
    authenticateAs(buyer);
    String incomplete =
        """
                { "deliveryAddress": { "recipientName": "Adam D" } }
                """;

    mockMvc
        .perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(incomplete))
        .andExpect(status().isBadRequest());
  }

  @Test
  void getOrder_returns200_whenServiceReturnsOrder() throws Exception {
    authenticateAs(buyer);
    when(orderService.getOrderForUser(1L, 42L, false))
        .thenReturn(sampleOrder(1L, 42L, OrderStatus.PENDING));

    mockMvc
        .perform(get("/api/v1/orders/1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(1))
        .andExpect(jsonPath("$.buyerId").value(42));
  }

  @Test
  void getOrder_returns404_whenOrderNotFoundOrNotOwned() throws Exception {
    authenticateAs(buyer);
    when(orderService.getOrderForUser(1L, 42L, false)).thenThrow(new OrderNotFoundException(1L));

    mockMvc.perform(get("/api/v1/orders/1")).andExpect(status().isNotFound());
  }

  @Test
  void getOrder_passesIsAdminFlagThrough_forAdminUsers() throws Exception {
    authenticateAs(admin);
    when(orderService.getOrderForUser(1L, 99L, true))
        .thenReturn(sampleOrder(1L, 42L, OrderStatus.PENDING));

    mockMvc.perform(get("/api/v1/orders/1")).andExpect(status().isOk());
  }

  @Test
  void listMyOrders_returnsPagedOrdersForRequestingUser() throws Exception {
    authenticateAs(buyer);
    when(orderService.listOrdersForUser(eq(42L), any(Pageable.class)))
        .thenReturn(
            new PageImpl<>(
                List.of(OrderResponse.from(sampleOrder(1L, 42L, OrderStatus.CONFIRMED)))));

    mockMvc
        .perform(get("/api/v1/orders"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].id").value(1))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.last").value(true));
  }

  @Test
  void updateStatus_returns200WithUpdatedOrder_onValidTransition() throws Exception {
    authenticateAs(buyer);
    when(orderService.updateStatus(1L, 42L, false, OrderStatus.CANCELLED))
        .thenReturn(sampleOrder(1L, 42L, OrderStatus.CANCELLED));

    mockMvc
        .perform(
            patch("/api/v1/orders/1/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"CANCELLED\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"));
  }

  @Test
  void updateStatus_returns409_onInvalidTransition() throws Exception {
    authenticateAs(admin);
    when(orderService.updateStatus(1L, 99L, true, OrderStatus.PENDING))
        .thenThrow(
            new InvalidOrderStatusTransitionException(OrderStatus.DELIVERED, OrderStatus.PENDING));

    mockMvc
        .perform(
            patch("/api/v1/orders/1/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"PENDING\"}"))
        .andExpect(status().isConflict());
  }

  @Test
  void updateStatus_returns403_whenChangeNotAllowedForRequestingUser() throws Exception {
    authenticateAs(buyer);
    when(orderService.updateStatus(1L, 42L, false, OrderStatus.SHIPPED))
        .thenThrow(new OrderStatusChangeNotAllowedException("nope"));

    mockMvc
        .perform(
            patch("/api/v1/orders/1/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"SHIPPED\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void updateStatus_returns400_whenStatusMissing() throws Exception {
    authenticateAs(buyer);

    mockMvc
        .perform(
            patch("/api/v1/orders/1/status").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest());
  }
}
