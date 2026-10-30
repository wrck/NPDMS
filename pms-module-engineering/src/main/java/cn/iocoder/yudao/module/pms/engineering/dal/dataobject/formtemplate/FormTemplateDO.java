package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.formtemplate;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;

/**
 * PMS 准备数据表单模板 DO（FR-ENG-007）。
 * <p>
 * 对应表 {@code plt_form_template}。
 * 状态：0 草稿、1 已发布、2 已停用。
 */
@TableName("plt_form_template")
@Data
@EqualsAndHashCode(callSuper = true)
public class FormTemplateDO extends BaseBusinessEntity {

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

}
