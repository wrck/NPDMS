package cn.iocoder.yudao.module.pms.project.service.normalclosure;

import cn.iocoder.yudao.module.pms.project.api.closure.ProjectClosureExitApi;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.normalclosure.NormalClosureExitRecordDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.normalclosure.ClosureProjectMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectStageInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectStageStatusUpdate;
import cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectChildWaitEvents;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

/** CLO-02 审批通过的 PROJ 侧退出执行：复检、终态阶段推进、NORMAL_CLOSED 写入与退出记录，整体原子。 */
@Service @RequiredArgsConstructor
public class NormalClosureExitExecutor implements ProjectClosureExitApi {
    private final NormalClosureAccess access;
    private final NormalClosureCheckService checks;
    private final ClosureProjectMapper closureProjects;
    private final ProjectStageInstanceMapper stages;
    private final ProjectChildWaitEvents childWaitEvents;

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public ClosureExitResult executeApprovedExit(ClosureExitCommand command) {
        var context = access.lock(command.projectId(), command.expectedProjectVersion(),
                command.expectedTreeVersion(), new NormalClosureAccess.Actor(command.tenantId(), command.actorId(), command.correlationId()));
        var project = context.project();
        if (!Objects.equals(command.expectedFromStage(), project.getCurrentStage()))
            throw closureFailure("CLOSURE_SOURCE_STAGE_CHANGED");
        var smIds = access.lockPrimaryServiceManagerUserIds(command.tenantId(), command.projectId());
        if (smIds.size() != 1 || !Objects.equals(command.expectedServiceManagerUserId(), smIds.getFirst()))
            throw closureFailure("CLOSURE_PRIMARY_SM_CHANGED");
        var evaluation = checks.evaluateLocked(project, context.treeVersion(), command.actorId(), command.correlationId());
        if (!evaluation.passed() || !Objects.equals(command.expectedReviewerUserId(), reviewerOf(evaluation.policyJson())))
            throw closureFailure("CLOSURE_REVALIDATION_FAILED");
        if (!Objects.equals(command.expectedSourceDigest(), evaluation.sourceDigest()))
            throw closureFailure("CLOSURE_APPROVED_FACTS_STALE_CANCEL_AND_REAPPLY");
        var templateRevisionId = closureProjects.selectFrozenTemplateRevisionId(
                new ClosureProjectMapper.TemplateRevisionQuery(command.tenantId(),
                        project.getLifecycleTemplateId(), project.getLifecycleTemplateRevisionNo()));
        if (templateRevisionId == null) throw closureFailure("CLOSURE_TEMPLATE_REVISION_UNAVAILABLE");
        var stage = evaluation.graph().current();
        if (stages.updateStatusIfMatch(new ProjectStageStatusUpdate(command.tenantId(), command.projectId(), stage.getId(),
                stage.getVersion(), "ACTIVE", "DONE", command.actorId().toString(), LocalDateTime.now())) != 1)
            throw closureFailure("CLOSURE_STAGE_VERSION_CONFLICT");
        LocalDateTime now = LocalDateTime.now();
        if (closureProjects.closeProjectIfMatch(new ClosureProjectMapper.ExitUpdate(command.tenantId(), command.projectId(),
                project.getVersion(), project.getCurrentStage(), now, command.actorId().toString())) != 1)
            throw closureFailure("CLOSURE_PROJECT_VERSION_CONFLICT");
        var exit = new NormalClosureExitRecordDO();
        exit.setId(IdWorker.getId()); exit.setTenantId(command.tenantId()); exit.setProjectId(command.projectId());
        exit.setApplicationId(command.applicationId()); exit.setSnapshotId(command.snapshotId()); exit.setClosureType("NORMAL");
        exit.setClosedFromStage(project.getCurrentStage()); exit.setProjectVersion(project.getVersion() + 1);
        exit.setStageInstanceId(stage.getId()); exit.setTemplateRevisionId(templateRevisionId);
        exit.setScopeVersion(context.treeVersion()); exit.setGateSnapshotRef(command.snapshotId());
        exit.setSourceContext("ACC"); exit.setSourceRecordId(command.applicationId());
        exit.setBeforeLifecycleStatus("ACTIVE"); exit.setAfterLifecycleStatus("NORMAL_CLOSED");
        exit.setBeforeProjectVersion(project.getVersion()); exit.setAfterProjectVersion(project.getVersion() + 1);
        exit.setProcessInstanceId(command.processInstanceId());
        exit.setRevalidationEvidence(evaluation.sourceVector()); exit.setClosedAt(now);
        exit.setCreator(command.actorId().toString()); exit.setUpdater(command.actorId().toString());
        if (closureProjects.insertExitRecord(exit) != 1) throw closureFailure("CLOSURE_EXIT_WRITE_FAILED");
        childWaitEvents.closureChanged(command.tenantId(), command.projectId(), command.actorId(), command.correlationId());
        return new ClosureExitResult(exit.getId(), Long.valueOf(project.getVersion() + 1), stage.getId(), templateRevisionId);
    }

    /** 评审人身份取自冻结闭环策略 JSON；不在此处完整解析策略语义（归 ACC）。 */
    private static Long reviewerOf(String policyJson) {
        if (policyJson == null || policyJson.isBlank()) return null;
        try {
            var node = cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseTree(policyJson);
            var reviewer = node == null ? null : node.get("reviewerUserId");
            return reviewer == null || reviewer.isNull() ? null : Long.valueOf(reviewer.asText());
        } catch (RuntimeException ex) { return null; }
    }

    private static cn.iocoder.yudao.framework.common.exception.ServiceException closureFailure(String reason) {
        return new cn.iocoder.yudao.framework.common.exception.ServiceException(
                cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.ACC_PROJECT_CLOSURE_VALIDATION_FAILED.getCode(), reason);
    }
}
