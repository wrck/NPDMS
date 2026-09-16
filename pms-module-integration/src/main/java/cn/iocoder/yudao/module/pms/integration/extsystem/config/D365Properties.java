package cn.iocoder.yudao.module.pms.integration.extsystem.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * D365 (Microsoft Dynamics 365) integration properties. Bound from the
 * {@code d365.*} prefix in {@code application.yml}.
 */
@Data
@ConfigurationProperties(prefix = "d365")
public class D365Properties {

    /** D365 API base url. */
    private String baseUrl;

    /** OAuth2 token endpoint url. */
    private String tokenUrl;

    /** OAuth2 client id. */
    private String clientId;

    /** OAuth2 client secret. */
    private String clientSecret;

    /** OAuth2 scope (optional). */
    private String scope;

    /** OAuth2 grant type, default {@code client_credentials}. */
    private String grantType = "client_credentials";
}
