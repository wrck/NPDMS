package com.dp.plat.deviceops.server.desktop;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;

@Profile("desktop")
@Configuration
@org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
public class DesktopDiscoverySecurity {
    @Bean
    @Order(1)
    SecurityFilterChain desktopDiscovery(HttpSecurity http, DesktopProperties properties, DesktopServerClient server) throws Exception {
        var cors = new CorsConfiguration();
        cors.setAllowedOrigins(List.of(properties.webOrigin()));
        cors.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Accept", "Content-Type", "X-NPDMS-DAC-Ticket", "tenant-id"));
        cors.setAllowCredentials(false);
        cors.setAllowPrivateNetwork(true);
        cors.setMaxAge(600L);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/v1/desktop/**", cors);
        return http.securityMatcher("/api/v1/desktop/**")
                .csrf(AbstractHttpConfigurer::disable).cors(config -> config.configurationSource(source))
                .sessionManagement(config -> config.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterAfter(new DesktopTicketFilter(server, properties), org.springframework.security.web.context.SecurityContextHolderFilter.class)
                .addFilterBefore(new com.dp.plat.deviceops.adapter.web.CollectionCredentialCleanupFilter(), org.springframework.security.web.context.SecurityContextHolderFilter.class)
                .authorizeHttpRequests(config -> config.requestMatchers("/api/v1/desktop/status").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/api/v1/desktop/**").permitAll()
                        .anyRequest().authenticated()).build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain desktopOtherRoutes(HttpSecurity http) throws Exception {
        // Generic/OIDC deployments keep their original chain; an installed desktop only accepts its environment-bound adapter.
        return http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(config -> config.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(config -> config.requestMatchers(org.springframework.http.HttpMethod.GET, "/desktop-status").permitAll()
                        .anyRequest().denyAll()).build();
    }

    @Bean
    FilterRegistrationBean<LoopbackHostFilter> desktopLoopbackHostFilter() {
        var registration = new FilterRegistrationBean<>(new LoopbackHostFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    static final class LoopbackHostFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            // Never resolve an arbitrary Host through DNS; this also fences DNS rebinding.
            if (!Set.of("127.0.0.1", "localhost").contains(request.getServerName())
                    || !Set.of("127.0.0.1", "0:0:0:0:0:0:0:1", "::1").contains(request.getRemoteAddr())) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN);
                return;
            }
            chain.doFilter(request, response);
        }
    }
}
