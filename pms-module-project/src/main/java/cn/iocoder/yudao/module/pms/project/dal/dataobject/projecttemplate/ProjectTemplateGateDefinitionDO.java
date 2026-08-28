package cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 项目模板门禁定义 DO（F-PM03 / V52）
 */
@TableName("proj_project_template_gate_definition")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectTemplateGateDefinitionDO extends TenantBaseDO {

    /**
     * 门禁定义ID
     */
    @TableId
    private Long id;
    /**
     * 模板版本ID
     */
    private Long templateRevisionId;
    /**
     * 门禁码（版本内唯一）
     */
    private String gateCode;
    /**
     * 门禁名称
     */
    private String name;
    /**
     * 类型：ENTRY准入/EXIT准出
     */
    private String gateType;
    /**
     * 所属阶段码
     */
    private String stageCode;
    /**
     * 门禁说明
     */
    private String description;
}
