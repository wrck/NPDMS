package cn.iocoder.yudao.module.pms.workflow.spi;

import cn.iocoder.yudao.module.pms.platform.api.spi.dto.TemplateSnapshot.ApprovalPlanDef;
import cn.iocoder.yudao.module.pms.platform.api.spi.ApprovalPlanBatchCreator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 审批计划批量注册 SPI 实现（TD-P8-003 模板深拷贝用）。
 *
 * <p>从模板创建项目时，{@code pms-project} 通过 {@link ApprovalPlanBatchCreator} 跨模块调用本实现，
 * 注册模板中定义的审批计划（当项目进入某阶段时触发某类型审批）。</p>
 *
 * <p>当前实现：仅记录日志（不立即创建审批记录）。实际审批在阶段推进时由
 * {@code ApprovalDispatcher} 监听 Spring Event 触发。模板深拷贝阶段仅注册计划元数据，
 * 后续可在该实现中扩展为持久化到审批计划表或发布到事件总线。</p>
 *
 * <p>关联设计文档：§3.5 审批中心统一规则、§4.5 阶段推进 + 审批创建事务边界。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalPlanBatchCreatorImpl implements ApprovalPlanBatchCreator {

    @Override
    public void batchCreateApprovalPlans(Long projectId,
                                         Map<String, Long> phaseCodeToIdMap,
                                         List<ApprovalPlanDef> approvalPlanDefs) {
        if (projectId == null || approvalPlanDefs == null || approvalPlanDefs.isEmpty()) {
            return;
        }
        int registered = 0;
        int skipped = 0;
        for (ApprovalPlanDef def : approvalPlanDefs) {
            Long triggerPhaseId = null;
            if (def.getTriggerPhaseCode() != null && phaseCodeToIdMap != null) {
                triggerPhaseId = phaseCodeToIdMap.get(def.getTriggerPhaseCode());
            }
            if (triggerPhaseId == null) {
                log.warn("模板深拷贝：审批计划 {} 的触发阶段 {} 未找到，跳过（projectId={}）",
                        def.getApprovalType(), def.getTriggerPhaseCode(), projectId);
                skipped++;
                continue;
            }
            // 当前实现仅记录日志；实际审批在阶段推进时由 ApprovalDispatcher 监听事件触发。
            // 后续可在此扩展为持久化到 wf_approval_plan 表或发布到事件总线。
            log.info("模板深拷贝：注册审批计划 projectId={} approvalType={} triggerPhaseId={} approverRoles={}",
                    projectId, def.getApprovalType(), triggerPhaseId, def.getApproverRoles());
            registered++;
        }
        log.info("模板深拷贝：批量注册审批计划完成 projectId={} registered={} skipped={}",
                projectId, registered, skipped);
    }
}
