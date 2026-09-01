package cn.iocoder.yudao.module.pms.cutover.service.approval;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.cutover.api.approval.dto.*;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.CutoverApprovalInstanceDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.CutoverApprovalNodeDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.CutoverApprovalNotificationDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.CutoverApprovalReviewItemDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskStageHistoryDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.CutoverApprovalInstanceMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.CutoverApprovalNodeMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.CutoverApprovalNotificationMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.CutoverApprovalReviewItemMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query.ApprovalNodeLockQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query.ApprovalNodeStatusUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskStageHistoryMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.query.CutoverTaskApprovalTransitionUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.query.CutoverTaskRowQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query.ApprovalInstanceLockQuery;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.cutover.service.approval.domain.CutoverApprovalRules;
import cn.iocoder.yudao.module.pms.cutover.service.approval.command.*;
import cn.iocoder.yudao.module.pms.cutover.service.approval.port.*;
import cn.iocoder.yudao.module.pms.cutover.service.approval.result.CutoverApprovalDecisionResult;
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
import java.util.Set;
import java.util.function.LongSupplier;

import static cn.iocoder.yudao.module.pms.cutover.service.approval.CutoverApprovalApplicationException.Code.*;

/** P5审批应用内核；跨模块事实只经端口消费，完整生产装配留待依赖Gate。 */
public class CutoverApprovalApplicationService {
    private static final Snowflake IDS = IdUtil.getSnowflake();
    private final CutoverApprovalSourceAssembler sourceAssembler;
    private final CutoverApprovalInstanceMapper instanceMapper;
    private final CutoverApprovalNodeMapper nodeMapper;
    private final CutoverApprovalNotificationMapper notificationMapper;
    private final CutoverApprovalReviewItemMapper reviewItemMapper;
    private final CutoverTaskMapper taskMapper;
    private final CutoverTaskStageHistoryMapper historyMapper;
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
        this.reviewItemMapper = null; this.taskMapper = null; this.historyMapper = null;
    }

    public CutoverApprovalApplicationService(CutoverApprovalSourceAssembler sourceAssembler,
            CutoverApprovalInstanceMapper instanceMapper, CutoverApprovalNodeMapper nodeMapper,
            CutoverApprovalNotificationMapper notificationMapper, CutoverApprovalReviewItemMapper reviewItemMapper,
            CutoverTaskMapper taskMapper, CutoverTaskStageHistoryMapper historyMapper,
            ProjectCutoverServiceManagerPort serviceManagerPort,
            CutoverApprovalRoleCandidatePort roleCandidatePort,
            CutoverApprovalProjectScopePort projectScopePort,
            PlatformCommandExecutionApi commandExecutionApi, LongSupplier currentUserId, Clock clock) {
        this.sourceAssembler = sourceAssembler; this.instanceMapper = instanceMapper; this.nodeMapper = nodeMapper;
        this.notificationMapper = notificationMapper; this.reviewItemMapper = reviewItemMapper;
        this.taskMapper = taskMapper; this.historyMapper = historyMapper;
        this.serviceManagerPort = serviceManagerPort; this.roleCandidatePort = roleCandidatePort;
        this.projectScopePort = projectScopePort; this.commandExecutionApi = commandExecutionApi;
        this.currentUserId = currentUserId; this.clock = clock;
    }

    public CutoverApprovalDecisionResult approve(ApproveCutoverApprovalCommand command) {
        validateDecisionCommand(command.tenantId(), command.taskId(), command.expectedTaskVersion(),
                command.approvalInstanceId(), command.expectedApprovalVersion(), command.reviewItems(),
                command.feedback(), command.idempotencyKey(), command.correlationId());
        require(command.reviewItems().stream().allMatch(item -> "YES".equals(item.decision())),
                INVALID_REQUEST, "DECISION_ACTION_RESULT_MISMATCH");
        return decide(command.tenantId(), command.taskId(), command.expectedTaskVersion(),
                command.approvalInstanceId(), command.expectedApprovalVersion(), command.reviewItems(),
                command.assessmentReview(), command.feedback(), command.idempotencyKey(), command.correlationId(), true);
    }

    public CutoverApprovalDecisionResult reject(RejectCutoverApprovalCommand command) {
        validateDecisionCommand(command.tenantId(), command.taskId(), command.expectedTaskVersion(),
                command.approvalInstanceId(), command.expectedApprovalVersion(), command.reviewItems(),
                command.feedback(), command.idempotencyKey(), command.correlationId());
        boolean anyNo = command.reviewItems().stream().anyMatch(item -> "NO".equals(item.decision()));
        boolean assessmentReject = command.assessmentReview() != null
                && "NOT_REASONABLE".equals(command.assessmentReview().decision());
        require(anyNo || assessmentReject, INVALID_REQUEST, "DECISION_ACTION_RESULT_MISMATCH");
        return decide(command.tenantId(), command.taskId(), command.expectedTaskVersion(),
                command.approvalInstanceId(), command.expectedApprovalVersion(), command.reviewItems(),
                command.assessmentReview(), command.feedback(), command.idempotencyKey(), command.correlationId(), false);
    }

    private CutoverApprovalDecisionResult decide(long tenantId, long taskId, int expectedTaskVersion,
            long approvalInstanceId, int expectedApprovalVersion, List<ReviewItemInput> reviewItems,
            AssessmentReviewInput assessmentReview, String feedback, String idempotencyKey,
            String correlationId, boolean approve) {
        long actorId = currentUserId.getAsLong();
        require(actorId > 0, INVALID_REQUEST, "缺少受信当前用户");
        DecisionBusinessInput digest = new DecisionBusinessInput(tenantId, taskId, expectedTaskVersion,
                approvalInstanceId, expectedApprovalVersion, approve ? "APPROVE" : "REJECT",
                reviewItems, assessmentReview, feedback);
        var execution = commandExecutionApi.execute(new PlatformCommandExecutionApi.IdempotencyScope(tenantId,
                        "CUTOVER_APPROVAL_DECISION:" + approvalInstanceId, actorId, idempotencyKey),
                sha256(JsonUtils.toJsonString(digest)), CutoverApprovalDecisionResult.class,
                () -> decideNew(tenantId, taskId, expectedTaskVersion, approvalInstanceId,
                        expectedApprovalVersion, reviewItems, assessmentReview, feedback,
                        correlationId, actorId, approve),
                result -> decisionSuccessFacts(result, correlationId));
        if (execution.decision() == PlatformCommandExecutionApi.Decision.CONFLICT)
            throw failure(IDEMPOTENCY_CONFLICT, "审批决定幂等载荷冲突");
        if (execution.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS || execution.response() == null)
            throw failure(IDEMPOTENCY_IN_PROGRESS, "审批决定命令处理中");
        if (execution.response().holdReason() != null)
            throw failure(STATE_CONFLICT, "APPROVER_UNAVAILABLE");
        return execution.response();
    }

    private CutoverApprovalDecisionResult decideNew(long tenantId, long taskId, int expectedTaskVersion,
            long approvalInstanceId, int expectedApprovalVersion, List<ReviewItemInput> reviewItems,
            AssessmentReviewInput assessmentReview, String feedback, String correlationId,
            long actorId, boolean approve) {
        require(reviewItemMapper != null && taskMapper != null && historyMapper != null,
                OWNER_DATA_CORRUPTED, "审批决定持久化组件未装配");
        CutoverTaskDO task = taskMapper.selectForUpdate(new CutoverTaskRowQuery(tenantId, taskId));
        require(task != null && "P5".equals(task.getCurrentStage()) && "APPROVING".equals(task.getTaskStatus()),
                STATE_CONFLICT, "任务不在P5审批中");
        require(Objects.equals(task.getVersion(), expectedTaskVersion), VERSION_CONFLICT, "任务版本已变化");
        CutoverApprovalInstanceDO instance = instanceMapper.selectByIdForUpdate(
                new ApprovalInstanceLockQuery(tenantId, approvalInstanceId, null, null));
        require(instance != null && Objects.equals(instance.getTaskId(), taskId), STATE_CONFLICT, "审批实例不存在");
        require("PENDING".equals(instance.getStatusCode()) && instance.getHoldReasonCode() == null,
                STATE_CONFLICT, "审批实例不可决定");
        require(Objects.equals(instance.getVersion(), expectedApprovalVersion), VERSION_CONFLICT, "审批版本已变化");
        CutoverApprovalNodeDO current = nodeMapper.selectByInstanceAndNodeForUpdate(
                new ApprovalNodeLockQuery(tenantId, approvalInstanceId, instance.getCurrentNodeNo()));
        require(current != null && "PENDING".equals(current.getStatusCode())
                && Objects.equals(current.getCurrentApproverUserId(), actorId), STATE_CONFLICT, "当前用户不是待审批人");
        if (!revalidateApprover(instance, current, actorId)) {
            int oldVersion = instance.getVersion();
            instance.setHoldReasonCode("APPROVER_UNAVAILABLE");
            instance.setUpdater(String.valueOf(actorId)); instance.setUpdateTime(LocalDateTime.now(clock));
            require(instanceMapper.updateById(instance) == 1, VERSION_CONFLICT, "审批实例并发变化");
            instance.setVersion(oldVersion + 1);
            return decisionResult(instance, task, "PENDING", task.getVersion(), task.getCurrentStage(), task.getTaskStatus());
        }
        boolean anyNo = reviewItems.stream().anyMatch(item -> "NO".equals(item.decision()));
        validateAssessmentReview(current.getNodeCode(), assessmentReview, approve, anyNo);
        LocalDateTime now = LocalDateTime.now(clock);
        insertReviewItems(tenantId, approvalInstanceId, current.getId(), reviewItems, actorId, now);
        updateNode(current, approve ? "APPROVED" : "REJECTED", assessmentReview, feedback, now, actorId, now);
        if (!approve) return rejectNew(instance, task, current, feedback, correlationId, actorId, now);
        CutoverApprovalNodeDO next = nodeMapper.selectByInstanceAndNodeForUpdate(
                new ApprovalNodeLockQuery(tenantId, approvalInstanceId, current.getNodeNo() + 1));
        if (next != null) return approveIntermediate(instance, task, next, actorId, now);
        return approveFinal(instance, task, correlationId, actorId, now);
    }

    public CutoverApprovalStartResult start(CutoverApprovalStartCommand command) {
        long actorId = currentUserId.getAsLong();
        if (actorId <= 0) throw failure(INVALID_REQUEST, "缺少受信当前用户");
        PlatformCommandExecutionApi.ExecutionResult<CutoverApprovalStartResult> execution = commandExecutionApi.execute(
                new PlatformCommandExecutionApi.IdempotencyScope(command.tenantId(),
                        "CUTOVER_APPROVAL_START:" + command.taskId() + ":" + command.planRevisionId(),
                        actorId, command.idempotencyKey()), startDigest(command),
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
                pauseDigest(command), CutoverApprovalCommandResult.class,
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
        instance.setDecisionAt(null); instance.setUpdater(String.valueOf(actorId)); instance.setUpdateTime(now);
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
        instance.setRouteSnapshot(routeSnapshot(command.grade(), route)); instance.setStatusCode("PENDING");
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
                "treeVersion", initiator.treeVersion())), null));
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
        return new NodeDraft(code, userId, 0, JsonUtils.toJsonString(current),
                userId == null ? "SERVICE_MANAGER_NOT_UNIQUE" : null);
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
        return new NodeDraft(code, selected, selected == null ? 0 : treeVersion, JsonUtils.toJsonString(snapshot),
                selected == null ? "PROJECT_CANDIDATE_NOT_UNIQUE" : null);
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
        row.setStatusCode("PENDING"); row.setRetryCount(0); row.setNextRetryAt(null); row.setVersion(0);
        row.setCreator(String.valueOf(actorId)); row.setUpdater(String.valueOf(actorId));
        row.setCreateTime(now); row.setUpdateTime(now);
        require(notificationMapper.insert(row) == 1, STATE_CONFLICT, "首节点通知创建失败");
    }

    private boolean revalidateApprover(CutoverApprovalInstanceDO instance, CutoverApprovalNodeDO node, long actorId) {
        return switch (node.getNodeCode()) {
            case "INITIATOR" -> {
                var expected = new CutoverApprovalProjectScopePort.ProjectScopeFact(instance.getTenantId(),
                        instance.getProjectId(), actorId, "ACTION_EDIT", true, node.getProjectScopeVersion());
                var locked = projectScopePort.lockAndRevalidate(expected);
                if (!locked.current().allowed()) yield false;
                require(locked.outcome() == CutoverApprovalProjectScopePort.Revalidation.VALID,
                        SOURCE_STALE, "发起人项目范围版本已变化");
                yield true;
            }
            case "SERVICE_MANAGER" -> {
                var expected = JsonUtils.parseObject(node.getCandidateFactSnapshot(),
                        ProjectCutoverServiceManagerPort.ServiceManagerFact.class);
                var locked = serviceManagerPort.lockAndRevalidate(expected);
                if (locked.current().outcome() != ProjectCutoverServiceManagerPort.Outcome.FOUND
                        || !Objects.equals(locked.current().userId(), actorId)) yield false;
                require(locked.outcome() == ProjectCutoverServiceManagerPort.Revalidation.VALID,
                        SOURCE_STALE, "服务经理事实版本已变化");
                yield true;
            }
            case "SECOND_LINE", "RND" -> {
                String group = "SECOND_LINE".equals(node.getNodeCode())
                        ? "CUT_SECOND_LINE_APPROVER" : "CUT_RND_APPROVER";
                FrozenRoleSnapshot snapshot = JsonUtils.parseObject(node.getCandidateFactSnapshot(), FrozenRoleSnapshot.class);
                require(group.equals(snapshot.roleGroupCode()) && snapshot.candidates() != null
                                && snapshot.projectScopes() != null, OWNER_DATA_CORRUPTED, "冻结候选快照损坏");
                var expectedCandidates = new CutoverApprovalRoleCandidatePort.CandidateSet(instance.getTenantId(),
                        group, snapshot.candidates());
                var lockedCandidates = roleCandidatePort.lockAndRevalidate(expectedCandidates);
                require(lockedCandidates.outcome() == CutoverApprovalRoleCandidatePort.Revalidation.VALID,
                        SOURCE_STALE, "审批角色候选事实版本已变化");
                List<Long> allowed = new ArrayList<>();
                for (FrozenProjectScope frozen : snapshot.projectScopes()) {
                    var expected = new CutoverApprovalProjectScopePort.ProjectScopeFact(instance.getTenantId(),
                            instance.getProjectId(), frozen.userId(), "ACTION_VIEW", frozen.allowed(), frozen.treeVersion());
                    var locked = projectScopePort.lockAndRevalidate(expected);
                    require(locked.outcome() == CutoverApprovalProjectScopePort.Revalidation.VALID,
                            SOURCE_STALE, "审批候选项目范围版本已变化");
                    if (locked.current().allowed()) allowed.add(frozen.userId());
                }
                yield allowed.size() == 1 && allowed.getFirst() == actorId;
            }
            default -> throw failure(OWNER_DATA_CORRUPTED, "未知审批节点");
        };
    }

    private void insertReviewItems(long tenantId, long instanceId, long nodeId, List<ReviewItemInput> items,
                                   long actorId, LocalDateTime now) {
        for (ReviewItemInput item : items) {
            CutoverApprovalReviewItemDO row = new CutoverApprovalReviewItemDO();
            row.setId(IDS.nextId()); row.setTenantId(tenantId); row.setApprovalInstanceId(instanceId);
            row.setApprovalNodeId(nodeId); row.setItemCode(item.itemCode()); row.setDecisionCode(item.decision());
            row.setUnreasonableReason(item.unreasonableReason()); row.setCreator(String.valueOf(actorId));
            row.setUpdater(String.valueOf(actorId)); row.setCreateTime(now); row.setUpdateTime(now);
            require(reviewItemMapper.insert(row) == 1, STATE_CONFLICT, "审批评审项创建失败");
        }
    }

    private void updateNode(CutoverApprovalNodeDO node, String status, AssessmentReviewInput assessment,
                            String feedback, LocalDateTime decisionAt, long actorId, LocalDateTime now) {
        require(nodeMapper.updateStatusIfMatch(new ApprovalNodeStatusUpdate(node.getTenantId(), node.getId(),
                node.getVersion(), node.getStatusCode(), status, node.getCurrentApproverUserId(),
                node.getProjectScopeVersion(), assessment == null ? null : assessment.decision(),
                assessment == null ? null : assessment.reason(), feedback, decisionAt, String.valueOf(actorId), now)) == 1,
                VERSION_CONFLICT, "审批节点并发变化");
    }

    private CutoverApprovalDecisionResult approveIntermediate(CutoverApprovalInstanceDO instance,
            CutoverTaskDO task, CutoverApprovalNodeDO next, long actorId, LocalDateTime now) {
        require("WAITING".equals(next.getStatusCode()), STATE_CONFLICT, "下一审批节点状态异常");
        require(nodeMapper.updateStatusIfMatch(new ApprovalNodeStatusUpdate(next.getTenantId(), next.getId(),
                next.getVersion(), "WAITING", "PENDING", next.getCurrentApproverUserId(),
                next.getProjectScopeVersion(), null, null, null, null, String.valueOf(actorId), now)) == 1,
                VERSION_CONFLICT, "下一审批节点并发变化");
        int oldVersion = instance.getVersion();
        instance.setCurrentNodeNo(next.getNodeNo()); instance.setUpdater(String.valueOf(actorId)); instance.setUpdateTime(now);
        require(instanceMapper.updateById(instance) == 1, VERSION_CONFLICT, "审批实例并发变化");
        instance.setVersion(oldVersion + 1);
        insertPendingNotification(instance, next, actorId, now);
        return decisionResult(instance, task, "PENDING", task.getVersion(), task.getCurrentStage(), task.getTaskStatus());
    }

    private CutoverApprovalDecisionResult rejectNew(CutoverApprovalInstanceDO instance, CutoverTaskDO task,
            CutoverApprovalNodeDO current, String feedback, String correlationId, long actorId, LocalDateTime now) {
        List<CutoverApprovalNodeDO> future = nodeMapper.selectList(new LambdaQueryWrapperX<CutoverApprovalNodeDO>()
                .eq(CutoverApprovalNodeDO::getTenantId, instance.getTenantId())
                .eq(CutoverApprovalNodeDO::getApprovalInstanceId, instance.getId())
                .gt(CutoverApprovalNodeDO::getNodeNo, current.getNodeNo())
                .eq(CutoverApprovalNodeDO::getStatusCode, "WAITING")
                .orderByAsc(CutoverApprovalNodeDO::getNodeNo));
        for (CutoverApprovalNodeDO node : future) updateNode(node, "CANCELLED", null, null, null, actorId, now);
        int oldVersion = instance.getVersion();
        instance.setStatusCode("REJECTED"); instance.setDecisionAt(now); instance.setRejectionReason(feedback);
        instance.setUpdater(String.valueOf(actorId)); instance.setUpdateTime(now);
        require(instanceMapper.updateById(instance) == 1, VERSION_CONFLICT, "审批实例并发变化");
        instance.setVersion(oldVersion + 1);
        transitionTask(task, "P4", "PLAN_DRAFTING", "P5_APPROVAL_REJECTED", instance.getId(),
                actorId, correlationId, now);
        return decisionResult(instance, task, "REJECTED", task.getVersion() + 1, "P4", "PLAN_DRAFTING");
    }

    private CutoverApprovalDecisionResult approveFinal(CutoverApprovalInstanceDO instance, CutoverTaskDO task,
            String correlationId, long actorId, LocalDateTime now) {
        int oldVersion = instance.getVersion();
        instance.setStatusCode("APPROVED"); instance.setDecisionAt(now); instance.setRejectionReason(null);
        instance.setUpdater(String.valueOf(actorId)); instance.setUpdateTime(now);
        require(instanceMapper.updateById(instance) == 1, VERSION_CONFLICT, "审批实例并发变化");
        instance.setVersion(oldVersion + 1);
        transitionTask(task, "P6", "CLOSURE_IN_PROGRESS", "P5_APPROVAL_APPROVED", instance.getId(),
                actorId, correlationId, now);
        return decisionResult(instance, task, "APPROVED", task.getVersion() + 1, "P6", "CLOSURE_IN_PROGRESS");
    }

    private void transitionTask(CutoverTaskDO task, String stage, String status, String triggerType,
                                long instanceId, long actorId, String correlationId, LocalDateTime now) {
        require(taskMapper.transitionFromApprovalIfMatch(new CutoverTaskApprovalTransitionUpdate(task.getTenantId(),
                task.getId(), task.getVersion(), stage, status)) == 1, VERSION_CONFLICT, "任务阶段并发变化");
        Integer max = taskMapper.selectMaxStageHistorySequence(new CutoverTaskRowQuery(task.getTenantId(), task.getId()));
        CutoverTaskStageHistoryDO history = new CutoverTaskStageHistoryDO();
        history.setId(IDS.nextId()); history.setTenantId(task.getTenantId()); history.setCutoverTaskId(task.getId());
        history.setSequenceNo((max == null ? 0 : max) + 1); history.setFromStage("P5"); history.setToStage(stage);
        history.setFromStatus("APPROVING"); history.setToStatus(status); history.setTriggerType(triggerType);
        history.setTriggerReferenceId(instanceId); history.setActorId(actorId); history.setCorrelationId(correlationId);
        history.setOccurredAt(now); history.setCreator(String.valueOf(actorId)); history.setCreateTime(now);
        require(historyMapper.insert(history) == 1, STATE_CONFLICT, "审批阶段历史创建失败");
    }

    private void insertPendingNotification(CutoverApprovalInstanceDO instance, CutoverApprovalNodeDO node,
                                           long actorId, LocalDateTime now) {
        CutoverApprovalNotificationDO row = new CutoverApprovalNotificationDO();
        row.setId(IDS.nextId()); row.setTenantId(instance.getTenantId()); row.setApprovalInstanceId(instance.getId());
        row.setApprovalNodeId(node.getId()); row.setRecipientUserId(node.getCurrentApproverUserId());
        row.setDeliveryKey("CUT_APPROVAL:" + instance.getId() + ":" + node.getNodeNo() + ":" + (node.getVersion() + 1));
        row.setTemplateCode("CUT_APPROVAL_PENDING"); row.setStatusCode("PENDING"); row.setRetryCount(0);
        row.setNextRetryAt(null); row.setVersion(0); row.setCreator(String.valueOf(actorId));
        row.setUpdater(String.valueOf(actorId)); row.setCreateTime(now); row.setUpdateTime(now);
        require(notificationMapper.insert(row) == 1, STATE_CONFLICT, "下一节点通知创建失败");
    }

    private PlatformCommandExecutionApi.SuccessFacts decisionSuccessFacts(CutoverApprovalDecisionResult result,
                                                                           String correlationId) {
        List<PlatformCommandExecutionApi.BusinessEvent> events = List.of();
        if ("APPROVED".equals(result.approvalStatus())) {
            String eventId = String.valueOf(IDS.nextId());
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("eventId", eventId); payload.put("tenantId", result.tenantId()); payload.put("taskId", result.taskId());
            payload.put("planRevisionId", result.planRevisionId());
            payload.put("approvalInstanceId", result.approvalInstanceId());
            payload.put("approvalVersion", result.approvalVersion()); payload.put("approvedAt", result.decisionAt());
            payload.put("sourceSnapshotVersion", result.sourceSnapshotVersion());
            payload.put("correlationId", correlationId);
            events = List.of(new PlatformCommandExecutionApi.BusinessEvent(eventId, "CutoverApproved",
                    JsonUtils.toJsonString(payload)));
        }
        return new PlatformCommandExecutionApi.SuccessFacts("CUTOVER_APPROVAL_DECISION", "CutoverApproval",
                String.valueOf(result.approvalInstanceId()), correlationId, JsonUtils.toJsonString(result), events);
    }

    private static CutoverApprovalDecisionResult decisionResult(CutoverApprovalInstanceDO instance, CutoverTaskDO task,
            String status, int taskVersion, String stage, String taskStatus) {
        return new CutoverApprovalDecisionResult(instance.getTenantId(), instance.getId(), instance.getVersion(),
                task.getId(), taskVersion, instance.getPlanRevisionId(), instance.getSourceSnapshotVersion(), status,
                instance.getHoldReasonCode(), instance.getCurrentNodeNo(), stage, taskStatus, instance.getDecisionAt() == null ? null
                : instance.getDecisionAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
    }

    private static void validateDecisionCommand(Long tenantId, Long taskId, Integer expectedTaskVersion,
            Long approvalInstanceId, Integer expectedApprovalVersion, List<ReviewItemInput> items,
            String feedback, String idempotencyKey, String correlationId) {
        require(tenantId != null && tenantId > 0 && taskId != null && taskId > 0
                && approvalInstanceId != null && approvalInstanceId > 0 && expectedTaskVersion != null
                && expectedTaskVersion >= 0 && expectedApprovalVersion != null && expectedApprovalVersion >= 0,
                INVALID_REQUEST, "审批命令身份或版本非法");
        require(nonBlank(idempotencyKey, 128) && nonBlank(correlationId, 128), INVALID_REQUEST, "审批命令头非法");
        require(nonBlank(feedback, 1000), BUSINESS_INCOMPLETE, "FEEDBACK_REQUIRED");
        require(items != null && items.size() == 5, BUSINESS_INCOMPLETE, "REVIEW_ITEMS_INCOMPLETE");
        Set<String> codes = new java.util.LinkedHashSet<>();
        for (int index = 0; index < items.size(); index++) {
            ReviewItemInput item = items.get(index);
            require(item != null && CutoverApprovalRules.REVIEW_ITEM_CODES.get(index).equals(item.itemCode())
                    && List.of("YES", "NO").contains(item.decision()) && codes.add(item.itemCode()),
                    INVALID_REQUEST, "审批评审项非法");
            require("YES".equals(item.decision()) ? item.unreasonableReason() == null
                    : nonBlank(item.unreasonableReason(), 1000), BUSINESS_INCOMPLETE, "NO_REASON_REQUIRED");
        }
    }

    private static void validateAssessmentReview(String nodeCode, AssessmentReviewInput input,
                                                 boolean approve, boolean anyNo) {
        if (!"SERVICE_MANAGER".equals(nodeCode)) {
            require(input == null, INVALID_REQUEST, "非服务经理节点不得复核P2");
            return;
        }
        require(input != null, BUSINESS_INCOMPLETE, "ASSESSMENT_REVIEW_REQUIRED");
        require(List.of("CONFIRMED", "NOT_REASONABLE").contains(input.decision()), INVALID_REQUEST, "复核决定非法");
        require(("CONFIRMED".equals(input.decision()) && input.reason() == null)
                || ("NOT_REASONABLE".equals(input.decision()) && nonBlank(input.reason(), 1000)),
                BUSINESS_INCOMPLETE, "ASSESSMENT_REVIEW_REQUIRED");
        require((approve && "CONFIRMED".equals(input.decision()))
                        || (!approve && (anyNo || "NOT_REASONABLE".equals(input.decision()))), BUSINESS_INCOMPLETE,
                "DECISION_ACTION_RESULT_MISMATCH");
    }

    private static boolean nonBlank(String value, int max) {
        return value != null && value.equals(value.trim()) && !value.isBlank() && value.length() <= max;
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
    private static String startDigest(CutoverApprovalStartCommand command) {
        return sha256(JsonUtils.toJsonString(new StartBusinessInput(command.tenantId(), command.taskId(),
                command.expectedTaskVersion(), command.planRevisionId(), command.planRevisionNo(), command.grade(),
                command.assessmentId(), command.assessmentVersion(), command.checklistId(), command.checklistVersion(),
                command.sourceSnapshotVersion(), command.previousApprovalInstanceId())));
    }
    private static String pauseDigest(CutoverApprovalPauseCommand command) {
        return sha256(JsonUtils.toJsonString(new PauseBusinessInput(command.tenantId(), command.approvalInstanceId(),
                command.expectedApprovalVersion(), command.planRevisionId(), command.expectedSourceSnapshotVersion(),
                command.reasonCode())));
    }
    private static String routeSnapshot(String grade, List<NodeDraft> route) {
        List<RouteNodeSnapshot> nodes = new ArrayList<>();
        for (int index = 0; index < route.size(); index++) {
            NodeDraft node = route.get(index);
            nodes.add(new RouteNodeSnapshot(index + 1, node.nodeCode(), node.approverUserId(), node.treeVersion(),
                    JsonUtils.parseTree(node.candidateSnapshot()), node.unresolvedReason()));
        }
        return JsonUtils.toJsonString(new RouteSnapshot(grade, List.copyOf(nodes)));
    }
    private static void require(boolean condition, CutoverApprovalApplicationException.Code code, String message) {
        if (!condition) throw failure(code, message);
    }
    private static CutoverApprovalApplicationException failure(CutoverApprovalApplicationException.Code code, String message) {
        return new CutoverApprovalApplicationException(code, message);
    }
    private record NodeDraft(String nodeCode, Long approverUserId, long treeVersion, String candidateSnapshot,
                             String unresolvedReason) { }
    private record RouteSnapshot(String grade, List<RouteNodeSnapshot> nodes) { }
    private record RouteNodeSnapshot(int nodeNo, String nodeCode, Long selectedUserId, long projectScopeVersion,
                                     tools.jackson.databind.JsonNode candidateFact, String unresolvedReason) { }
    private record StartBusinessInput(long tenantId, long taskId, int expectedTaskVersion, long planRevisionId,
                                      int planRevisionNo, String grade, long assessmentId, int assessmentVersion,
                                      Long checklistId, Integer checklistVersion, int sourceSnapshotVersion,
                                      Long previousApprovalInstanceId) { }
    private record PauseBusinessInput(long tenantId, long approvalInstanceId, int expectedApprovalVersion,
                                      long planRevisionId, int expectedSourceSnapshotVersion, String reasonCode) { }
    private record DecisionBusinessInput(long tenantId, long taskId, int expectedTaskVersion,
                                         long approvalInstanceId, int expectedApprovalVersion, String action,
                                         List<ReviewItemInput> reviewItems,
                                         AssessmentReviewInput assessmentReview, String feedback) { }
    private record FrozenRoleSnapshot(String roleGroupCode,
                                      List<CutoverApprovalRoleCandidatePort.Candidate> candidates,
                                      List<FrozenProjectScope> projectScopes, Long selectedUserId) { }
    private record FrozenProjectScope(long userId, boolean allowed, long treeVersion) { }
}
