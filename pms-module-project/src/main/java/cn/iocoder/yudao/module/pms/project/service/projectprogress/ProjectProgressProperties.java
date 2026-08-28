package cn.iocoder.yudao.module.pms.project.service.projectprogress;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "pms.project.progress-policy")
@Data
public class ProjectProgressProperties {
    private String processDefinitionKey;
}
