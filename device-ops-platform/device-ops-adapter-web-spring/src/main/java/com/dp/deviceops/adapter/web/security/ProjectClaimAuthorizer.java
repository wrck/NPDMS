package com.dp.deviceops.adapter.web.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Collection;

@Component
@ConfigurationProperties("device-ops.security")
public class ProjectClaimAuthorizer {
    private String projectClaim = "device_ops_projects";
    public void setProjectClaim(String projectClaim) { this.projectClaim = projectClaim; }
    private String namespaceClaim = "device_ops_namespaces";
    private String clientNamespaceClaim = "client_namespace";
    public void setNamespaceClaim(String value) { this.namespaceClaim = value; }
    public void setClientNamespaceClaim(String value) { this.clientNamespaceClaim = value; }

    public String requireSubject(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            throw new AccessDeniedException("caller subject is required");
        }
        return jwt.getSubject();
    }

    public void requireNamespace(Jwt jwt, String namespace) {
        String subject = requireSubject(jwt);
        if (namespace == null || namespace.isBlank() || namespace.length() > 100) {
            throw new AccessDeniedException("valid namespace is required");
        }
        Object namespaces = jwt.getClaim(namespaceClaim);
        boolean allowed;
        if (namespaces != null) {
            allowed = namespaces instanceof Collection<?> values && values.stream()
                    .anyMatch(value -> namespace.equals(value) || "*".equals(value));
        } else {
            Object clientNamespace = jwt.getClaim(clientNamespaceClaim);
            allowed = clientNamespace == null ? namespace.equals(subject) : namespace.equals(clientNamespace);
        }
        if (!allowed) {
            throw new AccessDeniedException("namespace access is not granted");
        }
    }

    /** Share precisely the same configured claim formats with SQL management projections. */
    public com.dp.deviceops.core.port.ManagementQueryPort.Scope visibleScope(Jwt jwt) {
        String subject = requireSubject(jwt);
        Object rawNamespaces = jwt.getClaim(namespaceClaim);
        java.util.List<String> namespaces;
        if (rawNamespaces != null) {
            namespaces = rawNamespaces instanceof Collection<?> values
                    ? values.stream().filter(String.class::isInstance).map(String.class::cast).distinct().toList()
                    : java.util.List.of();
        } else {
            Object clientNamespace = jwt.getClaim(clientNamespaceClaim);
            namespaces = clientNamespace == null ? java.util.List.of(subject)
                    : clientNamespace instanceof String value ? java.util.List.of(value) : java.util.List.of();
        }
        Object rawProjects = jwt.getClaim(projectClaim);
        java.util.List<String> projects = rawProjects instanceof Collection<?> values
                ? values.stream().map(String::valueOf).distinct().toList() : java.util.List.of();
        return new com.dp.deviceops.core.port.ManagementQueryPort.Scope(namespaces, projects,
                rawNamespaces instanceof Collection<?> values && values.contains("*"));
    }

    public void require(Jwt jwt, String projectKey) {
        requireSubject(jwt);
        Object raw = jwt.getClaim(projectClaim);
        boolean allowed = raw instanceof Collection<?> values && values.stream().map(String::valueOf).anyMatch(value -> value.equals("*") || value.equals(projectKey));
        if (!allowed) throw new AccessDeniedException("project access is not granted");
    }
}
