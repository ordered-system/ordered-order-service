package pl.dybcio.ordered.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class JwtClaimsAuthenticationFilterTest {

  private static final String SECRET = "test-secret-key-that-is-long-enough-for-hs256-signing";

  private JwtClaimsAuthenticationFilter filter;
  private SecretKey signingKey;

  @BeforeEach
  void setUp() {
    filter = new JwtClaimsAuthenticationFilter(SECRET);
    signingKey = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    SecurityContextHolder.clearContext();
  }

  @Test
  void buildsAuthenticatedUserFromClaims_noDbInvolved() throws Exception {
    String token =
        Jwts.builder()
            .subject("adam@example.com")
            .claim("userId", 42L)
            .claim("roles", List.of("ROLE_USER"))
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + 60_000))
            .signWith(signingKey)
            .compact();

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer " + token);
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(request, response, chain);

    var auth = SecurityContextHolder.getContext().getAuthentication();
    assertThat(auth).isNotNull();
    AuthenticatedUser principal = (AuthenticatedUser) auth.getPrincipal();
    assertThat(principal.userId()).isEqualTo(42L);
    assertThat(principal.email()).isEqualTo("adam@example.com");
    assertThat(principal.roles()).containsExactly("ROLE_USER");
    verify(chain).doFilter(request, response);
  }

  @Test
  void leavesSecurityContextEmpty_onTamperedToken() throws Exception {
    String validToken =
        Jwts.builder()
            .subject("adam@example.com")
            .claim("userId", 42L)
            .claim("roles", List.of("ROLE_USER"))
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + 60_000))
            .signWith(signingKey)
            .compact();
    String tampered = validToken.substring(0, validToken.length() - 2) + "xx";

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer " + tampered);
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(request, response, chain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    verify(chain).doFilter(request, response);
  }

  @Test
  void skipsAuthentication_whenNoAuthorizationHeader() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(request, response, chain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    verify(chain).doFilter(request, response);
  }
}
