package cn.iocoder.yudao.module.pms.cutover.service.approval;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.cutover.api.approval.dto.*;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.CutoverApprovalInstanceDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.CutoverApprovalNodeDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.CutoverApprovalNotificationDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.CutoverApprovalInstanceMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.CutoverApprovalNodeMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.CutoverApprovalNotificationMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query.ApprovalInstanceLockQuery;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.cutover.service.approval.domain.CutoverApprovalRules;
import cn.iocoder.yudao.module.pms.cutover.service.approval.port.*;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;

import static cn.iocoder.yudao.module.pms.cutover.service.approval.CutoverApprovalApplicationException.Code.*;

/** P5审批应用内核；跨模块事实只经端口消费，完整生产装配留待依赖Gate。 */
public class CutoverApprovalApplicationService {
    private static final Snowflake IDS = IdUtil.getSnowflake();
    private final CutoverApprovalSourceAssembler sourceAssembler;
    private final CutoverApprovalInstanceMapper instanceMapper;
    private final CutoverApprovalNodeMapper nodeMapper;
    private final CutoverApprovalNotificationMapper notificationMapper;
    private final ProjectCutoverServiceManagerPort serviceManagerPort;
    private final CutoverApprovalRoleCandidatePort roleCandidatePort;
    private final CutoverApprovalProjectScopePort projectScopePort;
    private final PlatformCommandExecutionApi commandExecutionApi;
    private final LongSupplier currentUserId;
    private final Clock clock;

    public CutoverApprovalApplicationService(CutoverApprovalSourceAssembler sourceAssembler,
            CutoverApprovalInstanceMapper instanceMapper, CutoverApprovalNodeMapper nodeMapper,
            CutoverApprovalNotificationMapper notificationMapper,
            ProjectCutoverServiceManagerPort serviceManagerPort,
            CutoverApprovalRoleCandidatePort roleCandidatePort,
            CutoverApprovalProjectScopePort projectScopePort,
            PlatformCommandExecutionApi commandExecutionApi, LongSupplier currentUserId, Clock clock) {
        this.sourceAssembler = sourceAssembler; this.instanceMapper = instanceMapper; this.nodeMapper = nodeMapper;
        this.notificationMapper = notificationMapper; this.serviceManagerPort = serviceManagerPort;
        this.roleCandidatePort = roleCandidatePort; this.projectScopePort = projectScopePort;
        this.commandExecutionApi = commandExecutionApi; this.currentUserId = currentUserId; this.clock = clock;
    }

    public CutoverApprovalStartResult start(CutoverApprovalStartCommand command) {
        long actorId = currentUserId.getAsLong();
        if (actorId <= 0) throw failure(INVALID_REQUEST, "缺少受信当前用户");
        PlatformCommandExecutionApi.ExecutionResult<CutoverApprovalStartResult> execution = commandExecutionApi.execute(
                new PlatformCommandExecutionApi.IdempotencyScope(command.tenantId(),
                        "CUTOVER_APPROVAL_START:" + command.taskId() + ":" + command.planRevisionId(),
                        actorId, command.idempotencyKey()), sha256(JsonUtils.toJsonString(command)),
                CutoverApprovalStartResult.class, () -> startNew(command, actorId),
                result -> new PlatformCommandExecutionApi.SuccessFacts("CUTOVER_APPROVAL_START",
                        "CutoverApproval", String.valueOf(result.fact().approvalInstanceId()),
                        command.correlationId(), JsonUtils.toJsonString(result), List.of()));
        if (execution.decision() == PlatformCommandExecutionApi.Decision.CONFLICT)
            throw failure(IDEMPOTENCY_CONFLICT, "审批启动幂等载荷冲突");
        if (execution.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS || execution.response() == null)
            throw failure(IDEMPOTENCY_IN_PROGRESS, "审批启动命令处理中");
        return execution.decision() == PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED
                ? new CutoverApprovalStartResult(StartOutcome.REPLAYED, execution.response().fact())
                : execution.response();
    }

    public CutoverApprovalCommandResult pause(CutoverApprovalPauseCommand command) {
        long actorId = currentUserId.getAsLong();
        if (actorId <= 0) throw failure(INVALID_REQUEST, "缺少受信当前用户");
        PlatformCommandExecutionApi.ExecutionResult<CutoverApprovalCommandResult> execution = commandExecutionApi.execute(
                new PlatformCommandExecutionApi.IdempotencyScope(command.tenantId(),
                        "CUTOVER_APPROVAL_PAUSE:" + command.approvalInstanceId(), actorId, command.idempotencyKey()),
                sha256(JsonUtils.toJsonString(command)), CutoverApprovalCommandResult.class,
                () -> pauseNew(command, actorId), result -> new PlatformCommandExecutionApi.SuccessFacts(
                        "CUTOVER_APPROVAL_SOURCE_PAUSE", "CutoverApproval",
                        String.valueOf(result.fact().approvalInstanceId()), command.correlationId(),
                        JsonUtils.toJsonString(result), List.of()));
        if (execution.decision() == PlatformCommandExecutionApi.Decision.CONFLICT)
            throw failure(IDEMPOTENCY_CONFLICT, "审批暂停幂等载荷冲突");
        if (execution.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS || execution.response() == null)
            throw failure(IDEMPOTENCY_IN_PROGRESS, "审批暂停命令处理中");
        return execution.decision() == PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED
                ? new CutoverApprovalCommandResult(CommandOutcome.REPLAYED, execution.response().fact())
                : execution.response();
    }

    private CutoverApprovalCommandResult pauseNew(CutoverApprovalPauseCommand command, long actorId) {
        CutoverApprovalInstanceDO instance = instanceMapper.selectByIdForUpdate(new ApprovalInstanceLockQuery(
                command.tenantId(), command.approvalInstanceId(), null, null));
        require(instance != null, STATE_CONFLICT, "审批实例不存在");
        require("PENDING".equals(instance.getStatusCode())
                        && Objects.equals(instance.getVersion(), command.expectedApprovalVersion())
                        && Objects.equals(instance.getPlanRevisionId(), command.planRevisionId())
                        && Objects.equals(instance.getSourceSnapshotVersion(), command.expectedSourceSnapshotVersion()),
                VERSION_CONFLICT, "审批事实已变化");
        LocalDateTime now = LocalDateTime.now(clock);
        instance.setStatusCode("PAUSED_SOURCE_INVALIDATED"); instance.setHoldReasonCode(null);
        instance.setDecisionAt(now); instance.setUpdater(String.valueOf(actorId)); instance.setUpdateTime(now);
        require(instanceMapper.updateById(instance) == 1, VERSION_CONFLICT, "审批实例并发变化");
        List<CutoverApprovalNodeDO> nodes = nodeMapper.selectList(new LambdaQueryWrapperX<CutoverApprovalNodeDO>()
                .eq(CutoverApprovalNodeDO::getTenantId, command.tenantId())
                .eq(CutoverApprovalNodeDO::getApprovalInstanceId, command.approvalInstanceId())
                .in(CutoverApprovalNodeDO::getStatusCode, List.of("WAITING", "PENDING"))
                .orderByAsc(CutoverApprovalNodeDO::getNodeNo));
        for (CutoverApprovalNodeDO node : nodes) {
            node.setStatusCode("CANCELLED"); node.setUpdater(String.valueOf(actorId)); node.setUpdateTime(now);
            require(nodeMapper.updateById(node) == 1, VERSION_CONFLICT, "审批节点并发变化");
        }
        return new CutoverApprovalCommandResult(CommandOutcome.APPLIED, fact(instance));
    }

    private CutoverApprovalStartResult startNew(CutoverApprovalStartCommand command, long actorId) {
        CutoverApprovalSourceAssembler.LockedSource source = sourceAssembler.lockAndAssemble(command);
        if (instanceMapper.selectByTaskAndPlanForUpdate(new ApprovalInstanceLockQuery(command.tenantId(), null,
                command.taskId(), command.planRevisionId())) != null) throw failure(STATE_CONFLICT, "审批实例已存在");
        long projectId = source.task().getProjectId();
        List<NodeDraft> route = resolveRoute(command.tenantId(), projectId, actorId, command.grade());
        String hold = route.stream().anyMatch(value -> value.approverUserId() == null)
                ? "ROUTE_CANDIDATE_NOT_UNIQUE" : null;
        long instanceId = IDS.nextId();
        LocalDateTime now = LocalDateTime.now(clock);
        CutoverApprovalInstanceDO instance = new CutoverApprovalInstanceDO();
        instance.setId(instanceId); instance.setTenantId(command.tenantId()); instance.setTaskId(command.taskId());
        instance.setProjectId(projectId); instance.setPlanRevisionId(command.planRevisionId());
        instance.setPlanRevisionNo(command.planRevisionNo()); instance.setAssessmentId(command.assessmentId());
        instance.setAssessmentVersion(command.assessmentVersion()); instance.setChecklistId(command.checklistId());
        instance.setChecklistVersion(command.checklistVersion()); instance.setGradeCode(command.grade());
        instance.setInitiatorUserId(actorId); instance.setInitiatorProjectScopeVersion(route.getFirst().treeVersion());
        instance.setSourceSnapshotVersion(command.sourceSnapshotVersion()); instance.setSourceSnapshot(source.sourceSnapshot());
        instance.setRouteSnapshot(JsonUtils.toJsonString(route)); instance.setStatusCode("PENDING");
        instance.setHoldReasonCode(hold); instance.setCurrentNodeNo(1);
        instance.setPreviousApprovalInstanceId(command.previousApprovalInstanceId()); instance.setVersion(0);
        instance.setCreator(String.valueOf(actorId)); instance.setUpdater(String.valueOf(actorId));
        instance.setCreateTime(now); instance.setUpdateTime(now);
        require(instanceMapper.insert(instance) == 1, STATE_CONFLICT, "审批实例创建失败");
        if (command.previousApprovalInstanceId() != null) linkPrevious(command, instanceId);
        Long firstNodeId = null;
        for (int index = 0; index < route.size(); index++) {
            NodeDraft draft = route.get(index);
            CutoverApprovalNodeDO node = new CutoverApprovalNodeDO();
            node.setId(IDS.nextId()); node.setTenantId(command.tenantId()); node.setApprovalInstanceId(instanceId);
            node.setNodeNo(index + 1); node.setNodeCode(draft.nodeCode());
            node.setStatusCode(index == 0 ? "PENDING" : "WAITING");
            node.setOriginalApproverUserId(draft.approverUserId()); node.setCurrentApproverUserId(draft.approverUserId());
            node.setCandidateFactSnapshot(draft.candidateSnapshot()); node.setProjectScopeVersion(draft.treeVersion());
            node.setVersion(0); node.setCreator(String.valueOf(actorId)); node.setUpdater(String.valueOf(actorId));
            node.setCreateTime(now); node.setUpdateTime(now);
            require(nodeMapper.insert(node) == 1, STATE_CONFLICT, "审批节点创建失败");
            if (index == 0) firstNodeId = node.getId();
        }
        if (hold == null) insertNotification(command, instanceId, firstNodeId, actorId, now);
        return new CutoverApprovalStartResult(StartOutcome.STARTED, fact(instance));
    }

    private List<NodeDraft> resolveRoute(long tenantId, long projectId, long actorId, String grade) {
        List<NodeDraft> route = new ArrayList<>();
        CutoverApprovalProjectScopePort.ProjectScopeFact initiator = projectScopePort.inspect(
                tenantId, projectId, actorId, "ACTION_EDIT");
        CutoverApprovalProjectScopePort.ProjectScopeRevalidation lockedInitiator = projectScopePort.lockAndRevalidate(initiator);
        require(initiator.allowed() && lockedInitiator.outcome() == CutoverApprovalProjectScopePort.Revalidation.VALID,
                SOURCE_STALE, "发起人项目范围已变化");
        route.add(new NodeDraft("INITIATOR", actorId, initiator.treeVersion(), JsonUtils.toJsonString(Map.of(
                "userId", actorId, "projectId", projectId, "requiredAction", "ACTION_EDIT",
                "treeVersion", initiator.treeVersion()))));
        for (String code : CutoverApprovalRules.routeFor(grade).subList(1, CutoverApprovalRules.routeFor(grade).size())) {
            route.add("SERVICE_MANAGER".equals(code) ? serviceManager(tenantId, projectId, code)
                    : roleCandidate(tenantId, projectId, code));
        }
        return route;
    }

    private NodeDraft serviceManager(long tenantId, long projectId, String code) {
        ProjectCutoverServiceManagerPort.ServiceManagerFact inspected = serviceManagerPort.inspectCurrent(
                tenantId, projectId, LocalDateTime.now(clock));
        ProjectCutoverServiceManagerPort.ServiceManagerRevalidation locked = serviceManagerPort.lockAndRevalidate(inspected);
        require(locked.outcome() == ProjectCutoverServiceManagerPort.Revalidation.VALID
                        || locked.outcome() == ProjectCutoverServiceManagerPort.Revalidation.STALE,
                OWNER_DATA_CORRUPTED, "服务经理事实损坏");
        var current = locked.current();
        Long userId = current.outcome() == ProjectCutoverServiceManagerPort.Outcome.FOUND ? current.userId() : null;
        return new NodeDraft(code, userId, 0, JsonUtils.toJsonString(current));
    }

    private NodeDraft roleCandidate(long tenantId, long projectId, String code) {
        String group = "SECOND_LINE".equals(code) ? "CUT_SECOND_LINE_APPROVER" : "CUT_RND_APPROVER";
        CutoverApprovalRoleCandidatePort.CandidateSet candidates = roleCandidatePort.inspectCandidates(tenantId, group);
        var lockedCandidates = roleCandidatePort.lockAndRevalidate(candidates);
        require(lockedCandidates.outcome() == CutoverApprovalRoleCandidatePort.Revalidation.VALID,
                SOURCE_STALE, "角色候选事实已变化");
        List<Map<String, Object>> scoped = new ArrayList<>();
        List<Long> allowed = new ArrayList<>();
        long treeVersion = 0;
        for (var candidate : candidates.candidates()) {
            var scope = projectScopePort.inspect(tenantId, projectId, candidate.adminUserId(), "ACTION_VIEW");
            var locked = projectScopePort.lockAndRevalidate(scope);
            require(locked.outcome() == CutoverApprovalProjectScopePort.Revalidation.VALID,
                    SOURCE_STALE, "候选项目范围已变化");
            scoped.add(Map.of("userId", candidate.adminUserId(), "allowed", scope.allowed(),
                    "treeVersion", scope.treeVersion()));
            if (scope.allowed()) { allowed.add(candidate.adminUserId()); treeVersion = scope.treeVersion(); }
        }
        Long selected = allowed.size() == 1 ? allowed.getFirst() : null;
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("roleGroupCode", group); snapshot.put("candidates", candidates.candidates());
        snapshot.put("projectScopes", scoped); snapshot.put("selectedUserId", selected);
        return new NodeDraft(code, selected, selected == null ? 0 : treeVersion, JsonUtils.toJsonString(snapshot));
    }

    private void linkPrevious(CutoverApprovalStartCommand command, long replacementId) {
        CutoverApprovalInstanceDO previous = instanceMapper.selectByIdForUpdate(new ApprovalInstanceLockQuery(
                command.tenantId(), command.previousApprovalInstanceId(), null, null));
        require(previous != null && Objects.equals(previous.getTaskId(), command.taskId())
                        && List.of("REJECTED", "PAUSED_SOURCE_INVALIDATED").contains(previous.getStatusCode())
                        && previous.getReplacementApprovalInstanceId() == null,
                STATE_CONFLICT, "前序审批不可被替代");
        previous.setReplacementApprovalInstanceId(replacementId);
        require(instanceMapper.updateById(previous) == 1, VERSION_CONFLICT, "前序审批并发变化");
    }

    private void insertNotification(CutoverApprovalStartCommand command, long instanceId, Long nodeId,
                                    long actorId, LocalDateTime now) {
        CutoverApprovalNotificationDO row = new CutoverApprovalNotificationDO();
        row.setId(IDS.nextId()); row.setTenantId(command.tenantId()); row.setApprovalInstanceId(instanceId);
        row.setApprovalNodeId(nodeId); row.setRecipientUserId(actorId);
        row.setDeliveryKey("CUT_APPROVAL:" + instanceId + ":1:0"); row.setTemplateCode("CUT_APPROVAL_PENDING");
        row.setStatusCode("PENDING"); row.setRetryCount(0); row.setNextRetryAt(now); row.setVersion(0);
        row.setCreator(String.valueOf(actorId)); row.setUpdater(String.valueOf(actorId));
        row.setCreateTime(now); row.setUpdateTime(now);
        require(notificationMapper.insert(row) == 1, STATE_CONFLICT, "首节点通知创建失败");
    }

    public static CutoverApprovalFact fact(CutoverApprovalInstanceDO row) {
        return new CutoverApprovalFact(row.getId(), row.getVersion(), row.getTaskId(), row.getPlanRevisionId(),
                row.getPlanRevisionNo(), ApprovalStatus.valueOf(row.getStatusCode()), row.getSourceSnapshotVersion(),
                row.getReplacementApprovalInstanceId(), row.getDecisionAt() == null ? null
                : row.getDecisionAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
                row.getRejectionReason());
    }

    private static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    private static void require(boolean condition, CutoverApprovalApplicationException.Code code, String message) {
        if (!condition) throw failure(code, message);
    }
    private static CutoverApprovalApplicationException failure(CutoverApprovalApplicationException.Code code, String message) {
        return new CutoverApprovalApplicationException(code, message);
    }
    private record NodeDraft(String nodeCode, Long approverUserId, long treeVersion, String candidateSnapshot) { }
}
