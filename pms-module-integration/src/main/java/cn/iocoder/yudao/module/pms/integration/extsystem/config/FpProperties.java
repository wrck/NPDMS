package cn.iocoder.yudao.module.pms.integration.extsystem.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * FP (Financial Platform) integration properties. Bound from the {@code fp.*}
 * prefix in {@code application.yml}.
 */
@Data
@ConfigurationProperties(prefix = "fp")
public class FpProperties {

    /** FP API base url. */
    private String baseUrl;

    /** OAuth2 token endpoint url. */
    private String tokenUrl;

    /** OAuth2 client id. */
    private String clientId;

    /** OAuth2 client secret. */
    private String clientSecret;
}
