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

import java.time.LocalDateTime;

/**
 * 低代码微流执行轨迹实体（借鉴 Joget APM）。
 *
 * <p>记录每次微流执行中各节点的开始/结束时间、耗时、输入输出与变量快照，
 * 通过 executionId 串联同一次执行的所有节点轨迹。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("pms_lowcode_microflow_execution_log")
public class LowCodeMicroflowExecutionLog extends BaseEntity {

    /** 微流ID */
    private Long microflowId;

    /** 微流编码 */
    @NotBlank(message = "微流编码不能为空")
    @Size(max = 64, message = "微流编码长度不能超过 64 个字符")
    private String microflowCode;

    /** 执行唯一ID（UUID） */
    @NotBlank(message = "执行ID不能为空")
    @Size(max = 64, message = "执行ID长度不能超过 64 个字符")
    private String executionId;

    /** 节点ID */
    @NotBlank(message = "节点ID不能为空")
    @Size(max = 64, message = "节点ID长度不能超过 64 个字符")
    private String nodeId;

    /** 节点类型 */
    @NotBlank(message = "节点类型不能为空")
    @Size(max = 32, message = "节点类型长度不能超过 32 个字符")
    private String nodeType;

    /** 节点执行开始时间 */
    private LocalDateTime startTime;

    /** 节点执行结束时间 */
    private LocalDateTime endTime;

    /** 节点执行耗时（毫秒） */
    private Long durationMs;

    /** 节点输入（config JSON 字符串） */
    private String inputs;

    /** 节点输出（result 等 JSON 字符串） */
    private String outputs;

    /** 执行前变量快照（JSON 字符串） */
    private String variablesSnapshot;

    /** 状态: RUNNING/SUCCESS/FAILED */
    @NotBlank(message = "状态不能为空")
    @Size(max = 16, message = "状态长度不能超过 16 个字符")
    private String status;

    /** 错误信息（FAILED 时） */
    private String errorMessage;

    /** 操作人 */
    @Size(max = 64, message = "操作人长度不能超过 64 个字符")
    private String operator;
}
