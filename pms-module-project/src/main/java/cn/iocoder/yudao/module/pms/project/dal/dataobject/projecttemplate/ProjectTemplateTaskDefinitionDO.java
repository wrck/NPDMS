package cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 项目模板任务定义 DO（F-PM03 / V52）
 */
@TableName("proj_project_template_task_definition")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectTemplateTaskDefinitionDO extends TenantBaseDO {

    /**
     * 任务定义ID
     */
    @TableId
    private Long id;
    /**
     * 模板版本ID
     */
    private Long templateRevisionId;
    /**
     * 任务码（版本内唯一）
     */
    private String taskCode;
    /**
     * 任务名称
     */
    private String name;
    /**
     * 父任务码（NULL=顶层）
     */
    private String parentTaskCode;
    /**
     * 所属阶段码
     */
    private String stageCode;
    /**
     * 优先级
     */
    private Integer priority;
    /**
     * 排序
     */
    private Integer sortOrder;
    /**
     * 预估工时
     */
    private BigDecimal estimatedHours;
    /**
     * 满意度适用时点（NULL=不适用，由ACC-02消费）
     */
    private String satisfactionTiming;
    /**
     * 任务说明
     */
    private String description;
    /**
     * V1.8阶段定义稳定键
     */
    private String stageDefinitionKey;
    /**
     * V1.8任务定义稳定键
     */
    private String taskDefinitionKey;
    /**
     * V1.8父任务定义稳定键
     */
    private String parentTaskDefinitionKey;
    /**
     * 工作绑定类型
     */
    private String workBindingTypeCode;
    private String targetContextCode;
    private String targetObjectType;
    private String targetObjectKey;
    private String componentKey;
    private Long dynamicFormRevisionId;
    private String approvalDefinitionKey;
    private String bindingConfig;
    private String permissionPolicyRef;
    private String completionRuleTypeCode;
    private String completionRuleConfig;
    private String gateRef;
    private Integer definitionVersion;
}
