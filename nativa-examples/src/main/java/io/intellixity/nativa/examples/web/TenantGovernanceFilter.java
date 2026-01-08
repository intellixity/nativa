package io.intellixity.nativa.examples.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import io.intellixity.nativa.persistence.governance.Governance;
import io.intellixity.nativa.persistence.governance.GovernanceContext;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Component
public final class TenantGovernanceFilter extends OncePerRequestFilter {
  public static final String TENANT_HEADER = "X-Tenant-Id";
  public static final String USER_HEADER = "X-User-Id";
  public static final String ENTERPRISE_HEADER = "X-Enterprise";
  public static final String DEALER_HEADER = "X-Dealer-Id";

  @Override
  protected void doFilterInternal(HttpServletRequest request,
                                  HttpServletResponse response,
                                  FilterChain filterChain) throws ServletException, IOException {

    String tenantId = request.getHeader(TENANT_HEADER);
    if (tenantId == null || tenantId.isBlank()) {
      response.sendError(400, "Missing required header: " + TENANT_HEADER);
      return;
    }
    tenantId = tenantId.trim();

    String userId = request.getHeader(USER_HEADER);
    if (userId != null) userId = userId.trim();

    boolean enterprise = false;
    String enterpriseRaw = request.getHeader(ENTERPRISE_HEADER);
    if (enterpriseRaw != null && !enterpriseRaw.isBlank()) {
      enterprise = Boolean.parseBoolean(enterpriseRaw.trim());
    }

    String dealerId = request.getHeader(DEALER_HEADER);
    if (dealerId != null) dealerId = dealerId.trim();
    if (!enterprise && (dealerId == null || dealerId.isBlank())) {
      response.sendError(400, "Missing required header for non-enterprise request: " + DEALER_HEADER);
      return;
    }

    Map<String, Object> ctxMap = new HashMap<>();
    ctxMap.put("tenantId", tenantId);
    if (userId != null && !userId.isBlank()) ctxMap.put("userId", userId);
    if (dealerId != null && !dealerId.isBlank()) ctxMap.put("dealerId", dealerId);
    ctxMap.put("enterprise", enterprise);

    final Set<String> tenantKeys = enterprise ? Set.of("tenantId") : Set.of("tenantId", "dealerId");
    final String cacheKey = enterprise ? ("t:" + tenantId) : ("t:" + tenantId + "|d:" + dealerId);

    GovernanceContext ctx = new GovernanceContext() {
      @Override public Object get(String key) { return ctxMap.get(key); }
      @Override public String cacheKey() { return cacheKey; }
      @Override public Set<String> tenantKeys() { return tenantKeys; }
    };

    try {
      Governance.inContext(ctx, () -> {
          try {
              filterChain.doFilter(request, response);
          } catch (Exception e) {
              throw new RuntimeException(e);
          }
          return null;
      });
    } catch (RuntimeException e) {
      Throwable c = e.getCause();
      if (c instanceof IOException ioe) throw ioe;
      if (c instanceof ServletException se) throw se;
      throw e;
    }
  }
}


