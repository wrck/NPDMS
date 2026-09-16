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
 * 低代码规则实体。
 *
 * <p>支持三种规则类型：决策表（DECISION_TABLE）/ 表达式（EXPRESSION）/ LiteFlow（LITEFLOW）。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("pms_lowcode_rule")
public class LowCodeRule extends BaseEntity {

    /** 规则编码（唯一） */
    @NotBlank(message = "规则编码不能为空")
    @Size(max = 64, message = "规则编码长度不能超过 64 个字符")
    private String code;

    /** 规则名称 */
    @NotBlank(message = "规则名称不能为空")
    @Size(max = 128, message = "规则名称长度不能超过 128 个字符")
    private String name;

    /** 描述 */
    @Size(max = 512, message = "描述长度不能超过 512 个字符")
    private String description;

    /** 规则类型: DECISION_TABLE / EXPRESSION / LITEFLOW */
    @NotBlank(message = "规则类型不能为空")
    @Size(max = 32, message = "规则类型长度不能超过 32 个字符")
    private String type;

    /** 规则定义（决策表 JSON / 表达式 / LiteFlow EL） */
    @NotBlank(message = "规则定义不能为空")
    private String definition;

    /** 状态: DRAFT/PUBLISHED/ARCHIVED */
    @Size(max = 16, message = "状态长度不能超过 16 个字符")
    @Builder.Default
    private String status = "DRAFT";

    /** 版本号（乐观锁） */
    @Version
    @Builder.Default
    private Integer version = 1;

    /** 业务类型 */
    @Size(max = 64, message = "业务类型长度不能超过 64 个字符")
    private String bizType;

    /** 扩展信息（JSON 字符串，如表达式规则的 inputsSchema） */
    private String ext;
}
