package cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

/** PM-03 已发布模板中的任务执行契约。 */
@TableName(value = "proj_project_template_task_definition", autoResultMap = true)
@Data
public class ProjectTemplateTaskDefinitionDO {

    @TableId
    private Long id;
    private Long tenantId;
    private Long templateRevisionId;
    private String stageDefinitionKey;
    private String taskDefinitionKey;
    private String parentTaskDefinitionKey;
    private String name;
    private Integer sortOrder;
    private String workBindingTypeCode;
    private String targetContextCode;
    private String targetObjectType;
    private String targetObjectKey;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private JsonNode bindingConfig;
    private String permissionPolicyRef;
    private String completionRuleTypeCode;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private JsonNode completionRuleConfig;
    private String gateRef;
    private Integer definitionVersion;
    private Integer version;
}
