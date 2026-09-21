package com.dp.deviceops.server;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("device-ops.runtime")
public class RuntimeConfigProperties {
    private String authMode = "oauth2";
    private String oidcAuthority;
    private String clientId;
    private String scope = "openid profile";
    private String apiBaseUrl = "/api/v1";
    private String projectClaim;
    private boolean telnetEnabled;
    public String getAuthMode() { return authMode; }
    public void setAuthMode(String value) {
        if (!"oauth2".equals(value) && !"local".equals(value)) {
            throw new IllegalArgumentException("device-ops.runtime.auth-mode must be oauth2 or local");
        }
        authMode = value;
    }
    public String getOidcAuthority() { return oidcAuthority; } public void setOidcAuthority(String value) { oidcAuthority = value; }
    public String getClientId() { return clientId; } public void setClientId(String value) { clientId = value; }
    public String getScope() { return scope; } public void setScope(String value) { scope = value; }
    public String getApiBaseUrl() { return apiBaseUrl; } public void setApiBaseUrl(String value) { apiBaseUrl = value; }
    public String getProjectClaim() { return projectClaim; } public void setProjectClaim(String value) { projectClaim = value; }
    public boolean isTelnetEnabled() { return telnetEnabled; }
    public void setTelnetEnabled(boolean value) { telnetEnabled = value; }
}
