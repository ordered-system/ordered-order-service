package pl.dybcio.ordered.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import pl.dybcio.ordered.commons.security.JwtClaimsAuthenticationFilter;
import pl.dybcio.ordered.commons.security.ProblemDetailAuthenticationEntryPoint;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  private final JwtClaimsAuthenticationFilter jwtClaimsAuthenticationFilter;
  private final ProblemDetailAuthenticationEntryPoint problemDetailAuthenticationEntryPoint;

  private static final String[] PUBLIC_ENDPOINTS = {
    "/actuator/health", "/actuator/prometheus", "/error"
  };

  private static final String[] INTERNAL_ENDPOINTS = {"/internal/v1/**"};

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(PUBLIC_ENDPOINTS)
                    .permitAll()
                    .requestMatchers(INTERNAL_ENDPOINTS)
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(jwtClaimsAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
        .exceptionHandling(
            exception -> exception.authenticationEntryPoint(problemDetailAuthenticationEntryPoint));

    return http.build();
  }
}
