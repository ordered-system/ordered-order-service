package pl.dybcio.ordered.order.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import pl.dybcio.ordered.order.service.CheckoutReservationException;

@Component
@Slf4j
public class ProductServiceClient {

  private final RestClient restClient;

  public ProductServiceClient(RestClient.Builder restClientBuilder, Environment env) {
    this.restClient =
        restClientBuilder
            .baseUrl(Objects.requireNonNull(env.getProperty("server.product-service.base-url")))
            .build();
  }

  @CircuitBreaker(name = "productService")
  @Retry(name = "productService", fallbackMethod = "reserveFallback")
  public CheckoutReservationResponse reserveCartForCheckout(Long buyerId) {
    try {
      return restClient
          .post()
          .uri("/internal/v1/checkout/reserve")
          .body(new CheckoutReservationRequest(buyerId))
          .retrieve()
          .body(CheckoutReservationResponse.class);
    } catch (RestClientResponseException e) {
      if (e.getStatusCode().is4xxClientError()) {
        throw new CheckoutReservationException(buyerId, e.getResponseBodyAsString());
      }
      throw e;
    }
  }

  @SuppressWarnings("unused")
  private CheckoutReservationResponse reserveFallback(Long buyerId, Throwable t) {
    log.error(
        "product-service unreachable while reserving cart for buyer {}: {}",
        buyerId,
        t.getMessage());
    throw new CheckoutReservationException(
        buyerId, "product-service is currently unavailable, please try again shortly");
  }

  @CircuitBreaker(name = "productService")
  @Retry(name = "productService")
  public void releaseReservation(String reservationId) {
    restClient
        .post()
        .uri("/internal/v1/checkout/{reservationId}/release", reservationId)
        .retrieve()
        .toBodilessEntity();
  }
}
