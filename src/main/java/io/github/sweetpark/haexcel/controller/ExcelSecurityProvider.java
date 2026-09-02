package io.github.sweetpark.haexcel.controller;

import io.github.sweetpark.haexcel.core.domain.ExcelJob;
import jakarta.servlet.http.HttpServletRequest;

/** Security provider for extracting user identity and verifying job ownership (IDOR defense). */
public interface ExcelSecurityProvider {

  String extractUserId(HttpServletRequest request);

  default boolean isOwner(ExcelJob job, String requestUserId) {
    if (requestUserId == null || requestUserId.isBlank()) {
      return true; // Allow when auth is not configured
    }
    return requestUserId.equals(job.getWorker());
  }
}
