package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.formtemplate;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * PMS 准备数据表单模板 DO（FR-ENG-007）。
 * <p>
 * 对应表 {@code pms_eng_form_template}。
 * 状态：0 草稿、1 已发布、2 已停用。
 */
@TableName("pms_eng_form_template")
@Data
@EqualsAndHashCode(callSuper = true)
public class FormTemplateDO extends TenantBaseDO {

    /**
     * 主键
     */
    @TableId
    private Long id;
    /**
     * 模板编号（如 FT-2026-001），全局唯一
     */
    private String code;
    /**
     * 模板名称
     */
    private String name;
    /**
     * 产品类型（联动条件）
     */
    private String productType;
    /**
     * 表单配置JSON（form-create conf）
     */
    private String conf;
    /**
     * 表单字段JSON（form-create fields）
     */
    private String fields;
    /**
     * 模板说明
     */
    private String description;
    /**
     * 状态：0 草稿 1 已发布 2 已停用
     */
    private Integer status;
    /**
     * 模板版本号
     */
    @Version
    private Integer version;

}
