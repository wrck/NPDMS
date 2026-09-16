package cn.iocoder.yudao.module.pms.workflow.service;

import cn.iocoder.yudao.module.pms.workflow.entity.ApprovalRecord;
import cn.iocoder.yudao.module.pms.workflow.event.ApprovalTriggerEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 审批分发器（Story 6）。
 *
 * <p>监听 Spring {@link ApprovalTriggerEvent}（业务模块发布）与其他业务事件，统一调用
 * {@link ApprovalCenterService#createApproval} 创建审批记录。</p>
 *
 * <p>使用 {@code @Async} 异步处理避免阻塞业务主流程；若创建失败仅记录日志不回滚业务事务
 * （审批记录可后续补偿创建）。</p>
 *
 * <p>关联设计文档：§3.5 审批触发规则矩阵（行 486-499）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalDispatcher {

    private final ApprovalCenterService approvalCenterService;

    /**
     * 监听通用审批触发事件 — 创建审批记录。
     *
     * <p>业务模块在需要审批时发布 {@link ApprovalTriggerEvent}，本方法异步创建审批记录。</p>
     *
     * @param event 审批触发事件
     */
    @Async
    @EventListener
    public void onApprovalTrigger(ApprovalTriggerEvent event) {
        try {
            ApprovalRecord record = event.toRecord();
            ApprovalRecord created = approvalCenterService.createApproval(record);
            log.info("审批分发完成：type={}, businessId={}, recordId={}, title={}",
                    event.getApprovalType(), event.getBusinessId(), created.getId(), event.getTitle());
        } catch (Exception e) {
            // 审批创建失败不阻断业务主流程，记录日志便于补偿
            log.error("审批分发失败：type={}, businessId={}, title={}",
                    event.getApprovalType(), event.getBusinessId(), event.getTitle(), e);
        }
    }
}
