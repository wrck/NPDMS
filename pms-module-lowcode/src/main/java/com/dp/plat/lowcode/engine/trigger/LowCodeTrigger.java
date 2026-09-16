package com.dp.plat.lowcode.engine.trigger;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dp.plat.common.entity.BaseEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 低代码触发器实体。
 *
 * <p>定义触发类型（CRUD/QUARTZ/EVENT）、目标类型（MICROFLOW/PROCESS）与目标编码，
 * 由对应执行器执行目标。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("pms_lowcode_trigger")
public class LowCodeTrigger extends BaseEntity {

    /** 触发器编码（唯一） */
    @NotBlank(message = "触发器编码不能为空")
    @Size(max = 64, message = "触发器编码长度不能超过 64 个字符")
    private String code;

    /** 触发器名称 */
    @NotBlank(message = "触发器名称不能为空")
    @Size(max = 128, message = "触发器名称长度不能超过 128 个字符")
    private String name;

    /** 触发类型: CRUD / QUARTZ / EVENT */
    @NotBlank(message = "触发类型不能为空")
    @Size(max = 32, message = "触发类型长度不能超过 32 个字符")
    private String type;

    /**
     * 配置 JSON，按触发类型不同结构不同：
     * <ul>
     *   <li>CRUD：{entityCode, operations:["CREATE","UPDATE","DELETE"], timing:["BEFORE","AFTER"]}</li>
     *   <li>QUARTZ：{cronExpression:"0 0/5 * * * ?", cron:"0 0/5 * * * ?"}（cron 为兼容别名）</li>
     *   <li>EVENT：{eventType:"xxx"}</li>
     * </ul>
     */
    @NotBlank(message = "配置不能为空")
    private String config;

    /** 目标类型: MICROFLOW / PROCESS */
    @NotBlank(message = "目标类型不能为空")
    @Size(max = 32, message = "目标类型长度不能超过 32 个字符")
    private String targetType;

    /** 目标编码 */
    @NotBlank(message = "目标编码不能为空")
    @Size(max = 128, message = "目标编码长度不能超过 128 个字符")
    private String targetCode;

    /** 状态: ACTIVE/INACTIVE */
    @Size(max = 16, message = "状态长度不能超过 16 个字符")
    @Builder.Default
    private String status = "ACTIVE";
}
