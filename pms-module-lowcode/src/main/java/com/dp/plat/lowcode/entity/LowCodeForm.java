package com.dp.plat.lowcode.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.dp.plat.common.entity.BaseEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 低代码表单配置实体。
 *
 * <p>存储表单设计器产出的 JSON Schema 配置，包含字段定义（fields）和布局（layout）。
 * 支持状态流转：DRAFT → PUBLISHED → ARCHIVED，以及按 code 唯一编码查询和导入导出。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("pms_lowcode_form")
public class LowCodeForm extends BaseEntity {

    /** 表单编码（唯一标识） */
    @NotBlank(message = "表单编码不能为空")
    @Size(max = 64, message = "表单编码长度不能超过 64 个字符")
    private String code;

    /** 表单名称 */
    @NotBlank(message = "表单名称不能为空")
    @Size(max = 128, message = "表单名称长度不能超过 128 个字符")
    private String name;

    /** 描述 */
    @Size(max = 512, message = "描述长度不能超过 512 个字符")
    private String description;

    /** 表单配置 JSON Schema（fields + layout） */
    @NotBlank(message = "表单配置不能为空")
    private String formConfig;

    /** 事件绑定 JSON: {onLoad:{type,code}, onChange:{...}, onSubmit:{...}} */
    private String events;

    /** 版本号（乐观锁） */
    @Version
    @Builder.Default
    private Integer version = 1;

    /** 状态: DRAFT/PUBLISHED/ARCHIVED */
    @Size(max = 16, message = "状态长度不能超过 16 个字符")
    @Builder.Default
    private String status = "DRAFT";

    /** 业务类型 */
    @Size(max = 64, message = "业务类型长度不能超过 64 个字符")
    private String bizType;
}
