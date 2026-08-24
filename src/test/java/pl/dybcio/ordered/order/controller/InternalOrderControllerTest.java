package pl.dybcio.ordered.order.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import pl.dybcio.ordered.order.repository.OrderItemRepository;

@ExtendWith(MockitoExtension.class)
class InternalOrderControllerTest {

  @Mock private OrderItemRepository orderItemRepository;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    InternalOrderController controller = new InternalOrderController(orderItemRepository);
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
  }

  @Test
  void hasPurchased_returnsTrue_whenRepositoryConfirmsPurchase() throws Exception {
    when(orderItemRepository.existsPurchaseByBuyerAndProduct(42L, 10L)).thenReturn(true);

    mockMvc
        .perform(get("/internal/v1/orders/purchases/42/10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.purchased").value(true));
  }

  @Test
  void hasPurchased_returnsFalse_whenRepositoryDeniesPurchase() throws Exception {
    when(orderItemRepository.existsPurchaseByBuyerAndProduct(42L, 10L)).thenReturn(false);

    mockMvc
        .perform(get("/internal/v1/orders/purchases/42/10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.purchased").value(false));
  }
}
