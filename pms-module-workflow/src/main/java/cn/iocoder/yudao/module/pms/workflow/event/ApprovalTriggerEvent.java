package cn.iocoder.yudao.module.pms.workflow.event;

import cn.iocoder.yudao.module.pms.workflow.entity.ApprovalRecord;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 审批触发事件（Story 6）。
 *
 * <p>业务模块（项目/任务/交付件/风险/问题/变更/基线等）在需要审批时发布此事件，
 * 由 {@link cn.iocoder.yudao.module.pms.workflow.service.ApprovalDispatcher} 统一监听并创建审批记录。</p>
 *
 * <p>审批触发规则矩阵参考设计文档 §3.5（行 486-499）：</p>
 * <ul>
 *   <li>项目创建/启动/关闭/取消 → PROJECT_CREATE / PROJECT_START / PROJECT_CLOSE / PROJECT_CANCEL</li>
 *   <li>阶段跳过/退出 → PHASE_SKIP / PHASE_EXIT</li>
 *   <li>任务完成验收 → TASK_COMPLETE</li>
 *   <li>交付件提交/发布 → DELIVERABLE</li>
 *   <li>基线变更 → BASELINE_CHANGE</li>
 *   <li>风险/问题/变更 → RISK / ISSUE / CHANGE</li>
 * </ul>
 */
@Getter
public class ApprovalTriggerEvent extends ApplicationEvent {

    /** 审批类型（与 {@link ApprovalRecord#getApprovalType()} 一致）。 */
    private final String approvalType;

    /** 业务对象ID。 */
    private final Long businessId;

    /** 业务编码（可空，冗余）。 */
    private final String businessCode;

    /** 项目ID（可空，用于项目维度审批列表）。 */
    private final Long projectId;

    /** 审批标题。 */
    private final String title;

    /** 提交人ID。 */
    private final Long submitterId;

    /** 提交人姓名（可空，冗余）。 */
    private final String submitterName;

    /**
     * 构造审批触发事件。
     *
     * @param source        事件源
     * @param approvalType  审批类型
     * @param businessId    业务对象ID
     * @param businessCode  业务编码
     * @param projectId     项目ID（可空）
     * @param title         审批标题
     * @param submitterId   提交人ID
     * @param submitterName 提交人姓名（可空）
     */
    public ApprovalTriggerEvent(Object source, String approvalType, Long businessId,
                                String businessCode, Long projectId, String title,
                                Long submitterId, String submitterName) {
        super(source);
        this.approvalType = approvalType;
        this.businessId = businessId;
        this.businessCode = businessCode;
        this.projectId = projectId;
        this.title = title;
        this.submitterId = submitterId;
        this.submitterName = submitterName;
    }

    /**
     * 转换为审批记录实体（不含 ID 与审计字段，由服务层填充）。
     *
     * @return 审批记录
     */
    public ApprovalRecord toRecord() {
        return ApprovalRecord.builder()
                .approvalType(approvalType)
                .businessId(businessId)
                .businessCode(businessCode)
                .projectId(projectId)
                .title(title)
                .submitterId(submitterId)
                .submitterName(submitterName)
                .build();
    }
}
