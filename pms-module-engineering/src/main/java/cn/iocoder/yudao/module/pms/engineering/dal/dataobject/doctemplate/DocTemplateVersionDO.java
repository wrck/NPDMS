package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.doctemplate;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * PMS 工程文档模板版本 DO（V36 结构化文档模板）。
 * <p>
 * 对应表 {@code pms_eng_doc_template_version}。
 * published：0 未发布、1 已发布（已发布版本不可修改）。
 */
@TableName("pms_eng_doc_template_version")
@Data
@EqualsAndHashCode(callSuper = true)
public class DocTemplateVersionDO extends TenantBaseDO {

    /**
     * 主键
     */
    @TableId
    private Long id;
    /**
     * 模板ID
     */
    private Long templateId;
    /**
     * 版本标签（SemVer，如 1.0.0）
     */
    private String versionLabel;
    /**
     * 章节定义JSON（sections数组，每个section含code/title/fields的form-create规则）
     */
    private String sections;
    /**
     * 相对父模板的章节覆盖声明（key=章节编码，value=覆盖配置）
     */
    private String sectionOverrides;
    /**
     * 排除的父模板章节编码列表（如 ["wireless","log_retention"]）
     */
    private String excludedSections;
    /**
     * 版本变更说明
     */
    private String changeLog;
    /**
     * 0 未发布 1 已发布
     */
    private Integer published;

}
