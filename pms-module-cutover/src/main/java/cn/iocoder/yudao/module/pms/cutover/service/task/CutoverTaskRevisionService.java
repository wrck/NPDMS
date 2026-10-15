package cn.iocoder.yudao.module.pms.cutover.service.task;

import cn.hutool.core.util.IdUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.*;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage.CutoverAssessmentDeviceDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.query.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.CutoverChecklistMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.query.CutoverChecklistRowQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.plan.CutoverPlanRevisionMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.plan.query.CutoverPlanRevisionQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.CutoverApprovalInstanceMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query.ApprovalInstanceLockQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.CutoverAssessmentDeviceMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.query.CutoverAssessmentDeviceListQuery;
import cn.iocoder.yudao.module.pms.cutover.service.approval.CutoverApprovalApplicationService;
import cn.iocoder.yudao.module.pms.cutover.service.approval.command.WithdrawCutoverApprovalCommand;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverChecklistRevisionService;
import cn.iocoder.yudao.module.pms.cutover.service.plan.CutoverPlanApplicationService;
import cn.iocoder.yudao.module.pms.cutover.service.plan.command.ReviseCutoverPlanCommand;
import cn.iocoder.yudao.module.pms.cutover.service.task.command.ReviseCutoverTaskCommand;
import cn.iocoder.yudao.module.pms.cutover.service.task.context.CutoverTaskContext;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.configuration.CutoverConfigurationRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration.CutoverConfigurationRevisionMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration.query.CutoverConfigurationStorageQuery;
import cn.iocoder.yudao.module.pms.cutover.service.configuration.CutoverIntakeConfigurationPolicy;
import cn.iocoder.yudao.module.pms.cutover.service.task.domain.CutoverTaskRules;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverProjectScopePort;
import cn.iocoder.yudao.module.pms.cutover.service.task.result.CutoverTaskCommandResult;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import static cn.iocoder.yudao.module.pms.cutover.service.task.CutoverTaskApplicationException.Code.*;

/** 前序修订的单一事务：撤回待办、使依赖失效、创建草稿、状态机回退与审计共同提交。 */
@Service
@RequiredArgsConstructor
public class CutoverTaskRevisionService {
    private final CutoverTaskMapper tasks;
    private final CutoverAssessmentMapper assessments;
    private final CutoverAssessmentDeviceMapper assessmentDevices;
    private final CutoverChecklistMapper checklists;
    private final CutoverPlanRevisionMapper plans;
    private final CutoverApprovalInstanceMapper approvals;
    private final CutoverTaskStageHistoryMapper history;
    private final CutoverProjectScopePort scopes;
    private final CutoverApprovalApplicationService approvalService;
    private final CutoverChecklistRevisionService checklistRevisions;
    private final CutoverPlanApplicationService planService;
    private final PlatformCommandExecutionApi commands;
    private final CutoverConfigurationRevisionMapper configurations;
    private final CutoverIntakeConfigurationPolicy configurationPolicy;

    public record ConfigurationOptionsQuery(CutoverTaskContext context, Long taskId) { }

    public List<CutoverIntakeConfigurationPolicy.Options> configurationOptions(ConfigurationOptionsQuery query) {
        var context = query.context();
        var task = tasks.selectById(query.taskId());
        require(task != null && Objects.equals(task.getTenantId(), context.tenantId())
                && Objects.equals(task.getOwnerUserId(), context.actorId()) && "NEW_PLATFORM".equals(task.getTaskOrigin()),
                NOT_FOUND, "割接任务不可修订");
        var scope = scopes.inspect(context.actorId(), task.getProjectId(), "ACTION_EDIT");
        require(scope != null && scope.allowed() && Objects.equals(scope.projectId(), task.getProjectId()),
                DATA_SCOPE_FORBIDDEN, "无项目编辑范围");
        try { CutoverTaskRules.revisionTarget(task.getCurrentStage(), task.getTaskStatus(), task.getManualGrade(), "P2"); }
        catch (IllegalArgumentException invalid) { throw new CutoverTaskApplicationException(STATE_CONFLICT, invalid.getMessage()); }
        return configurationPolicy.available(context.tenantId()).stream()
                .filter(option -> Objects.equals(option.code(), task.getConfigurationCode()))
                .filter(option -> !Objects.equals(option.revisionId(), task.getConfigurationRevisionId()))
                .filter(option -> option.cutoverTypes().stream().anyMatch(value -> Objects.equals(value.value(), task.getCutoverType())))
                .filter(option -> task.getNetworkMode() == null || option.networkModes().stream().anyMatch(value -> Objects.equals(value.value(), task.getNetworkMode())))
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public CutoverTaskCommandResult revise(ReviseCutoverTaskCommand command) {
        require(command != null && command.tenantId() != null && command.actorId() != null && command.actorId() > 0
                        && command.taskId() != null && command.taskId() > 0 && command.expectedTaskVersion() != null
                        && command.expectedTaskVersion() >= 0 && command.expectedProjectScopeVersion() != null
                        && command.targetStage() != null && Set.of("P2", "P3", "P4").contains(command.targetStage())
                        && (command.configurationRevisionId() == null || command.configurationRevisionId() > 0)
                        && command.reason() != null && !command.reason().isBlank() && command.reason().length() <= 1000
                        && command.idempotencyKey() != null && !command.idempotencyKey().isBlank()
                        && command.idempotencyKey().length() <= 128 && command.correlationId() != null,
                INVALID_REQUEST, "修订请求不完整");
        var visible = owned(tasks.selectById(command.taskId()), command);
        requireScope(scopes.inspect(command.actorId(), visible.getProjectId(), "ACTION_EDIT"), visible, command);
        var digest = cn.hutool.crypto.digest.DigestUtil.sha256Hex(JsonUtils.toJsonString(Map.of(
                "taskId", command.taskId(), "taskVersion", command.expectedTaskVersion(), "projectScopeVersion", command.expectedProjectScopeVersion(),
                "targetStage", command.targetStage(), "reason", command.reason(),
                "configurationRevisionId", command.configurationRevisionId() == null ? "UNCHANGED" : command.configurationRevisionId())));
        var execution = commands.execute(new PlatformCommandExecutionApi.IdempotencyScope(command.tenantId(),
                        "CUT:TASK_REVISION:" + command.taskId(), command.actorId(), command.idempotencyKey()),
                digest, CutoverTaskCommandResult.class, () -> reviseOnce(command),
                result -> new PlatformCommandExecutionApi.SuccessFacts("CUTOVER_TASK_REVISION", "CutoverTask", String.valueOf(result.taskId()),
                        command.correlationId(), JsonUtils.toJsonString(Map.of("result", result, "reason", command.reason(),
                                "configurationRevisionId", command.configurationRevisionId() == null ? "UNCHANGED" : command.configurationRevisionId())), List.of()));
        require(execution.decision() != PlatformCommandExecutionApi.Decision.CONFLICT, IDEMPOTENCY_CONFLICT, "修订幂等键载荷冲突");
        require(execution.response() != null && execution.decision() != PlatformCommandExecutionApi.Decision.IN_PROGRESS,
                IDEMPOTENCY_IN_PROGRESS, "修订命令处理中");
        return execution.decision() == PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED
                ? execution.response().replayedCopy() : execution.response();
    }

    private CutoverTaskCommandResult reviseOnce(ReviseCutoverTaskCommand command) {
        var inspected = owned(tasks.selectById(command.taskId()), command);
        requireScope(scopes.lockAndRevalidate(CutoverProjectScopePort.RevalidationQuery.builder().actorId(command.actorId())
                .projectId(inspected.getProjectId()).action("ACTION_EDIT").expectedProjectScopeVersion(command.expectedProjectScopeVersion()).build()), inspected, command);
        var task = owned(tasks.selectForUpdate(new CutoverTaskRowQuery(command.tenantId(), command.taskId())), command);
        require(Objects.equals(task.getVersion(), command.expectedTaskVersion()), TASK_VERSION_CONFLICT, "任务版本已变化，请刷新后修订");
        CutoverTaskRules.RevisionTarget target;
        try { target = CutoverTaskRules.revisionTarget(task.getCurrentStage(), task.getTaskStatus(), task.getManualGrade(), command.targetStage()); }
        catch (IllegalArgumentException invalid) { throw new CutoverTaskApplicationException(STATE_CONFLICT, invalid.getMessage()); }
        var configuration = selectedConfiguration(new ConfigurationSelection(command, task));
        var assessment = assessments.selectForUpdate(new CutoverAssessmentRowQuery(command.tenantId(), task.getId(), task.getCurrentAssessmentId()));
        require(assessment != null && "SUBMITTED".equals(assessment.getAssessmentStatus()), STATE_CONFLICT, "当前等级评估未提交");
        var checklist = checklists.selectCurrentForUpdate(new CutoverChecklistRowQuery(command.tenantId(), task.getId(), null));
        if (target.replaceChecklist()) require(checklist != null && "SUBMITTED".equals(checklist.getStatusCode()), STATE_CONFLICT, "当前调研清单未提交");
        var plan = plans.selectCurrentForUpdate(new CutoverPlanRevisionQuery(command.tenantId(), task.getId(), null));
        Long approvalVersion = plan == null ? null : plan.getApprovalVersion();
        if ("P5".equals(task.getCurrentStage())) {
            require(plan != null, STATE_CONFLICT, "审批中的当前方案不存在");
            approvalVersion = approvalService.withdrawForRevision(new WithdrawCutoverApprovalCommand(task, plan, command.actorId(), command.reason())).approvalVersion();
        } else if (plan != null && plan.getApprovalInstanceId() != null) {
            var approval = approvals.selectByTaskAndPlanForUpdate(new ApprovalInstanceLockQuery(command.tenantId(), null, task.getId(), plan.getId()));
            require(approval != null && "REJECTED".equals(approval.getStatusCode()), STATE_CONFLICT, "方案审批状态不允许修订");
            approvalVersion = approval.getVersion();
        }
        LocalDateTime now = LocalDateTime.now();
        if (plan != null) require(plans.invalidateForRevisionIfMatch(CutoverRevisionInvalidationUpdate.builder()
                .tenantId(command.tenantId()).taskId(task.getId()).sourceId(plan.getId()).expectedVersion(plan.getVersion())
                .actorId(command.actorId()).time(now).reason(command.reason()).approvalVersion(approvalVersion).build()) == 1, STATE_CONFLICT, "方案已变化");
        if ((target.replaceAssessment() || target.replaceChecklist()) && checklist != null) {
            require(checklists.invalidateForRevisionIfMatch(CutoverRevisionInvalidationUpdate.builder()
                    .tenantId(command.tenantId()).taskId(task.getId()).sourceId(checklist.getId()).expectedVersion(checklist.getVersion())
                    .actorId(command.actorId()).time(now).reason(command.reason()).build()) == 1, STATE_CONFLICT, "清单已变化");
        }
        Long assessmentId = target.replaceAssessment() ? copyAssessment(new AssessmentRevision(command, assessment, now)).getId() : assessment.getId();
        String fromStage = task.getCurrentStage(); String fromStatus = task.getTaskStatus();
        require(tasks.reviseIfMatch(new CutoverTaskRevisionUpdate(task, target, assessmentId, command.actorId(), configuration)) == 1, TASK_VERSION_CONFLICT, "任务阶段已变化");
        task.setCurrentStage(target.stage()); task.setTaskStatus(target.status()); task.setCurrentAssessmentId(assessmentId);
        task.setVersion(task.getVersion() + 1); if (target.replaceAssessment()) task.setManualGrade(null);
        if (configuration != null) {
            task.setConfigurationRevisionId(configuration.getId()); task.setConfigurationCode(configuration.getConfigurationCode());
            task.setConfigurationRevisionNo(configuration.getRevisionNo());
        }
        // 更换配置后由负责人重新匹配；新清单生成时只承接字段定义一致的既有填报。
        if (target.replaceChecklist() && configuration == null) checklistRevisions.copyDraft(new CutoverChecklistRevisionService.Revision(command, task, checklist, now));
        if ("P4".equals(target.stage())) {
            planService.revise(new ReviseCutoverPlanCommand(command.tenantId(), command.actorId(), task.getId(), task.getVersion(),
                    plan.getId(), "USER_REVISION", "revision-plan:" + task.getVersion(), command.correlationId()));
        }
        Integer sequence = tasks.selectMaxStageHistorySequence(new CutoverTaskRowQuery(command.tenantId(), task.getId()));
        var event = CutoverTaskStageHistoryDO.builder().id(IdUtil.getSnowflakeNextId()).tenantId(command.tenantId()).cutoverTaskId(task.getId())
                .sequenceNo((sequence == null ? 0 : sequence) + 1).fromStage(fromStage).fromStatus(fromStatus).toStage(target.stage()).toStatus(target.status())
                .triggerType("TASK_REVISION").triggerReferenceId(assessmentId).actorId(command.actorId()).correlationId(command.correlationId()).occurredAt(now).build();
        event.setCreator(String.valueOf(command.actorId())); event.setCreateTime(now);
        require(history.insert(event) == 1, STATE_CONFLICT, "修订阶段历史保存失败");
        return new CutoverTaskCommandResult(task.getId(), task.getTaskNo(), task.getCurrentStage(), task.getTaskStatus(), task.getVersion(), false);
    }

    private record ConfigurationSelection(ReviseCutoverTaskCommand command, CutoverTaskDO task) { }

    private CutoverConfigurationRevisionDO selectedConfiguration(ConfigurationSelection selection) {
        var command = selection.command(); var task = selection.task();
        if (command.configurationRevisionId() == null || Objects.equals(command.configurationRevisionId(), task.getConfigurationRevisionId())) return null;
        require(Set.of("P2", "P3").contains(command.targetStage()), INVALID_REQUEST, "更换配置需要修订等级评估或调研清单");
        var configuration = configurations.selectForUpdate(new CutoverConfigurationStorageQuery(command.tenantId(), command.configurationRevisionId()));
        var now = LocalDateTime.now();
        require(configuration != null && Objects.equals(configuration.getTenantId(), command.tenantId())
                        && Objects.equals(configuration.getConfigurationCode(), task.getConfigurationCode())
                        && "PUBLISHED".equals(configuration.getStatusCode()) && configuration.getEffectiveFrom() != null
                        && !configuration.getEffectiveFrom().isAfter(now)
                        && (configuration.getEffectiveTo() == null || configuration.getEffectiveTo().isAfter(now)),
                CONFIGURATION_CONFLICT, "只能选择同一配置当前生效的已发布版本");
        configurationPolicy.validate(configuration, task.getCutoverType(), task.getNetworkMode());
        return configuration;
    }

    private record AssessmentRevision(ReviseCutoverTaskCommand command, CutoverAssessmentDO source, LocalDateTime time) { }

    private CutoverAssessmentDO copyAssessment(AssessmentRevision revision) {
        var command = revision.command(); var source = revision.source();
        require(assessments.invalidateForRevisionIfMatch(CutoverRevisionInvalidationUpdate.builder().tenantId(command.tenantId())
                .taskId(command.taskId()).sourceId(source.getId()).expectedVersion(source.getVersion()).actorId(command.actorId())
                .time(revision.time()).reason(command.reason()).build()) == 1, ASSESSMENT_VERSION_CONFLICT, "等级评估已变化");
        var draft = new CutoverAssessmentDO(); BeanUtils.copyProperties(source, draft);
        draft.setId(IdUtil.getSnowflakeNextId()); draft.setAssessmentVersion(source.getAssessmentVersion() + 1); draft.setVersion(0L);
        draft.setAssessmentStatus("DRAFT"); draft.setSimpleFlow(false); draft.setCurrentMarker(1);
        draft.setSubmittedBy(null); draft.setSubmittedAt(null); draft.setInvalidatedBy(null); draft.setInvalidatedAt(null); draft.setInvalidationReason(null);
        draft.setCreator(String.valueOf(command.actorId())); draft.setUpdater(String.valueOf(command.actorId()));
        draft.setCreateTime(revision.time()); draft.setUpdateTime(revision.time());
        require(assessments.insert(draft) == 1, STATE_CONFLICT, "修订评估创建失败");
        for (var previous : assessmentDevices.selectListByParents(CutoverAssessmentDeviceListQuery.builder().tenantId(command.tenantId()).assessmentIds(Set.of(source.getId())).build())) {
            var device = new CutoverAssessmentDeviceDO(); BeanUtils.copyProperties(previous, device);
            device.setId(null); device.setAssessmentId(draft.getId()); device.setCreateUserId(command.actorId()); device.setCreateTime(revision.time());
            require(assessmentDevices.insert(device) == 1, STATE_CONFLICT, "修订评估设备复制失败");
        }
        return draft;
    }

    private static CutoverTaskDO owned(CutoverTaskDO task, ReviseCutoverTaskCommand command) {
        require(task != null && Objects.equals(task.getTenantId(), command.tenantId()) && Objects.equals(task.getOwnerUserId(), command.actorId())
                && "NEW_PLATFORM".equals(task.getTaskOrigin()), NOT_FOUND, "割接任务不可修订");
        return task;
    }
    private static void requireScope(CutoverProjectScopePort.ProjectScopeFact scope, CutoverTaskDO task, ReviseCutoverTaskCommand command) {
        require(scope != null && scope.allowed() && Objects.equals(scope.projectId(), task.getProjectId()), DATA_SCOPE_FORBIDDEN, "无项目编辑范围");
        require(Objects.equals(scope.projectScopeVersion(), command.expectedProjectScopeVersion())
                && Objects.equals(task.getProjectScopeVersion(), command.expectedProjectScopeVersion()), PROJECT_SCOPE_STALE, "项目范围已变化");
    }
    private static void require(boolean valid, CutoverTaskApplicationException.Code code, String message) {
        if (!valid) throw new CutoverTaskApplicationException(code, message);
    }
}
