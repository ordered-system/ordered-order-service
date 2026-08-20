package pl.dybcio.ordered.order.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import pl.dybcio.ordered.order.service.CheckoutReservationException;

class ProductServiceClientTest {

  private MockRestServiceServer server;
  private ProductServiceClient client;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder();
    server = MockRestServiceServer.bindTo(builder).build();
    MockEnvironment env =
        new MockEnvironment()
            .withProperty("app.product-service.base-url", "http://product-service");
    client = new ProductServiceClient(builder, env);
  }

  @Test
  void reserveCartForCheckout_returnsParsedReservation_onSuccess() {
    server
        .expect(requestTo("http://product-service/internal/v1/checkout/reserve"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(jsonPath("$.buyerId").value(42))
        .andRespond(
            withSuccess(
                """
                                {
                                  "reservationId": "res-1",
                                  "lines": [
                                    {"productId": 1, "productName": "Keyboard", "quantity": 2, "unitPrice": 50.00, "subtotal": 100.00}
                                  ],
                                  "totalAmount": 100.00
                                }
                                """,
                MediaType.APPLICATION_JSON));

    CheckoutReservationResponse response = client.reserveCartForCheckout(42L);

    assertThat(response.reservationId()).isEqualTo("res-1");
    assertThat(response.lines()).hasSize(1);
    assertThat(response.lines().get(0).productName()).isEqualTo("Keyboard");
    assertThat(response.totalAmount()).isEqualByComparingTo("100.00");
    server.verify();
  }

  @Test
  void reserveCartForCheckout_throwsCheckoutReservationException_on4xxResponse() {
    server
        .expect(requestTo("http://product-service/internal/v1/checkout/reserve"))
        .andRespond(withStatus(HttpStatus.CONFLICT).body("cart is empty"));

    assertThatThrownBy(() -> client.reserveCartForCheckout(42L))
        .isInstanceOf(CheckoutReservationException.class)
        .hasMessageContaining("cart is empty");

    server.verify();
  }

  @Test
  void releaseReservation_postsToReleaseEndpointWithReservationIdInPath() {
    server
        .expect(requestTo("http://product-service/internal/v1/checkout/res-1/release"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess());

    client.releaseReservation("res-1");

    server.verify();
  }
}
