package com.dp.plat.deviceops.server.desktop;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.time.Instant;
import java.util.List;

final class DesktopTicketFilter extends OncePerRequestFilter {
    static final String GRANT = DesktopTicketFilter.class.getName() + ".grant";
    private final DesktopServerClient server;
    private final DesktopProperties properties;
    DesktopTicketFilter(DesktopServerClient server, DesktopProperties properties) { this.server = server; this.properties = properties; }

    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return "OPTIONS".equals(request.getMethod()) || "/api/v1/desktop/status".equals(request.getRequestURI());
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-store");
        if (!properties.webOrigin().equals(request.getHeader("Origin"))) { response.sendError(403); return; }
        DesktopServerClient.Grant grant;
        try {
            String ticket = request.getHeader("X-NPDMS-DAC-Ticket");
            if (ticket == null || ticket.length() > 4096) { response.sendError(401); return; }
            grant = server.resolve(new DesktopServerClient.Ticket(ticket, Long.parseLong(request.getHeader("tenant-id"))));
        } catch (org.springframework.web.client.RestClientResponseException rejected) {
            response.sendError(rejected.getStatusCode().is4xxClientError() ? 401 : 503); return;
        } catch (NumberFormatException invalid) { response.sendError(401); return; }
        catch (RuntimeException unavailable) { response.sendError(503); return; }
        var jwt = Jwt.withTokenValue("npdms-desktop-operation").header("typ", "internal")
                .subject(Long.toString(grant.actorId())).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60))
                .claim("device_ops_namespaces", List.of(grant.namespace())).claim("device_ops_projects", List.of(grant.projectId())).build();
        String scope = switch (grant.operation()) {
            case "EXECUTE", "CONNECTION_SAVE" -> "SCOPE_device-ops:collections:execute";
            default -> "SCOPE_device-ops:collections:read";
        };
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority(scope))));
        SecurityContextHolder.setContext(context);
        request.setAttribute(GRANT, grant);
        try { chain.doFilter(request, response); }
        finally { request.removeAttribute(GRANT); SecurityContextHolder.clearContext(); }
    }
}
