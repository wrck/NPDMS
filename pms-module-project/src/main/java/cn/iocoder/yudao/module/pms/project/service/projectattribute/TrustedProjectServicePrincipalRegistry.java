package cn.iocoder.yudao.module.pms.project.service.projectattribute;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/** 将受信任机器身份映射为稳定、可审计的服务主体ID；默认不信任任何身份。 */
@Component
@ConfigurationProperties(prefix = "pms.project-attribute")
@Data
public class TrustedProjectServicePrincipalRegistry {

    private Map<String, Long> trustedServicePrincipals = new HashMap<>();

    public Long resolve(String serviceIdentity) {
        if (serviceIdentity == null || serviceIdentity.isBlank()) {
            throw new IllegalArgumentException("服务身份不能为空");
        }
        Long principalId = trustedServicePrincipals.get(serviceIdentity.trim());
        if (principalId == null || principalId <= 0) {
            throw new IllegalArgumentException("服务身份未注册或未受信任");
        }
        return principalId;
    }
}
