package cn.iocoder.yudao.module.pms.workflow.spi;

import cn.iocoder.yudao.module.pms.platform.api.spi.ApprovalTrigger;
import cn.iocoder.yudao.module.pms.workflow.security.SecurityUtils;
import cn.iocoder.yudao.module.pms.workflow.entity.ApprovalRecord;
import cn.iocoder.yudao.module.pms.workflow.service.ApprovalCenterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 审批流程触发 SPI 实现（TD-P8-008 BASELINE_CHANGE 审批实际触发用）。
 *
 * <p>实现 {@link ApprovalTrigger}，供 {@code pms-baseline} 跨模块触发 BASELINE_CHANGE 审批，
 * 避免直接依赖 {@code pms-workflow}（与 TD-P8-001 解耦模式一致）。</p>
 *
 * <p>触发流程：
 * <ol>
 *   <li>从 {@link SecurityUtils#getCurrentUserId()} 获取提交人ID（无用户上下文使用 0L 系统用户）</li>
 *   <li>构造 {@link ApprovalRecord}（approvalType/businessId/projectId/title/submitterId，初始 PENDING）</li>
 *   <li>调用 {@link ApprovalCenterService#createApproval} 落库（同时记录 SUBMIT 历史）</li>
 *   <li>返回审批记录ID供调用方回填 baseline.approvalRecordId</li>
 * </ol>
 * </p>
 *
 * <p>注：Flowable 流程实例由 {@code ApprovalDispatcher} 监听 Spring Event 异步启动并回填
 * {@code processInstanceId}（参见 §4.5 阶段推进 + 审批创建事务边界）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalTriggerImpl implements ApprovalTrigger {

    /** 系统用户ID（无用户上下文时的回退值，标识为系统自动触发）。 */
    private static final Long SYSTEM_USER_ID = 0L;

    private final ApprovalCenterService approvalCenterService;

    @Override
    public Long triggerApproval(String approvalType, Long businessId, Long projectId,
                                String title, String reason) {
        if (approvalType == null || approvalType.isBlank()) {
            log.warn("审批触发失败：approvalType 为空");
            return null;
        }
        if (businessId == null) {
            log.warn("审批触发失败：businessId 为空，approvalType={}", approvalType);
            return null;
        }
        Long submitterId = resolveSubmitterId();
        ApprovalRecord record = ApprovalRecord.builder()
                .approvalType(approvalType)
                .businessId(businessId)
                .projectId(projectId)
                .title(title != null && !title.isBlank() ? title : buildDefaultTitle(approvalType, businessId))
                .submitterId(submitterId)
                .status("PENDING")
                .round(1)
                .escalated(false)
                .build();
        try {
            ApprovalRecord created = approvalCenterService.createApproval(record);
            if (created == null || created.getId() == null) {
                log.error("审批触发失败：createApproval 返回空记录，approvalType={} businessId={}",
                        approvalType, businessId);
                return null;
            }
            log.info("审批触发成功：approvalType={} businessId={} projectId={} approvalRecordId={} reason={}",
                    approvalType, businessId, projectId, created.getId(), reason);
            return created.getId();
        } catch (Exception e) {
            log.error("审批触发异常：approvalType={} businessId={} projectId={}",
                    approvalType, businessId, projectId, e);
            return null;
        }
    }

    /** 解析当前登录用户ID；无用户上下文返回 0L（系统用户）。 */
    private Long resolveSubmitterId() {
        try {
            Long uid = SecurityUtils.getCurrentUserId();
            return uid != null ? uid : SYSTEM_USER_ID;
        } catch (Exception e) {
            log.warn("审批触发：无法获取当前用户ID，使用系统用户回退，reason={}", e.getMessage());
            return SYSTEM_USER_ID;
        }
    }

    /** 构造默认审批标题（当 title 为空时使用）。 */
    private String buildDefaultTitle(String approvalType, Long businessId) {
        return approvalType + " #" + businessId;
    }
}
