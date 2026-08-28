package cn.iocoder.yudao.module.pms.project.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "pms.project.governance.guard")
@Getter
@Setter
public class ProjectGovernanceGuardProperties {

    private String signingKey;
}
