package cn.iocoder.yudao.module.pms.acceptance.service.normalclosure;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.normalclosure.BpmNormalClosureApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.closure.ProjectClosureCheckApi;
import cn.iocoder.yudao.module.pms.project.api.closure.ProjectClosureContextApi;
import cn.iocoder.yudao.module.pms.project.api.closure.ProjectClosureExitApi;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.normalclosure.*;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.normalclosure.NormalClosureMapper;
import cn.iocoder.yudao.module.system.api.permission.ExplicitPermissionApi;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Supplier;
import static cn.iocoder.yudao.module.pms.acceptance.service.normalclosure.NormalClosureErrors.failure;

@Service @RequiredArgsConstructor
public class NormalClosureApplicationService {
    private final ProjectClosureContextApi contexts;
    private final ProjectClosureCheckApi checks;
    private final ProjectClosureExitApi exits;
    private final NormalClosureMapper mapper;
    private final PlatformCommandExecutionApi commands;
    private final BpmNormalClosureApi bpm;
    private final ExplicitPermissionApi explicitPermissions;
    public record Actor(Long tenantId, Long userId, String correlationId) {}
    public record CheckCommand(Long projectId, Integer expectedProjectVersion, Long expectedTreeVersion, String idempotencyKey) {}
    public record SubmitCommand(Long projectId, Long snapshotId, Integer expectedProjectVersion, Long expectedTreeVersion, String idempotencyKey) {}
    public record DecisionResult(Long applicationId, Long projectId, String status) {}

    @Transactional(rollbackFor = Exception.class)
    public NormalClosureSnapshotDO check(CheckCommand command, Actor actor) {
        contexts.read(actor.tenantId(), command.projectId(), actor.userId(),
                ProjectClosureContextApi.PERMISSION_SUBMIT); // also authorize idempotency replays
        validate(command.expectedProjectVersion(), command.expectedTreeVersion(), command.idempotencyKey());
        return execute("CHECK", command.projectId(), command.idempotencyKey(), command, actor, NormalClosureSnapshotDO.class, () -> {
            var context = contexts.lock(actor.tenantId(), command.projectId(), command.expectedProjectVersion(),
                    command.expectedTreeVersion(), actor.userId());
            var evaluation = evaluate(actor, command.projectId(), command.expectedProjectVersion(),
                    command.expectedTreeVersion(), context.treeVersion());
            var row = new NormalClosureSnapshotDO();
            row.setId(IdWorker.getId()); row.setTenantId(actor.tenantId()); row.setProjectId(command.projectId());
            row.setProjectVersion(context.projectVersion()); row.setTreeVersion(context.treeVersion());
            row.setFromStage(context.currentStage()); row.setClosureType("NORMAL"); row.setRuleRevision(1);
            row.setPassed(evaluation.passed()); row.setEvidence(evaluation.evidence()); row.setSourceVector(evaluation.sourceVector());
            row.setSourceDigest(evaluation.sourceDigest()); row.setCheckedBy(actor.userId()); row.setCheckedAt(LocalDateTime.now());
            row.setCreator(actor.userId().toString()); row.setUpdater(actor.userId().toString());
            if (mapper.insertSnapshot(row) != 1) throw failure("CLOSURE_SNAPSHOT_WRITE_FAILED");
            return row;
        });
    }

    @Transactional(rollbackFor = Exception.class)
    public NormalClosureApplicationDO submit(SubmitCommand command, Actor actor) {
        contexts.read(actor.tenantId(), command.projectId(), actor.userId(), ProjectClosureContextApi.PERMISSION_SUBMIT);
        validate(command.expectedProjectVersion(), command.expectedTreeVersion(), command.idempotencyKey());
        if (command.snapshotId() == null || command.snapshotId() <= 0) throw failure("CLOSURE_SNAPSHOT_REQUIRED");
        return execute("SUBMIT", command.projectId(), command.idempotencyKey(), command, actor, NormalClosureApplicationDO.class, () -> {
            var context = contexts.lock(actor.tenantId(), command.projectId(), command.expectedProjectVersion(),
                    command.expectedTreeVersion(), actor.userId());
            var projectQuery = new NormalClosureMapper.ProjectQuery(actor.tenantId(), command.projectId());
            var latest = mapper.selectLatestApplication(projectQuery);
            if (latest != null && ("IN_REVIEW".equals(latest.getStatus()) || "APPROVED".equals(latest.getStatus())))
                throw failure("CLOSURE_APPLICATION_IN_PROGRESS");
            var snapshot = mapper.selectLatestSnapshot(projectQuery);
            requireSnapshot(snapshot, command, context.currentStage());
            if (latest != null && Objects.equals(latest.getSnapshotId(), snapshot.getId()))
                throw failure("CLOSURE_NEW_SNAPSHOT_REQUIRED_AFTER_REJECTION");
            var evaluation = evaluate(actor, command.projectId(), command.expectedProjectVersion(),
                    command.expectedTreeVersion(), context.treeVersion());
            var policy = NormalClosurePolicy.parseFrozen(evaluation.closurePolicySnapshot());
            if (!evaluation.passed() || !Objects.equals(snapshot.getSourceDigest(), evaluation.sourceDigest()))
                throw failure("CLOSURE_SNAPSHOT_STALE");
            Long sm = requireServiceManager(actor.tenantId(), command.projectId());
            requireReviewer(actor.tenantId(), policy.reviewerUserId());
            var definition = bpm.inspectDefinition(actor.tenantId(), policy.processDefinitionKey());
            Long id = IdWorker.getId(); String businessKey = "PROJECT_NORMAL_CLOSURE:" + id;
            var started = bpm.start(new BpmNormalClosureApi.StartCommand(actor.tenantId(), actor.userId(), businessKey,
                    policy.processDefinitionKey(), sm, policy.reviewerUserId(), command.projectId()));
            NormalClosureBpmEvidence.requireStarted(definition, started, sm, policy.reviewerUserId());
            var row = new NormalClosureApplicationDO();
            row.setId(id); row.setTenantId(actor.tenantId()); row.setProjectId(command.projectId()); row.setSnapshotId(snapshot.getId());
            row.setClosureType("NORMAL"); row.setRuleRevision(1); row.setFromStage(context.currentStage());
            row.setProjectVersion(context.projectVersion()); row.setTreeVersion(context.treeVersion()); row.setStatus("IN_REVIEW");
            row.setApplicantUserId(actor.userId()); row.setServiceManagerUserId(sm); row.setReviewerUserId(policy.reviewerUserId());
            row.setProcessDefinitionKey(policy.processDefinitionKey()); row.setProcessDefinitionId(started.actualDefinitionId());
            row.setProcessInstanceId(started.instanceId()); row.setBusinessKey(businessKey); row.setProcessEvidence(JsonUtils.toJsonString(started));
            row.setSubmittedAt(LocalDateTime.now()); row.setVersion(0); row.setCreator(actor.userId().toString()); row.setUpdater(actor.userId().toString());
            if (mapper.insertApplication(row) != 1) throw failure("CLOSURE_APPLICATION_WRITE_FAILED");
            return row;
        });
    }

    /** Only the real BPM Owner event invokes this method. No HTTP approve/pass command exists. */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void onBpmResult(Long tenantId, String processInstanceId) {
        if (!Objects.equals(tenantId, TenantContextHolder.getRequiredTenantId())) throw failure("CLOSURE_BPM_TENANT_MISMATCH");
        var result = bpm.inspectResult(tenantId, processInstanceId);
        if (result == null || result.businessKey() == null || !result.businessKey().startsWith("PROJECT_NORMAL_CLOSURE:"))
            throw failure("CLOSURE_BPM_BUSINESS_KEY_MISMATCH");
        Long applicationId;
        try { applicationId = Long.valueOf(result.businessKey().substring("PROJECT_NORMAL_CLOSURE:".length())); }
        catch (NumberFormatException ex) { throw failure("CLOSURE_BPM_BUSINESS_KEY_MISMATCH"); }
        // Read only identity first. Project locks always precede closure application locks.
        var identity = mapper.selectApplicationByProcess(new NormalClosureMapper.ProcessQuery(tenantId, processInstanceId));
        if (identity == null || !Objects.equals(applicationId, identity.getId())) throw failure("CLOSURE_APPLICATION_NOT_FOUND");
        var context = contexts.lock(tenantId, identity.getProjectId(), identity.getProjectVersion(),
                identity.getTreeVersion(), identity.getApplicantUserId());
        var query = new NormalClosureMapper.ApplicationQuery(tenantId, identity.getProjectId(), applicationId);
        var application = mapper.selectApplicationForUpdate(query);
        if (application == null) throw failure("CLOSURE_APPLICATION_NOT_FOUND");
        var reviews = NormalClosureBpmEvidence.requireResult(application, result);
        if ("RUNNING".equals(result.status())) return;
        if (!"IN_REVIEW".equals(application.getStatus())) {
            String expected = "APPROVE".equals(result.status()) ? "APPROVED" : "REJECT".equals(result.status()) ? "REJECTED" : "CANCELLED";
            if (!expected.equals(application.getStatus())) throw failure("CLOSURE_BPM_DECISION_CONFLICT");
            return;
        }
        var actor = new Actor(tenantId, application.getApplicantUserId(), "BPM_NORMAL_CLOSURE:" + processInstanceId);
        execute("BPM_RESULT", context.projectId(), processInstanceId + ":" + result.status(), result, actor, DecisionResult.class, () -> {
            LocalDateTime now = LocalDateTime.now();
            String status = "APPROVE".equals(result.status()) ? "APPROVED" : "REJECT".equals(result.status()) ? "REJECTED" : "CANCELLED";
            if ("APPROVE".equals(result.status())) {
                var original = mapper.selectSnapshot(new NormalClosureMapper.SnapshotQuery(tenantId, context.projectId(), application.getSnapshotId()));
                if (original == null || !Boolean.TRUE.equals(original.getPassed()))
                    throw failure("CLOSURE_APPROVED_FACTS_STALE_CANCEL_AND_REAPPLY");
                exits.executeApprovedExit(new ProjectClosureExitApi.ClosureExitCommand(tenantId, context.projectId(),
                        application.getProjectVersion(), application.getTreeVersion(), actor.userId(), actor.correlationId(),
                        applicationId, application.getSnapshotId(), application.getReviewerUserId(),
                        application.getFromStage(), application.getServiceManagerUserId(),
                        original.getSourceDigest(), processInstanceId));
            }
            for (var review : reviews) appendReview(application, review);
            if (mapper.decideApplicationIfMatch(new NormalClosureMapper.ApplicationDecision(tenantId, context.projectId(), applicationId,
                    application.getVersion(), status, now, actor.userId().toString())) != 1) throw failure("CLOSURE_APPLICATION_VERSION_CONFLICT");
            return new DecisionResult(applicationId, context.projectId(), status);
        });
    }

    private ProjectClosureCheckApi.ClosureEvaluation evaluate(Actor actor, Long projectId,
            Integer expectedProjectVersion, Long expectedTreeVersion, Long treeVersion) {
        return checks.evaluateLocked(new ProjectClosureCheckApi.ClosureCheckCommand(actor.tenantId(), projectId,
                expectedProjectVersion, treeVersion, actor.userId(), actor.correlationId()));
    }

    private Long requireServiceManager(Long tenantId, Long projectId) {
        var ids = contexts.lockPrimaryServiceManagerUserIds(tenantId, projectId).stream()
                .filter(Objects::nonNull).distinct().toList();
        if (ids.size() != 1) throw failure("CLOSURE_PRIMARY_SM_REQUIRED");
        return ids.getFirst();
    }
    private void requireReviewer(Long tenantId, Long userId) {
        if (!explicitPermissions.lockAndCheck(tenantId, userId, ProjectClosureContextApi.PERMISSION_AUDIT))
            throw failure("CLOSURE_EXPLICIT_MATERIAL_REVIEWER_REQUIRED");
    }
    private void appendReview(NormalClosureApplicationDO application, BpmNormalClosureApi.Review fact) {
        var row = new NormalClosureReviewDO(); row.setId(IdWorker.getId()); row.setTenantId(application.getTenantId());
        row.setApplicationId(application.getId()); row.setProjectId(application.getProjectId()); row.setProcessInstanceId(application.getProcessInstanceId());
        row.setProcessDefinitionId(application.getProcessDefinitionId()); row.setTaskId(fact.taskId()); row.setTaskDefinitionKey(fact.taskDefinitionKey());
        row.setReviewerUserId(fact.assigneeUserId()); row.setOutcome(fact.decision()); row.setReason(fact.reason()); row.setReviewedAt(fact.completedAt());
        row.setCreator(fact.assigneeUserId().toString()); row.setUpdater(fact.assigneeUserId().toString());
        if (mapper.insertReview(row) != 1) throw failure("CLOSURE_REVIEW_WRITE_FAILED");
    }
    static void requireSnapshot(NormalClosureSnapshotDO snapshot, SubmitCommand command, String stage) {
        if (snapshot == null || !Objects.equals(snapshot.getId(), command.snapshotId()) || !Boolean.TRUE.equals(snapshot.getPassed())
                || !Objects.equals(snapshot.getProjectId(), command.projectId())
                || !Objects.equals(snapshot.getProjectVersion(), command.expectedProjectVersion())
                || !Objects.equals(snapshot.getTreeVersion(), command.expectedTreeVersion())
                || !Objects.equals(snapshot.getFromStage(), stage)) throw failure("CLOSURE_LATEST_PASSED_SNAPSHOT_REQUIRED");
    }
    private static void validate(Integer version, Long treeVersion, String key) {
        if (version == null || version < 0 || treeVersion == null || treeVersion <= 0 || key == null || key.isBlank() || key.length() > 128)
            throw failure("CLOSURE_COMMAND_INVALID");
    }
    private <T> T execute(String action, Long projectId, String key, Object payload, Actor actor, Class<T> type, Supplier<T> operation) {
        var execution = commands.execute(new PlatformCommandExecutionApi.IdempotencyScope(actor.tenantId(), "PROJECT_NORMAL_CLOSURE_" + action,
                        actor.userId(), key), NormalClosurePolicy.digest(JsonUtils.toJsonString(payload)), type, operation,
                value -> new PlatformCommandExecutionApi.SuccessFacts("PROJECT_NORMAL_CLOSURE_" + action, "Project", projectId.toString(),
                        actor.correlationId(), JsonUtils.toJsonString(value), List.of()));
        if (execution.decision() == PlatformCommandExecutionApi.Decision.CONFLICT) throw failure("CLOSURE_IDEMPOTENCY_CONFLICT");
        if (execution.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS || execution.response() == null)
            throw failure("CLOSURE_IDEMPOTENCY_IN_PROGRESS");
        return execution.response();
    }
}
