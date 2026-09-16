package cn.iocoder.yudao.module.pms.integration.extsystem.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OA (致远 OA) integration properties. Bound from the
 * {@code integration.oa.*} prefix in {@code application.yml}.
 */
@Data
@ConfigurationProperties(prefix = "integration.oa")
public class OaProperties {

    /** OA API base url. */
    private String baseUrl;

    /** OAuth2 token endpoint url. */
    private String tokenUrl;

    /** OAuth2 client id. */
    private String clientId;

    /** OAuth2 client secret. */
    private String clientSecret;
}
