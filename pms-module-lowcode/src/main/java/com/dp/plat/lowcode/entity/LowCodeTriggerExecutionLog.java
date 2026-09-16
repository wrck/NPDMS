package com.dp.plat.lowcode.entity;

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
 * 低代码触发器执行日志实体。
 *
 * <p>记录触发器每次执行的历史（输入、输出、状态、耗时、错误信息等），
 * 供前端查看执行轨迹与排查问题。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("pms_lowcode_trigger_execution_log")
public class LowCodeTriggerExecutionLog extends BaseEntity {

    /** 触发器 ID */
    private Long triggerId;

    /** 触发器编码 */
    @NotBlank(message = "触发器编码不能为空")
    @Size(max = 64, message = "触发器编码长度不能超过 64 个字符")
    private String triggerCode;

    /** 触发类型: CRUD / QUARTZ / EVENT */
    @NotBlank(message = "触发类型不能为空")
    @Size(max = 16, message = "触发类型长度不能超过 16 个字符")
    private String triggerType;

    /** 目标类型: MICROFLOW / PROCESS */
    @NotBlank(message = "目标类型不能为空")
    @Size(max = 16, message = "目标类型长度不能超过 16 个字符")
    private String targetType;

    /** 目标编码 */
    @NotBlank(message = "目标编码不能为空")
    @Size(max = 64, message = "目标编码长度不能超过 64 个字符")
    private String targetCode;

    /** 执行唯一ID（微流执行ID，用于串联同一次执行的节点轨迹） */
    @Size(max = 64, message = "执行ID长度不能超过 64 个字符")
    private String executionId;

    /** 输入数据 JSON */
    private String inputs;

    /** 输出结果 JSON */
    private String outputs;

    /** 执行状态: SUCCESS / FAILED */
    @NotBlank(message = "执行状态不能为空")
    @Size(max = 16, message = "执行状态长度不能超过 16 个字符")
    private String status;

    /** 失败原因（FAILED 时） */
    private String errorMessage;

    /** 执行耗时毫秒 */
    private Long durationMs;

    /** 操作人 */
    @Size(max = 64, message = "操作人长度不能超过 64 个字符")
    private String operator;
}
