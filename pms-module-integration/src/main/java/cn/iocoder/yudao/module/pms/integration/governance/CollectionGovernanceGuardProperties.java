package cn.iocoder.yudao.module.pms.integration.governance;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "pms.integration.collection-guard")
@Data
public class CollectionGovernanceGuardProperties {

    private String baseUrl;
}
