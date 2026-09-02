package io.github.sweetpark.haexcel.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;

public class DefaultExcelSecurityProvider implements ExcelSecurityProvider {

  @Override
  public String extractUserId(HttpServletRequest request) {
    if (request == null) {
      return "anonymous";
    }
    // 1. Check custom header
    String headerUser = request.getHeader("X-User-Id");
    if (headerUser != null && !headerUser.isBlank()) {
      return headerUser;
    }
    // 2. Check Principal
    Principal principal = request.getUserPrincipal();
    if (principal != null && principal.getName() != null && !principal.getName().isBlank()) {
      return principal.getName();
    }
    // 3. Fallback
    return "anonymous";
  }
}
