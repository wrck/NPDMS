package com.dp.deviceops.adapter.web.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

final class LocalDebugAuthenticationFilter extends OncePerRequestFilter {
    private static final String SCOPES = "device-ops:collections:read device-ops:collections:execute "
            + "device-ops:devices:read device-ops:projects:read parser:release:read parser:release:write "
            + "parser:task:create parser:task:read parser:task:cancel parser:task:terminate";
    private static final List<SimpleGrantedAuthority> AUTHORITIES = List.of(
            new SimpleGrantedAuthority("SCOPE_device-ops:collections:read"),
            new SimpleGrantedAuthority("SCOPE_device-ops:collections:execute"),
            new SimpleGrantedAuthority("SCOPE_device-ops:devices:read"),
            new SimpleGrantedAuthority("SCOPE_device-ops:projects:read"),
            new SimpleGrantedAuthority("SCOPE_parser:release:read"),
            new SimpleGrantedAuthority("SCOPE_parser:release:write"),
            new SimpleGrantedAuthority("SCOPE_parser:task:create"),
            new SimpleGrantedAuthority("SCOPE_parser:task:read"),
            new SimpleGrantedAuthority("SCOPE_parser:task:cancel"),
            new SimpleGrantedAuthority("SCOPE_parser:task:terminate"));

    private final String projectClaim;

    LocalDebugAuthenticationFilter(String projectClaim) {
        this.projectClaim = projectClaim;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            Instant now = Instant.now();
            Jwt jwt = new Jwt(
                    "local-debug",
                    now,
                    now.plusSeconds(3600),
                    Map.of("alg", "none"),
                    Map.of("sub", "local-debug-user", "scope", SCOPES, projectClaim, List.of("*"),
                            "device_ops_namespaces", List.of("*")));
            SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, AUTHORITIES));
        }
        chain.doFilter(request, response);
    }
}
