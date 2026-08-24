package pl.dybcio.ordered.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pl.dybcio.ordered.order.entity.Order;
import pl.dybcio.ordered.order.entity.OrderItem;
import pl.dybcio.ordered.order.entity.OrderStatus;
import pl.dybcio.ordered.order.repository.OrderItemRepository;
import pl.dybcio.ordered.order.repository.OrderRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class OrderItemRepositoryIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void extraProperties(DynamicPropertyRegistry registry) {
    registry.add("eureka.client.enabled", () -> "false");
    registry.add("app.jwt.secret", () -> "test-only-signing-secret-not-used-for-any-real-auth");
  }

  @Autowired private OrderRepository orderRepository;
  @Autowired private OrderItemRepository orderItemRepository;

  private Order persistOrder(Long buyerId, OrderStatus status, Long productId) {
    Order order =
        Order.builder().buyerId(buyerId).status(status).totalAmount(BigDecimal.TEN).build();
    order.addItem(
        OrderItem.builder()
            .productId(productId)
            .productName("Test product")
            .quantity(1)
            .unitPrice(BigDecimal.TEN)
            .subtotal(BigDecimal.TEN)
            .build());
    return orderRepository.save(order);
  }

  @Test
  void existsPurchase_returnsTrue_whenOrderIsDeliveredAndMatchesBuyerAndProduct() {
    persistOrder(42L, OrderStatus.DELIVERED, 10L);

    boolean result = orderItemRepository.existsPurchaseByBuyerAndProduct(42L, 10L);

    assertThat(result).isTrue();
  }

  @Test
  void existsPurchase_returnsFalse_whenOrderExistsButIsNotDeliveredYet() {
    persistOrder(42L, OrderStatus.SHIPPED, 10L);

    boolean result = orderItemRepository.existsPurchaseByBuyerAndProduct(42L, 10L);

    assertThat(result).isFalse();
  }

  @Test
  void existsPurchase_returnsFalse_whenProductDoesNotMatch() {
    persistOrder(42L, OrderStatus.DELIVERED, 10L);

    boolean result = orderItemRepository.existsPurchaseByBuyerAndProduct(42L, 999L);

    assertThat(result).isFalse();
  }

  @Test
  void existsPurchase_returnsFalse_whenBuyerDoesNotMatch() {
    persistOrder(42L, OrderStatus.DELIVERED, 10L);

    boolean result = orderItemRepository.existsPurchaseByBuyerAndProduct(999L, 10L);

    assertThat(result).isFalse();
  }

  @Test
  void existsPurchase_returnsTrue_whenBuyerHasMultipleOrders_onlyOneDelivered() {
    persistOrder(42L, OrderStatus.CANCELLED, 10L);
    persistOrder(42L, OrderStatus.PENDING, 10L);
    persistOrder(42L, OrderStatus.DELIVERED, 10L);

    boolean result = orderItemRepository.existsPurchaseByBuyerAndProduct(42L, 10L);

    assertThat(result).isTrue();
  }

  @Test
  void existsPurchase_returnsFalse_whenNoOrdersExistAtAll() {
    boolean result = orderItemRepository.existsPurchaseByBuyerAndProduct(1234L, 5678L);

    assertThat(result).isFalse();
  }
}
