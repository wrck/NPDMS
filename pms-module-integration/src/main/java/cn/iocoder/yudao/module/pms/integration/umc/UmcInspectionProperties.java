package cn.iocoder.yudao.module.pms.integration.umc;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Bind secrets from the process environment/secret provider; never serialize this object. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "pms.integration.umc")
public class UmcInspectionProperties {
    private String provider = "java";
    private String baseUrl;
    private String username;
    private String password;
    private String nodeExecutable = "node";
    private String certificateSha256;
    private int timeoutSeconds = 180;

    public void setProvider(String provider) {
        if (!"java".equals(provider) && !"node".equals(provider)) {
            throw new IllegalArgumentException("UMC provider must be java or node");
        }
        this.provider = provider;
    }
}
