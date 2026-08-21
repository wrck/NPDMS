package cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 项目模板 DO（模板身份/状态/优先级，F-PM03 / V52）
 * <p>
 * 状态：DRAFT草稿/ACTIVE生效/RETIRED停用（BR-1）；系统保留编码不得删除/复用/改义（BR-8）。
 * 逻辑删除后编码因 uk(tenant_id, code) 不可复用，符合编码不复用约束。
 */
@TableName("proj_project_template")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectTemplateDO extends TenantBaseDO {

    /**
     * 模板ID
     */
    @TableId
    private Long id;
    /**
     * 模板编码（租户内唯一，创建后不可修改）
     */
    private String code;
    /**
     * 模板名称
     */
    private String name;
    /**
     * 状态：DRAFT草稿/ACTIVE生效/RETIRED停用
     */
    private String status;
    /**
     * 匹配优先级（数值小者先命中）
     */
    private Integer matchPriority;
    /**
     * 业务场景描述
     */
    private String description;
    /**
     * 系统保留编码标志：不得删除/复用/改义
     */
    private Boolean systemReserved;
}
