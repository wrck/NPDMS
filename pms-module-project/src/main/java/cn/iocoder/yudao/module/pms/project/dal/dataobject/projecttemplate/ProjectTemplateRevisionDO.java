package cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 项目模板版本 DO（F-PM03 / V52）
 * <p>
 * 草稿即版本：每模板至多一个 DRAFT 工作副本（revision_no=0，可编辑）；
 * 发布时递增 revision_no 冻结为 PUBLISHED，此后应用层只读（BR-3）。
 */
@TableName("proj_project_template_revision")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectTemplateRevisionDO extends TenantBaseDO {

    /**
     * 版本ID
     */
    @TableId
    private Long id;
    /**
     * 模板ID
     */
    private Long templateId;
    /**
     * 版本号（0=草稿工作副本，发布时递增冻结）
     */
    private Integer revisionNo;
    /**
     * 状态：DRAFT草稿/PUBLISHED已发布
     */
    private String status;
    /**
     * 匹配条件：签约方式（字典 pms_signing_method，NULL=不限）
     */
    private String signingMethod;
    /**
     * 匹配条件：项目类别（字典 pms_project_category，NULL=不限）
     */
    private String projectCategory;
    /**
     * 匹配条件：实施方式（字典 pms_implementation_method，NULL=不限）
     */
    private String implementationMethod;
    /**
     * 匹配条件：重大项目级别（CRM来源属性映射，NULL=不限）
     */
    private String majorProjectLevel;
    /**
     * 模板级流程定义引用（仅存引用，不校验流程内部）
     */
    private String processDefinitionKey;
    /**
     * 流程定义版本引用
     */
    private String processDefinitionVersion;
    /**
     * 最近一次发布校验结果摘要（留痕）
     */
    private String validationSummary;
    /**
     * 发布人
     */
    private String publishedBy;
    /**
     * 发布时间
     */
    private LocalDateTime publishedTime;
}
