package cn.iocoder.yudao.module.pms.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import cn.iocoder.yudao.module.pms.workflow.entity.BaseEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 统一审批记录实体（Story 6）。
 *
 * <p>审批中心核心表，承载所有业务对象（项目/任务/交付件/风险/问题/变更/资源/成本/
 * 阶段退出/基线变更）的审批流转。审批退回后重新提交时复用原记录，{@code round} 字段
 * 递增记录轮次，历史明细由 {@link ApprovalHistory} 子表追加。</p>
 *
 * <p>状态机：{@code [DRAFT] → [PENDING] → [APPROVED]}
 * ；PENDING 可流转至 {@code REJECTED / WITHDRAWN / TIMEOUT}；REJECTED 重新提交后
 * round+1 回到 PENDING（复用原记录）。</p>
 *
 * <p>关联设计文档：§2.2 ApprovalRecord（行 214-231）、§3.5（行 429-500）、§6.9。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("wf_approval_record")
public class ApprovalRecord extends BaseEntity {

    /** 审批类型：PROJECT/TASK/DELIVERABLE/RISK/ISSUE/CHANGE/RESOURCE/COST/PHASE_EXIT/BASELINE_CHANGE */
    @NotBlank(message = "审批类型不能为空")
    @Size(max = 32, message = "审批类型长度不能超过 32 个字符")
    private String approvalType;

    /** 业务对象ID。 */
    @NotNull(message = "业务对象ID不能为空")
    private Long businessId;

    /** 业务编码冗余。 */
    @Size(max = 64, message = "业务编码长度不能超过 64 个字符")
    private String businessCode;

    /** 项目维度（可空，用于项目维度审批列表）。 */
    private Long projectId;

    /** Flowable 流程实例ID。 */
    @Size(max = 64, message = "流程实例ID长度不能超过 64 个字符")
    private String processInstanceId;

    /** 审批标题。 */
    @NotBlank(message = "审批标题不能为空")
    @Size(max = 200, message = "审批标题长度不能超过 200 个字符")
    private String title;

    /** 提交人ID。 */
    @NotNull(message = "提交人ID不能为空")
    private Long submitterId;

    /** 提交人姓名（冗余）。 */
    @Size(max = 64, message = "提交人姓名长度不能超过 64 个字符")
    private String submitterName;

    /** 当前节点ID（Flowable 任务ID）。 */
    @Size(max = 64, message = "当前节点ID长度不能超过 64 个字符")
    private String currentNodeId;

    /** 当前节点名称。 */
    @Size(max = 64, message = "当前节点名称长度不能超过 64 个字符")
    private String currentNodeName;

    /** 状态：PENDING / APPROVED / REJECTED / WITHDRAWN / TIMEOUT。 */
    @Builder.Default
    private String status = "PENDING";

    /** 审批轮次（退回后重新提交 +1）。 */
    @Builder.Default
    private Integer round = 1;

    /** 提交时间。 */
    private LocalDateTime submittedAt;

    /** 完成时间（通过/退回/撤回/超时后填充）。 */
    private LocalDateTime completedAt;

    /** 超时时间点（超时扫描依据）。 */
    private LocalDateTime timeoutAt;

    /** 是否已升级。 */
    @Builder.Default
    private Boolean escalated = false;

    /** 乐观锁版本号（MyBatis-Plus @Version）。 */
    @Version
    private Integer version;
}
