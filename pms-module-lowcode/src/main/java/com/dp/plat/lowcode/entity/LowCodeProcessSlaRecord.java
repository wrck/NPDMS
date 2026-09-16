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
 * 流程 SLA 记录实体（缺口4）。
 *
 * <p>记录每个流程任务的 SLA 配置、截止时间与预警/升级触发状态。
 * 由 {@code ProcessSlaService.recordSlaForTask} 在任务创建时记录，
 * 由定时任务 {@code checkSlaStatus} 在 80% 截止时间点触发预警、
 * 在截止时间点触发升级，最终由 {@code completeSla} 置 COMPLETED。</p>
 *
 * <p><b>状态机</b>：ACTIVE → WARNING（80% 时间点）→ ESCALATED（截止时间）
 * → COMPLETED（任务完成）。每个阶段触发对应微流（slaEscalationMicroflow）。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("pms_lowcode_process_sla_record")
public class LowCodeProcessSlaRecord extends BaseEntity {

    /** 流程实例ID */
    @NotBlank(message = "流程实例ID不能为空")
    @Size(max = 64, message = "流程实例ID长度不能超过 64 个字符")
    private String processInstanceId;

    /** 任务ID */
    @NotBlank(message = "任务ID不能为空")
    @Size(max = 64, message = "任务ID长度不能超过 64 个字符")
    private String taskId;

    /** SLA 配置 JSON（slaDuration/slaUnit/slaEscalationMicroflow） */
    private String slaConfigJson;

    /** 截止时间 */
    private LocalDateTime deadline;

    /** 是否已发送预警: 0否 1是 */
    @Builder.Default
    private Integer warningSent = 0;

    /** 是否已发送升级: 0否 1是 */
    @Builder.Default
    private Integer escalateSent = 0;

    /** 状态: ACTIVE/WARNING/ESCALATED/COMPLETED */
    @NotBlank(message = "状态不能为空")
    @Size(max = 16, message = "状态长度不能超过 16 个字符")
    @Builder.Default
    private String status = "ACTIVE";
}
