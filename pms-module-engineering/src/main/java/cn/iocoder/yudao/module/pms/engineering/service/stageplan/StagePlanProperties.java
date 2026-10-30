package cn.iocoder.yudao.module.pms.engineering.service.stageplan;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 阶段施工计划审批流程配置（PLN-04）。
 * <p>
 * processDefinitionKey 未配置时提交审批直接失败，不伪造审批完成。
 */
@Component
@ConfigurationProperties(prefix = "pms.sol.stage-plan")
@Data
public class StagePlanProperties {

    private String processDefinitionKey;

}
