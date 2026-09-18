package cn.iocoder.yudao.module.pms.project.service.operation;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.BusinessEvent;
import cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformBusinessEventApi;
import cn.iocoder.yudao.module.pms.project.api.runtime.ProjectRuleReevaluationRequested;
import cn.iocoder.yudao.module.pms.project.dal.mysql.businessresult.ResultEvidenceMapper;
import cn.iocoder.yudao.module.pms.project.domain.rule.RuleEvaluation;
import cn.iocoder.yudao.module.pms.project.domain.template.ResultEvidencePolicy;
import cn.iocoder.yudao.module.pms.project.domain.template.ResultSubscriptionContract;
import cn.iocoder.yudao.module.pms.project.service.projectplan.ProjectStageCompletionService;
import cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectStageAdmissionService;
import cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectTaskAdmissionService;
import cn.iocoder.yudao.module.pms.project.service.taskbusiness.ProjectTaskBusinessAssociationService;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.ProjectTaskLifecycleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 评估通知只唤醒原正式推进服务；不能以单个扫描结果替代实时规则、全部订阅或执行轮次。 */
@Service
@RequiredArgsConstructor
public class ProjectResultEvidenceProcessor {
    private final ProjectResultSubscriptionContext contexts;
    private final ResultEvidenceMapper evidence;
    private final ProjectTaskAdmissionService taskAdmission;
    private final ProjectStageAdmissionService stageAdmission;
    private final ProjectTaskBusinessAssociationService associations;
    private final ProjectTaskLifecycleService tasks;
    private final ProjectStageCompletionService stages;
    private final PlatformBusinessEventApi outbox;
    private final OperationAuditApi audit;

    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public String process(ResultEvidenceEvaluatedEvent event) {
        Objects.requireNonNull(event, "evidence event");
        var context = contexts.lock(event.target());
        if (context == null) return "OBSOLETE_RECIPIENT";
        var row = context.subscription();
        var scan = evidence.selectById(new ResultEvidenceMapper.ScanId(row.getTenantId(), row.getProjectId(), event.scanId()));
        if (scan == null || !Objects.equals(event.scanId(), scan.getId())
                || !Objects.equals(row.getTenantId(), scan.getTenantId()) || !Objects.equals(row.getProjectId(), scan.getProjectId())
                || !Objects.equals(row.getId(), scan.getSubscriptionId()) || scan.getSubscriptionVersion() == null)
            throw new IllegalArgumentException("EVIDENCE_EVENT_SCAN_MISMATCH");
        if (scan.getSubscriptionVersion() > row.getVersion()) throw new IllegalArgumentException("EVIDENCE_FUTURE_CHECKPOINT");
        if (scan.getSubscriptionVersion() < row.getVersion() || !"LIVE".equals(row.getPhase())) return "OBSOLETE_EVALUATION";
        ProjectResultEvidenceScanner.requireScan(scan, row);
        if ("COLLECTING".equals(scan.getStatus())) throw new IllegalStateException("EVIDENCE_EVALUATION_INCOMPLETE");
        var definition = ResultSubscriptionContract.read(row.getConfiguration());
        var decision = ResultEvidencePolicy.decide(definition,
                JsonUtils.parseObject(scan.getAccumulator(), ResultEvidencePolicy.Accumulator.class), true);
        if (!decision.status().name().equals(scan.getStatus())) throw new IllegalStateException("EVIDENCE_EVALUATION_CORRUPT");
        var round = context.execution();
        // 完成历史不能由后来的扫描改写，失效影响由独立的历史证据复核处理。
        if (round.getEndedAt() != null || Set.of("DONE", "COMPLETED", "TERMINATED").contains(round.getStatus()))
            return "HISTORICAL_RECIPIENT";
        if (!"ACTIVE".equals(context.project().getLifecycleStatus())) return "PROJECT_NOT_ACTIVE";
        if (!"EFFECTIVE".equals(context.plan().getStatus())
                || !Objects.equals(row.getPlanVersionId(), context.project().getActivePlanVersionId())) return "OBSOLETE_RECIPIENT";
        if (!decision.satisfied()) return "WAITING_EVIDENCE";

        boolean changed;
        if ("TASK".equals(row.getNodeKind())) {
            var admitted = taskAdmission.activateEligible(row.getProjectId(), row.getNodeId(), event.eventId());
            if (admitted.unknown()) throw new IllegalStateException("EVIDENCE_ADMISSION_UNKNOWN");
            if (!admitted.activated() && !"ACTIVE".equals(round.getStatus())) return "WAITING_ADMISSION";
            if (context.snapshot().getTasks().stream().anyMatch(node -> row.getNodeKey().equals(node.getNodeKey()) && node.getBinding() != null))
                associations.synchronize(row.getProjectId(), row.getNodeId(), event.eventId());
            var completed = tasks.completeFromBusinessResult(row.getProjectId(), row.getNodeId(), event.eventId());
            if (completed.unknown()) throw new IllegalStateException("EVIDENCE_COMPLETION_UNKNOWN");
            changed = admitted.activated() || completed.completed();
        } else if ("STAGE".equals(row.getNodeKind())) {
            var admitted = stageAdmission.activateStage(row.getProjectId(), null, event.eventId(), row.getNodeId());
            if (admitted == null || admitted.stream().anyMatch(item -> item.outcome() == RuleEvaluation.Outcome.UNKNOWN))
                throw new IllegalStateException("EVIDENCE_ADMISSION_UNKNOWN");
            boolean activated = admitted.stream().anyMatch(ProjectStageAdmissionService.StageAdmission::activated);
            if (!activated && !"ACTIVE".equals(round.getStatus())) return "WAITING_ADMISSION";
            if (context.snapshot().getStages().stream().anyMatch(node -> row.getNodeKey().equals(node.getNodeKey()) && node.getBinding() != null))
                associations.synchronizeStage(row.getProjectId(), row.getNodeId(), event.eventId());
            var completed = stages.completeStage(row.getProjectId(), row.getNodeId(), null, event.eventId());
            if (completed.unknown()) throw new IllegalStateException("EVIDENCE_COMPLETION_UNKNOWN");
            changed = activated || completed.completed() > 0;
        } else throw new IllegalStateException("EVIDENCE_NODE_KIND_UNSUPPORTED");
        if (!changed) return "REEVALUATED_WAITING";
        // 不提前持有业务结果通道锁；正式Writer在原Owner事实与规则检查之后执行证据屏障。
        audit.record(row.getTenantId(), 0L, event.eventId(), "PROJECT_EVIDENCE_ADVANCED", "ProjectNode",
                row.getNodeId().toString(), "SUCCESS", Map.of("scanId", scan.getId(), "subscriptionId", row.getId(),
                        "executionId", row.getExecutionId(), "planVersionId", row.getPlanVersionId()));
        var reevaluation = ProjectRuleReevaluationRequested.create(row.getTenantId(), row.getProjectId(), null, event.eventId());
        outbox.append("Project", row.getProjectId().toString(), new BusinessEvent(reevaluation.eventId(),
                ProjectRuleReevaluationRequested.EVENT_TYPE, JsonUtils.toJsonString(reevaluation)));
        return "ADVANCED";
    }
}
