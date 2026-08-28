package cn.iocoder.yudao.module.pms.engineering.service.constructionplan;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "pms.sol.duration-change")
@Data
public class DurationChangeProperties {

    private String processDefinitionKey;

}
