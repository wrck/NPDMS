package cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 项目模板门禁引用行 DO（F-PM03 / V52）
 * <p>
 * 门禁对任务/交付件/状态/流程的结构化引用（gate_code, ref_type, ref_code）三元组，
 * 发布校验据此逐项存在性检查（BR-2），不使用 JSON 承载。
 */
@TableName("proj_project_template_gate_reference")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectTemplateGateReferenceDO extends TenantBaseDO {

    /**
     * 门禁引用ID
     */
    @TableId
    private Long id;
    /**
     * 模板版本ID
     */
    private Long templateRevisionId;
    /**
     * 所属门禁码
     */
    private String gateCode;
    /**
     * 引用类型：TASK/DELIVERABLE/STATE/PROCESS
     */
    private String refType;
    /**
     * 引用编码
     */
    private String refCode;
    /**
     * 引用版本（流程引用时使用）
     */
    private String refVersion;
}
