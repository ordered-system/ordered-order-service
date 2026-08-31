package pl.dybcio.ordered.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  private static final String BEARER_SCHEME_NAME = "bearerAuth";

  @Bean
  public OpenAPI orderServiceOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("ordered-system - Order Service")
                .description(
                    "Orders, checkout orchestration, and Stripe payments. Part of the"
                        + " ordered-system microservices project - see the other services in the"
                        + " ordered-system GitHub org.")
                .version("v1"))
        .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME_NAME))
        .components(
            new Components()
                .addSecuritySchemes(
                    BEARER_SCHEME_NAME,
                    new SecurityScheme()
                        .name(BEARER_SCHEME_NAME)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")));
  }
}
