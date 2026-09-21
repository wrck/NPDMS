package com.dp.deviceops.adapter.web.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.SecurityFilterChain;
import com.dp.deviceops.adapter.web.CollectionCredentialCleanupFilter;
import org.springframework.http.HttpMethod;
import jakarta.servlet.DispatcherType;

@Configuration
@EnableMethodSecurity
public class DeviceOpsSecurityConfiguration {
    @Bean SecurityFilterChain deviceOpsApiSecurity(
            HttpSecurity http,
            EmbedSecurityProperties embed,
            @Value("${device-ops.security.mode:oauth2}") String securityMode,
            @Value("${device-ops.security.project-claim:device_ops_projects}") String projectClaim) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable).addFilterBefore(new CollectionCredentialCleanupFilter(), SecurityContextHolderFilter.class).sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers.frameOptions(frame -> frame.disable())
                        .contentSecurityPolicy(csp -> csp.policyDirectives(embed.contentSecurityPolicy())))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                        .requestMatchers("/actuator/health/liveness", "/actuator/health/readiness", "/api/v1/runtime-config",
                                "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/actuator/prometheus").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/parser-log-types",
                                "/api/v1/parser-log-types/*/releases",
                                "/api/v1/parser-releases/*/validations",
                                "/api/v1/parser-releases/*/publications").hasAuthority("SCOPE_parser:release:write")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/parser-log-types/*/active-release")
                                .hasAuthority("SCOPE_parser:release:write")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/parser-log-types/*/active-release")
                                .hasAuthority("SCOPE_parser:release:write")
                        .requestMatchers(HttpMethod.GET, "/api/v1/parser-log-types/**", "/api/v1/parser-releases/*")
                                .hasAuthority("SCOPE_parser:release:read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/parse-tasks/*/cancellations")
                                .hasAuthority("SCOPE_parser:task:cancel")
                        .requestMatchers(HttpMethod.POST, "/api/v1/parse-tasks/*/terminations")
                                .hasAuthority("SCOPE_parser:task:terminate")
                        .requestMatchers(HttpMethod.POST, "/api/v1/parse-tasks")
                                .hasAuthority("SCOPE_parser:task:create")
                        .requestMatchers(HttpMethod.GET, "/api/v1/parse-tasks", "/api/v1/parse-tasks/*",
                                "/api/v1/parse-tasks/*/result", "/api/v1/parse-results/*").hasAuthority("SCOPE_parser:task:read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/connections/test", "/api/v1/collections").hasAuthority("SCOPE_device-ops:collections:execute")
                        .requestMatchers(HttpMethod.GET, "/api/v1/collections/*", "/api/v1/collections/*/output-events",
                                "/api/v1/collections/*/semantic-results", "/api/v1/collections/*/evidence").hasAuthority("SCOPE_device-ops:collections:read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/projects/*/collections").hasAuthority("SCOPE_device-ops:collections:execute")
                        .requestMatchers(HttpMethod.GET, "/api/v1/projects/*/collections/*",
                                "/api/v1/projects/*/collections/*/output-events",
                                "/api/v1/projects/*/collections/*/evidence").hasAuthority("SCOPE_device-ops:collections:read")
                        .requestMatchers(HttpMethod.GET, "/api/v1/master-data/projects/*/devices").hasAuthority("SCOPE_device-ops:devices:read")
                        .requestMatchers(HttpMethod.GET, "/api/v1/master-data/projects").hasAuthority("SCOPE_device-ops:projects:read")
                        .requestMatchers("/api/v1/**").authenticated().anyRequest().permitAll());
        if ("local".equalsIgnoreCase(securityMode)) {
            http.addFilterAfter(new LocalDebugAuthenticationFilter(projectClaim), SecurityContextHolderFilter.class);
        } else {
            http.oauth2ResourceServer(oauth -> oauth.jwt(jwt -> { }));
        }
        return http.build();
    }
}
