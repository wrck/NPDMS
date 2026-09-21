package com.dp.deviceops.adapter.web.masterdata;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.net.URI; import java.time.Duration;

@ConfigurationProperties("device-ops.master-data")
public class MasterDataHttpProperties {
    private boolean enabled; private URI integrationHost; private URI tokenUri; private String clientId; private String clientSecret; private String scope=""; private Duration connectTimeout=Duration.ofSeconds(3); private Duration readTimeout=Duration.ofSeconds(10);
    public boolean isEnabled(){return enabled;} public void setEnabled(boolean value){enabled=value;}
    public URI getIntegrationHost() { return integrationHost; } public void setIntegrationHost(URI value) { integrationHost = value; }
    public URI getTokenUri() { return tokenUri; } public void setTokenUri(URI value) { tokenUri = value; }
    public String getClientId() { return clientId; } public void setClientId(String value) { clientId = value; }
    public String getClientSecret() { return clientSecret; } public void setClientSecret(String value) { clientSecret = value; }
    public String getScope(){return scope;} public void setScope(String value){scope=value;}
    public Duration getConnectTimeout(){return connectTimeout;} public void setConnectTimeout(Duration value){connectTimeout=value;}
    public Duration getReadTimeout(){return readTimeout;} public void setReadTimeout(Duration value){readTimeout=value;}
    public void validate(){ if(enabled && (integrationHost==null||tokenUri==null||clientId==null||clientId.isBlank()||clientSecret==null||clientSecret.isBlank()||!webUri(integrationHost)||!webUri(tokenUri)||connectTimeout==null||connectTimeout.isZero()||connectTimeout.isNegative()||readTimeout==null||readTimeout.isZero()||readTimeout.isNegative())) throw new IllegalStateException("master data configuration is invalid"); }
    private static boolean webUri(URI uri){ return "http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()); }
}
