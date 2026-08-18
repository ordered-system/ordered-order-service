package pl.dybcio.ordered.security;

import java.util.List;

public record AuthenticatedUser(Long userId, String email, List<String> roles) {

  public boolean isAdmin() {
    return roles.contains("ROLE_ADMIN");
  }
}
