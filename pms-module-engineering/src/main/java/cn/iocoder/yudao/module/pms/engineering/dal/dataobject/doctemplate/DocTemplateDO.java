package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.doctemplate;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * PMS 工程文档模板 DO（V36 结构化文档模板）。
 * <p>
 * 对应表 {@code pms_eng_doc_template}。
 * 支持需求分析(REQUIREMENT)和实施方案(SOLUTION)的结构化模板。
 * 状态：0 草稿、1 已发布、2 已停用。
 */
@TableName("pms_eng_doc_template")
@Data
@EqualsAndHashCode(callSuper = true)
public class DocTemplateDO extends TenantBaseDO {

    /**
     * 主键
     */
    @TableId
    private Long id;
    /**
     * 模板编号（如 DT-REQ-2026-001），全局唯一
     */
    private String code;
    /**
     * 模板名称
     */
    private String name;
    /**
     * 文档类别：REQUIREMENT 需求分析 / SOLUTION 实施方案
     */
    private String docCategory;
    /**
     * 父模板ID（支持继承，NULL表示基础模板）
     */
    private Long parentTemplateId;
    /**
     * 适用条件JSON：projectType/networkType/productType/implementMode/priority/isDefault
     */
    private String applicability;
    /**
     * 模板说明
     */
    private String description;
    /**
     * 当前生效版本ID（指向 pms_eng_doc_template_version.id）
     */
    private Long currentVersionId;
    /**
     * 状态：0 草稿 1 已发布 2 已停用
     */
    private Integer status;
    /**
     * 乐观锁版本号
     */
    @Version
    private Integer version;

}
