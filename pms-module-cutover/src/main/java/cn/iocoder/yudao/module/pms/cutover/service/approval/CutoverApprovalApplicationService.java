package cn.iocoder.yudao.module.pms.cutover.service.approval;

import jakarta.annotation.Resource;
import cn.iocoder.yudao.module.pms.cutover.service.approval.port.CutoverApprovalProjectScopePort;
import cn.iocoder.yudao.module.pms.cutover.service.task.command.CutoverOperationContext;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query.CutoverApprovalNodesQuery;
import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.cutover.api.approval.dto.*;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.CutoverApprovalInstanceDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.CutoverApprovalNodeDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.CutoverApprovalNotificationDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.CutoverApprovalReviewItemDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.CutoverApprovalReassignmentDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverTaskStageHistoryDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.CutoverApprovalInstanceMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.CutoverApprovalNodeMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.CutoverApprovalNotificationMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.CutoverApprovalReviewItemMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.CutoverApprovalReassignmentMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query.ApprovalNodeLockQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query.ApprovalNodeStatusUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query.ApprovalInstanceStateUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.CutoverTaskMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.CutoverTaskStageHistoryMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.query.CutoverTaskApprovalTransitionUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.query.CutoverTaskRowQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query.ApprovalInstanceLockQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query.ApprovalTaskQuery;
import cn.iocoder.yudao.module.pms.cutover.service.approval.domain.CutoverApprovalRules;
import cn.iocoder.yudao.module.pms.cutover.service.approval.domain.CutoverRoleApprovalSnapshot;
import cn.iocoder.yudao.module.pms.cutover.service.approval.domain.CutoverApprovalNodeFacts;
import cn.iocoder.yudao.module.pms.cutover.service.approval.leadtime.CutoverLeadTimeCalculator;
import cn.iocoder.yudao.module.pms.cutover.service.approval.leadtime.CutoverLeadTimeCompliance;
import cn.iocoder.yudao.module.pms.cutover.service.approval.notification.CutoverExternalNotificationRequestFactory;
import cn.iocoder.yudao.module.pms.cutover.service.approval.command.*;
import cn.iocoder.yudao.module.pms.cutover.service.approval.port.*;
import cn.iocoder.yudao.module.pms.cutover.service.approval.result.CutoverApprovalDecisionResult;
import cn.iocoder.yudao.module.pms.cutover.service.approval.view.CutoverApprovalViews;
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

/** P5生产编排：CUT评审、冻结快照和状态机与平台BPM在同一事务完成。 */
public class CutoverApprovalApplicationService {
    @Resource private cn.iocoder.yudao.module.pms.cutover.service.taskbusiness.CutoverApprovalTaskEvents taskFactEvents;
    private static final Snowflake IDS = IdUtil.getSnowflake();
    private static final CutoverLeadTimeCalculator LEAD_TIME_CALCULATOR = new CutoverLeadTimeCalculator();
    private static final CutoverExternalNotificationRequestFactory EXTERNAL_NOTIFICATION_FACTORY =
            new CutoverExternalNotificationRequestFactory();
    @Resource
    private CutoverApprovalStorage storage;

    private final CutoverApprovalSourceAssembler sourceAssembler;
    private final CutoverApprovalInstanceMapper instanceMapper;
    private final CutoverApprovalNodeMapper nodeMapper;
    private final CutoverApprovalNotificationMapper notificationMapper;
    private final CutoverApprovalReviewItemMapper reviewItemMapper;
    private final CutoverTaskMapper taskMapper;
    private final CutoverTaskStageHistoryMapper historyMapper;
    private final CutoverApprovalReassignmentMapper reassignmentMapper;
    private final ProjectCutoverServiceManagerPort serviceManagerPort;
    private final CutoverApprovalRoleCandidatePort roleCandidatePort;
    private final CutoverApprovalProjectScopePort projectScopePort;
    private final PlatformCommandExecutionApi commandExecutionApi;
    private final LongSupplier currentUserId;
    private final Clock clock;
    private final CutoverWorkflowCoordinator workflow;
    private final cn.iocoder.yudao.module.pms.cutover.service.task.CutoverExecutionReadinessPolicy readiness;



    public CutoverApprovalApplicationService(CutoverApprovalSourceAssembler sourceAssembler,
            CutoverApprovalInstanceMapper instanceMapper, CutoverApprovalNodeMapper nodeMapper,
            CutoverApprovalNotificationMapper notificationMapper, CutoverApprovalReviewItemMapper reviewItemMapper,
            CutoverApprovalReassignmentMapper reassignmentMapper, CutoverTaskMapper taskMapper,
            CutoverTaskStageHistoryMapper historyMapper, ProjectCutoverServiceManagerPort serviceManagerPort,
            CutoverApprovalRoleCandidatePort roleCandidatePort, CutoverApprovalProjectScopePort projectScopePort,
            PlatformCommandExecutionApi commandExecutionApi, LongSupplier currentUserId, Clock clock,
            CutoverWorkflowCoordinator workflow, cn.iocoder.yudao.module.pms.cutover.service.task.CutoverExecutionReadinessPolicy readiness) {
        this.sourceAssembler = sourceAssembler; this.instanceMapper = instanceMapper; this.nodeMapper = nodeMapper;
        this.notificationMapper = notificationMapper; this.reviewItemMapper = reviewItemMapper;
        this.reassignmentMapper = reassignmentMapper; this.taskMapper = taskMapper; this.historyMapper = historyMapper;
        this.serviceManagerPort = serviceManagerPort; this.roleCandidatePort = roleCandidatePort;
        this.projectScopePort = projectScopePort; this.commandExecutionApi = commandExecutionApi;
        this.currentUserId = currentUserId; this.clock = clock; this.workflow = workflow; this.readiness = readiness;
    }

    public CutoverApprovalDecisionResult approve(ApproveCutoverApprovalCommand command) {
        var input = new DecisionBusinessInput(command.tenantId(), command.taskId(), command.approvalInstanceId(), command.expectedTaskVersion(),
                command.expectedApprovalVersion(), "APPROVE", command.reviewItems(), command.assessmentReview(), command.feedback());
        validateDecisionCommand(input, command.idempotencyKey(), command.correlationId());
        require(command.reviewItems().stream().allMatch(item -> "YES".equals(item.decision())),
                BUSINESS_INCOMPLETE, "DECISION_ACTION_RESULT_MISMATCH", "审批动作与评审结果不一致");
        return decide(input, command.idempotencyKey(), command.correlationId());
    }

    public CutoverApprovalDecisionResult reject(RejectCutoverApprovalCommand command) {
        var input = new DecisionBusinessInput(command.tenantId(), command.taskId(), command.approvalInstanceId(), command.expectedTaskVersion(),
                command.expectedApprovalVersion(), "REJECT", command.reviewItems(), command.assessmentReview(), command.feedback());
        validateDecisionCommand(input, command.idempotencyKey(), command.correlationId());
        boolean anyNo = command.reviewItems().stream().anyMatch(item -> "NO".equals(item.decision()));
        boolean assessmentReject = command.assessmentReview() != null
                && "NOT_REASONABLE".equals(command.assessmentReview().decision());
        require(anyNo || assessmentReject, BUSINESS_INCOMPLETE, "DECISION_ACTION_RESULT_MISMATCH",
                "审批动作与评审结果不一致");
        return decide(input, command.idempotencyKey(), command.correlationId());
    }

    public CutoverApprovalViews.ApprovalReassignmentView reassign(ReassignCutoverApprovalCommand command) {
        require(command != null && command.tenantId() != null && command.tenantId() > 0
                        && command.taskId() != null && command.taskId() > 0
                        && command.expectedTaskVersion() != null && command.expectedTaskVersion() >= 0
                        && command.approvalInstanceId() != null && command.approvalInstanceId() > 0
                        && command.expectedApprovalVersion() != null && command.expectedApprovalVersion() >= 0
                        && command.nodeNo() != null && command.nodeNo() > 0
                        && command.newApproverUserId() != null && command.newApproverUserId() > 0,
                INVALID_REQUEST, "REQUEST_SCHEMA_INVALID", "改派命令身份非法");
        require(nonBlank(command.reason(), 1000), BUSINESS_INCOMPLETE, "FEEDBACK_REQUIRED", "改派原因缺失");
        require(nonBlank(command.idempotencyKey(), 128) && nonBlank(command.correlationId(), 128),
                INVALID_REQUEST, "HEADER_REQUIRED_OR_INVALID", "改派命令头非法");
        long actorId = currentUserId.getAsLong();
        require(actorId > 0, INVALID_REQUEST, "REQUEST_SCHEMA_INVALID", "缺少受信当前用户");
        ReassignmentBusinessInput digest = new ReassignmentBusinessInput(command.tenantId(), command.taskId(),
                command.expectedTaskVersion(), command.approvalInstanceId(), command.expectedApprovalVersion(),
                command.nodeNo(), command.newApproverUserId(), command.reason());
        var execution = commandExecutionApi.execute(new PlatformCommandExecutionApi.IdempotencyScope(command.tenantId(),
                        "CUTOVER_APPROVAL_REASSIGN:" + command.approvalInstanceId(), actorId, command.idempotencyKey()),
                sha256(JsonUtils.toJsonString(digest)), CutoverApprovalViews.ApprovalReassignmentView.class,
                () -> reassignNew(command, actorId), result -> new PlatformCommandExecutionApi.SuccessFacts(
                        "CUTOVER_APPROVAL_REASSIGNED", "CUTOVER_APPROVAL", String.valueOf(result.approvalInstanceId()),
                        command.correlationId(), JsonUtils.toJsonString(Map.of("approvalInstanceId", result.approvalInstanceId(),
                        "taskId", result.taskId(), "nodeNo", command.nodeNo(), "newApproverUserId", command.newApproverUserId())),
                        null, null));
        if (execution.decision() == PlatformCommandExecutionApi.Decision.CONFLICT)
            throw failure(IDEMPOTENCY_CONFLICT, "IDEMPOTENCY_PAYLOAD_CONFLICT", "改派幂等载荷冲突");
        if (execution.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS || execution.response() == null)
            throw failure(IDEMPOTENCY_IN_PROGRESS, "IDEMPOTENCY_OPERATION_IN_PROGRESS", "改派命令处理中");
        return execution.response();
    }

    private CutoverApprovalViews.ApprovalReassignmentView reassignNew(ReassignCutoverApprovalCommand command,
                                                                       long actorId) {
        require(reassignmentMapper != null && taskMapper != null, OWNER_DATA_CORRUPTED, "改派持久化组件未装配");
        CutoverTaskDO task = taskMapper.selectForUpdate(new CutoverTaskRowQuery(command.tenantId(), command.taskId()));
        require(task != null && "P5".equals(task.getCurrentStage()) && "APPROVING".equals(task.getTaskStatus()),
                STATE_CONFLICT, "APPROVAL_NOT_PENDING", "任务不在P5审批中");
        if (!Objects.equals(task.getVersion(), command.expectedTaskVersion()))
            throw failure(VERSION_CONFLICT, "TASK_VERSION_STALE", null, null, task.getVersion(), "任务版本已变化");
        CutoverApprovalInstanceDO root = instanceMapper.selectByIdForUpdate(new ApprovalInstanceLockQuery(
                command.tenantId(), command.approvalInstanceId(), null, null));
        require(root != null && Objects.equals(root.getTaskId(), command.taskId()) && "PENDING".equals(root.getStatusCode()),
                STATE_CONFLICT, "APPROVAL_NOT_PENDING", "审批实例不可改派");
        if (!Objects.equals(root.getVersion(), command.expectedApprovalVersion()))
            throw failure(VERSION_CONFLICT, "APPROVAL_VERSION_STALE", null, root.getVersion(), task.getVersion(), "审批版本已变化");
        CutoverApprovalNodeDO node = nodeMapper.selectByInstanceAndNodeForUpdate(new ApprovalNodeLockQuery(
                command.tenantId(), command.approvalInstanceId(), command.nodeNo()));
        require(node != null && List.of("WAITING", "PENDING").contains(node.getStatusCode()),
                STATE_CONFLICT, "REASSIGN_TARGET_NOT_OPEN", "改派目标节点未开放");
        require(!CutoverRoleApprovalSnapshot.isRole(node), STATE_CONFLICT,
                "REASSIGN_TARGET_NOT_OPEN", "角色审批节点按角色办理，不指定人员");
        require(!Objects.equals(node.getCurrentApproverUserId(), command.newApproverUserId()),
                STATE_CONFLICT, "REASSIGN_TARGET_NOT_OPEN", "改派目标未变化");
        ReassignmentCandidate candidate = lockReassignmentCandidate(root, node, command.newApproverUserId());
        workflow.reassign(root, node, command, actorId);
        LocalDateTime now = LocalDateTime.now(clock);
        Long from = node.getCurrentApproverUserId();
        CutoverApprovalNodeFacts.apply(node, candidate.qualification());
        node.setUpdater(String.valueOf(actorId)); node.setUpdateTime(now);
        require(nodeMapper.updateStatusIfMatch(new ApprovalNodeStatusUpdate(node, node.getVersion(), node.getStatusCode())) == 1,
                VERSION_CONFLICT, "审批节点并发变化");
        node.setVersion(node.getVersion() + 1);
        CutoverApprovalReassignmentDO history = new CutoverApprovalReassignmentDO();
        history.setId(IDS.nextId()); history.setTenantId(command.tenantId()); history.setApprovalInstanceId(root.getId());
        history.setApprovalNodeId(node.getId()); history.setReassignmentNo(
                reassignmentMapper.selectMaxReassignmentNo(new ApprovalNodeLockQuery(command.tenantId(), root.getId(), node.getNodeNo())) + 1);
        history.setFromApproverUserId(from); history.setToApproverUserId(command.newApproverUserId());
        history.setReason(command.reason()); CutoverApprovalNodeFacts.apply(history, candidate.qualification());
        history.setOperatedBy(actorId); history.setOperatedAt(now); history.setCreator(String.valueOf(actorId));
        history.setUpdater(String.valueOf(actorId)); history.setCreateTime(now); history.setUpdateTime(now);
        require(reassignmentMapper.insert(history) == 1, STATE_CONFLICT, "改派历史创建失败");
        List<CutoverApprovalNodeDO> openNodes = nodeMapper.open(new CutoverApprovalNodesQuery(command.tenantId(), root.getId()));
        Long oldRootVersion = root.getVersion();
        boolean allOpenNodesAssigned = openNodes.stream().allMatch(value -> value.getCurrentApproverUserId() != null
                || CutoverRoleApprovalSnapshot.isRole(value));
        if (allOpenNodesAssigned && ("ROUTE_CANDIDATE_NOT_UNIQUE".equals(root.getHoldReasonCode())
                || ("APPROVER_UNAVAILABLE".equals(root.getHoldReasonCode())
                && Objects.equals(root.getCurrentNodeNo(), node.getNodeNo())))) root.setHoldReasonCode(null);
        root.setUpdater(String.valueOf(actorId)); root.setUpdateTime(now);
        require(instanceMapper.updateAfterReassignmentIfMatch(new cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query.ApprovalInstanceReassignmentUpdate(
                root.getTenantId(), root.getId(), oldRootVersion, root.getHoldReasonCode(),
                String.valueOf(actorId), now)) == 1, VERSION_CONFLICT, "审批实例并发变化");
        root.setVersion(oldRootVersion + 1);
        if ("PENDING".equals(node.getStatusCode())) insertPendingNotification(root, node, node.getVersion(), new CutoverOperationContext(actorId, command.correlationId(), now));
        return reassignmentView(root, task, openNodes);
    }

    private CutoverApprovalDecisionResult decide(DecisionBusinessInput input, String idempotencyKey,
            String correlationId) {
        long tenantId = input.tenantId(); long taskId = input.taskId();
        long actorId = currentUserId.getAsLong();
        require(actorId > 0, INVALID_REQUEST, "REQUEST_SCHEMA_INVALID", "缺少受信当前用户");
        var execution = commandExecutionApi.execute(new PlatformCommandExecutionApi.IdempotencyScope(tenantId,
                        "CUTOVER_APPROVAL_DECISION:" + taskId, actorId, idempotencyKey),
                sha256(JsonUtils.toJsonString(input)), CutoverApprovalDecisionResult.class,
                () -> decideNew(input, actorId, correlationId),
                result -> decisionSuccessFacts(result, correlationId));
        if (execution.decision() == PlatformCommandExecutionApi.Decision.CONFLICT)
            throw failure(IDEMPOTENCY_CONFLICT, "IDEMPOTENCY_PAYLOAD_CONFLICT", "审批决定幂等载荷冲突");
        if (execution.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS || execution.response() == null)
            throw failure(IDEMPOTENCY_IN_PROGRESS, "IDEMPOTENCY_OPERATION_IN_PROGRESS", "审批决定命令处理中");
        if (execution.response().holdReason() != null)
            throw failure(STATE_CONFLICT, "APPROVER_UNAVAILABLE", "审批人当前不可用");
        return execution.response();
    }

    private CutoverApprovalDecisionResult decideNew(DecisionBusinessInput input, long actorId, String correlationId) {
        long tenantId = input.tenantId(); long taskId = input.taskId();
        Long expectedTaskVersion = input.expectedTaskVersion(); Long expectedApprovalVersion = input.expectedApprovalVersion();
        var reviewItems = input.reviewItems();
        String feedback = input.feedback(); boolean approve = "APPROVE".equals(input.action());
        require(reviewItemMapper != null && taskMapper != null && historyMapper != null,
                OWNER_DATA_CORRUPTED, "审批决定持久化组件未装配");
        CutoverTaskDO task = taskMapper.selectForUpdate(new CutoverTaskRowQuery(tenantId, taskId));
        require(task != null && "P5".equals(task.getCurrentStage()) && "APPROVING".equals(task.getTaskStatus()),
                STATE_CONFLICT, "APPROVAL_NOT_PENDING", "任务不在P5审批中");
        if (!Objects.equals(task.getVersion(), expectedTaskVersion))
            throw failure(VERSION_CONFLICT, "TASK_VERSION_STALE", null, null, task.getVersion(), "任务版本已变化");
        CutoverApprovalInstanceDO currentInstance = instanceMapper.selectCurrentByTask(
                new ApprovalTaskQuery(tenantId, taskId));
        require(currentInstance != null, STATE_CONFLICT, "APPROVAL_NOT_PENDING", "审批实例不存在");
        require(Objects.equals(currentInstance.getId(), input.approvalInstanceId()), STATE_CONFLICT,
                "APPROVAL_NOT_PENDING", "所选审批实例已被替换，请重新进入当前审批");
        CutoverApprovalInstanceDO instance = instanceMapper.selectByIdForUpdate(
                new ApprovalInstanceLockQuery(tenantId, currentInstance.getId(), null, null));
        require(instance != null && Objects.equals(instance.getTaskId(), taskId), STATE_CONFLICT,
                "APPROVAL_NOT_PENDING", "审批实例不存在");
        require("PENDING".equals(instance.getStatusCode()) && instance.getHoldReasonCode() == null,
                STATE_CONFLICT, "APPROVAL_NOT_PENDING", "审批实例不可决定");
        if (!Objects.equals(instance.getVersion(), expectedApprovalVersion))
            throw failure(VERSION_CONFLICT, "APPROVAL_VERSION_STALE", null, instance.getVersion(), task.getVersion(), "审批版本已变化");
        CutoverApprovalNodeDO current = nodeMapper.selectByInstanceAndNodeForUpdate(
                new ApprovalNodeLockQuery(tenantId, instance.getId(), instance.getCurrentNodeNo()));
        boolean byRole = CutoverRoleApprovalSnapshot.isRole(current);
        require(current != null && "PENDING".equals(current.getStatusCode())
                && (byRole || Objects.equals(current.getCurrentApproverUserId(), actorId)), STATE_CONFLICT,
                "NOT_CURRENT_NODE", "当前用户不是待审批人");
        var qualification = revalidateApprover(instance, current, actorId);
        if (!qualification.allowed()) {
            require(!byRole, STATE_CONFLICT, "NOT_CURRENT_NODE", "当前用户不具备本角色审批资格");
            instance.setHoldReasonCode("APPROVER_UNAVAILABLE");
            instance.setUpdater(String.valueOf(actorId)); instance.setUpdateTime(LocalDateTime.now(clock));
            updateInstance(instance);
            return decisionResult(instance, task, current.getNodeNo());
        }
        validateAssessmentReview(current, input);
        LocalDateTime now = LocalDateTime.now(clock);
        var operation = new CutoverOperationContext(actorId, correlationId, now);
        insertReviewItems(current, reviewItems, operation);
        workflow.decide(instance, current, input, actorId);
        updateNode(current, input, new DecisionAuditContext(operation, qualification.roleCandidate()));
        if (!approve) return rejectNew(instance, task, current, input, operation);
        CutoverApprovalNodeDO next = nodeMapper.selectByInstanceAndNodeForUpdate(
                new ApprovalNodeLockQuery(tenantId, instance.getId(), current.getNodeNo() + 1));
        if (next != null) return approveIntermediate(instance, task, current, next, operation);
        readiness.requireCurrent(task);
        return approveFinal(instance, task, current, operation);
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
        workflow.cancel(instance, actorId);
        instance.setStatusCode("PAUSED_SOURCE_INVALIDATED"); instance.setHoldReasonCode(null);
        instance.setDecisionAt(null); instance.setUpdater(String.valueOf(actorId)); instance.setUpdateTime(now);
        updateInstance(instance);
        List<CutoverApprovalNodeDO> nodes = nodeMapper.open(new CutoverApprovalNodesQuery(command.tenantId(), command.approvalInstanceId()));
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
        List<NodeDraft> route = resolveRoute(command, projectId, actorId);
        String hold = route.stream().map(NodeDraft::unresolvedReason).filter(Objects::nonNull).findFirst().orElse(null);
        long instanceId = IDS.nextId();
        LocalDateTime now = LocalDateTime.now(clock);
        CutoverApprovalInstanceDO instance = new CutoverApprovalInstanceDO();
        instance.setId(instanceId); instance.setTenantId(command.tenantId()); instance.setTaskId(command.taskId());
        instance.setProjectId(projectId); instance.setPlanRevisionId(command.planRevisionId());
        instance.setPlanRevisionNo(command.planRevisionNo()); instance.setAssessmentId(command.assessmentId());
        instance.setAssessmentVersion(command.assessmentVersion()); instance.setChecklistId(command.checklistId());
        instance.setChecklistVersion(command.checklistVersion()); instance.setGradeCode(command.grade());
        instance.setInitiatorUserId(actorId); instance.setInitiatorProjectScopeVersion(route.getFirst().treeVersion());
        instance.setSourceSnapshotVersion(command.sourceSnapshotVersion());
        var frozenSource = CutoverApprovalStorage.applySource(instance, source.sourceSnapshot());
        freezeLeadTime(instance, source, command);
        instance.setStatusCode("PENDING");
        instance.setHoldReasonCode(hold); instance.setCurrentNodeNo(1);
        instance.setPreviousApprovalInstanceId(command.previousApprovalInstanceId()); instance.setVersion(0L);
        instance.setCreator(String.valueOf(actorId)); instance.setUpdater(String.valueOf(actorId));
        instance.setCreateTime(now); instance.setUpdateTime(now);
        require(instanceMapper.insert(instance) == 1, STATE_CONFLICT, "审批实例创建失败");
        storage.insertResults(CutoverApprovalStorage.ResultSetWrite.builder().instance(instance).source(frozenSource)
                .actorId(actorId).time(now).build());
        if (command.previousApprovalInstanceId() != null) linkPrevious(command, instanceId);
        CutoverApprovalNodeDO firstNode = null;
        for (int index = 0; index < route.size(); index++) {
            NodeDraft draft = route.get(index);
            CutoverApprovalNodeDO node = new CutoverApprovalNodeDO();
            node.setId(IDS.nextId()); node.setTenantId(command.tenantId()); node.setApprovalInstanceId(instanceId);
            node.setNodeNo(index + 1); node.setNodeCode(draft.nodeCode());
            node.setStatusCode(index == 0 ? "PENDING" : "WAITING");
            node.setOriginalApproverUserId(draft.approverUserId()); node.setCurrentApproverUserId(draft.approverUserId());
            CutoverApprovalNodeFacts.apply(node, draft.qualification());
            node.setVersion(0); node.setCreator(String.valueOf(actorId)); node.setUpdater(String.valueOf(actorId));
            node.setCreateTime(now); node.setUpdateTime(now);
            require(nodeMapper.insert(node) == 1, STATE_CONFLICT, "审批节点创建失败");
            if (index == 0) firstNode = node;
        }
        workflow.start(instance, actorId);
        if (hold == null) insertPendingNotifications(instance, firstNode, 0, new CutoverOperationContext(actorId, command.correlationId(), now), "首节点通知创建失败");
        return new CutoverApprovalStartResult(StartOutcome.STARTED, fact(instance));
    }

    private static void freezeLeadTime(CutoverApprovalInstanceDO instance,
                                       CutoverApprovalSourceAssembler.LockedSource source, CutoverApprovalStartCommand command) {
        String grade = command.grade(); LocalDateTime planSubmittedAt = command.planSubmittedAt();
        if (!List.of("A", "B").contains(grade)) {
            CutoverApprovalStorage.applyLeadTime(instance, null);
            return;
        }
        require(source.task() != null && source.plan() != null && source.task().getScheduledTime() != null,
                OWNER_DATA_CORRUPTED, "提前时间来源事实缺失");
        try {
            CutoverLeadTimeCompliance compliance = LEAD_TIME_CALCULATOR.calculate(grade,
                    source.task().getCutoverType(), source.task().getScheduledTime(), planSubmittedAt);
            CutoverApprovalStorage.applyLeadTime(instance, compliance);
        } catch (IllegalArgumentException exception) {
            throw failure(OWNER_DATA_CORRUPTED, "提前时间来源事实损坏");
        }
    }

    private List<NodeDraft> resolveRoute(CutoverApprovalStartCommand command, long projectId, long actorId) {
        long tenantId = command.tenantId(); String grade = command.grade();
        List<NodeDraft> route = new ArrayList<>();
        CutoverApprovalProjectScopePort.ProjectScopeFact initiator = projectScopePort.inspect(CutoverApprovalProjectScopePort.ScopeQuery.builder()
                .tenantId(tenantId)
                .projectId(projectId)
                .subjectUserId(actorId)
                .requiredAction("ACTION_EDIT")
                .build());
        CutoverApprovalProjectScopePort.ProjectScopeRevalidation lockedInitiator = projectScopePort.lockAndRevalidate(initiator);
        require(initiator.allowed() && lockedInitiator.outcome() == CutoverApprovalProjectScopePort.Revalidation.VALID,
                SOURCE_STALE, "发起人项目范围已变化");
        var initiatorFact = CutoverApprovalNodeFacts.projectScope(initiator);
        initiatorFact.setQualificationCheckTime(LocalDateTime.now(clock));
        route.add(new NodeDraft("INITIATOR", initiatorFact, null));
        for (String code : CutoverApprovalRules.routeFor(grade).subList(1, CutoverApprovalRules.routeFor(grade).size())) {
            route.add("SERVICE_MANAGER".equals(code) ? serviceManager(tenantId, projectId, code)
                    : roleCandidate(tenantId, code));
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
        return new NodeDraft(code, CutoverApprovalNodeFacts.manager(current),
                userId == null ? "SERVICE_MANAGER_NOT_UNIQUE" : null);
    }

    private NodeDraft roleCandidate(long tenantId, String code) {
        String group = "SECOND_LINE".equals(code) ? "probSupporter" : "probRD";
        var reference = roleCandidatePort.inspectRole(new CutoverApprovalRoleCandidatePort.RoleQuery(tenantId, group));
        require(reference != null && reference.tenantId() == tenantId && group.equals(reference.roleGroupCode()),
                OWNER_DATA_CORRUPTED, "审批角色配置不存在或已停用");
        return new NodeDraft(code, CutoverApprovalNodeFacts.role(reference), null);
    }

    private void linkPrevious(CutoverApprovalStartCommand command, long replacementId) {
        CutoverApprovalInstanceDO previous = instanceMapper.selectByIdForUpdate(new ApprovalInstanceLockQuery(
                command.tenantId(), command.previousApprovalInstanceId(), null, null));
        require(previous != null && Objects.equals(previous.getTaskId(), command.taskId())
                        && List.of("REJECTED", "PAUSED_SOURCE_INVALIDATED").contains(previous.getStatusCode())
                        && previous.getReplacementApprovalInstanceId() == null,
                STATE_CONFLICT, "前序审批不可被替代");
        previous.setReplacementApprovalInstanceId(replacementId);
        updateInstance(previous);
    }

    private void insertPendingNotifications(CutoverApprovalInstanceDO instance, CutoverApprovalNodeDO node,
                                            int committedNodeVersion, CutoverOperationContext operation, String failureMessage) {
        long actorId = operation.actorId(); String correlationId = operation.correlationId();
        LocalDateTime now = operation.occurredAt();
        var role = CutoverRoleApprovalSnapshot.from(node);
        boolean byRole = role != null && role.byRole();
        List<Long> recipients = byRole ? roleCandidatePort.inspectCandidates(
                new CutoverApprovalRoleCandidatePort.RoleQuery(instance.getTenantId(), role.roleGroupCode())).candidates().stream()
                .filter(candidate -> candidate.roleGroupId() == role.roleGroupId())
                .map(CutoverApprovalRoleCandidatePort.Candidate::adminUserId).toList()
                : List.of(node.getCurrentApproverUserId());
        for (Long recipient : recipients) {
            String recipientSuffix = byRole ? ":" + recipient : "";
            CutoverApprovalNotificationDO row = new CutoverApprovalNotificationDO();
            row.setId(IDS.nextId()); row.setTenantId(instance.getTenantId()); row.setApprovalInstanceId(instance.getId());
            row.setApprovalNodeId(node.getId()); row.setRecipientUserId(recipient);
            row.setDeliveryKey("CUT_APPROVAL:" + instance.getId() + ":" + node.getNodeNo() + ":" + committedNodeVersion + recipientSuffix);
            row.setCorrelationId(correlationId);
            row.setTemplateCode("CUT_APPROVAL_PENDING");
            row.setChannelCode("IN_PLATFORM"); row.setStatusCode("PENDING"); row.setRetryCount(0);
            row.setNextRetryAt(null); row.setVersion(0);
            row.setCreator(String.valueOf(actorId)); row.setUpdater(String.valueOf(actorId));
            row.setCreateTime(now); row.setUpdateTime(now);
            require(notificationMapper.insert(row) == 1, STATE_CONFLICT, failureMessage);
            // 通知收件人投影不写回角色节点；角色节点办理前仍未指定人员。
            var recipientNode = new CutoverApprovalNodeDO();
            recipientNode.setId(node.getId()); recipientNode.setNodeNo(node.getNodeNo());
            recipientNode.setCurrentApproverUserId(recipient);
            for (CutoverApprovalNotificationDO external :
                    EXTERNAL_NOTIFICATION_FACTORY.createForActivatedNode(instance, recipientNode, committedNodeVersion,
                            correlationId, actorId, now)) {
                external.setId(IDS.nextId());
                external.setDeliveryKey(external.getDeliveryKey() + recipientSuffix);
                require(notificationMapper.insert(external) == 1, STATE_CONFLICT, "外部提醒请求创建失败");
            }
        }
    }

    private ApproverQualification revalidateApprover(CutoverApprovalInstanceDO instance, CutoverApprovalNodeDO node, long actorId) {
        return switch (node.getNodeCode()) {
            case "INITIATOR" -> {
                var expected = new CutoverApprovalProjectScopePort.ProjectScopeFact(instance.getTenantId(),
                        instance.getProjectId(), actorId, "ACTION_EDIT", true, node.getProjectScopeVersion());
                var locked = projectScopePort.lockAndRevalidate(expected);
                if (!locked.current().allowed()) yield ApproverQualification.of(false);
                require(locked.outcome() == CutoverApprovalProjectScopePort.Revalidation.VALID,
                        SOURCE_STALE, "APPROVER_FACT_STALE", "发起人项目范围版本已变化");
                yield ApproverQualification.of(true);
            }
            case "SERVICE_MANAGER" -> {
                var expected = CutoverApprovalNodeFacts.manager(new CutoverApprovalNodeFacts.Source(instance, node));
                var locked = serviceManagerPort.lockAndRevalidate(expected);
                if (locked.current().outcome() != ProjectCutoverServiceManagerPort.Outcome.FOUND
                        || !Objects.equals(locked.current().userId(), actorId)) yield ApproverQualification.of(false);
                require(locked.outcome() == ProjectCutoverServiceManagerPort.Revalidation.VALID,
                        SOURCE_STALE, "APPROVER_FACT_STALE", "服务经理事实版本已变化");
                yield ApproverQualification.of(true);
            }
            case "SECOND_LINE", "RND" -> {
                var role = CutoverRoleApprovalSnapshot.from(node);
                String group = "SECOND_LINE".equals(node.getNodeCode()) ? "probSupporter" : "probRD";
                String personalGroup = "SECOND_LINE".equals(node.getNodeCode()) ? "CUT_SECOND_LINE_APPROVER" : "CUT_RND_APPROVER";
                require(role != null && (group.equals(role.roleGroupCode())
                                || !role.byRole() && personalGroup.equals(role.roleGroupCode())),
                        OWNER_DATA_CORRUPTED, "审批角色引用损坏");
                var query = new CutoverApprovalRoleCandidatePort.ActorQuery(instance.getTenantId(), actorId);
                var actual = role.currentActor(query, roleCandidatePort.lockAndInspectActor(query));
                if (actual == null) yield ApproverQualification.of(false);
                if (!role.byRole()) {
                    var expected = new CutoverApprovalProjectScopePort.ProjectScopeFact(instance.getTenantId(),
                            instance.getProjectId(), actorId, "ACTION_VIEW", true, node.getProjectScopeVersion());
                    var scope = projectScopePort.lockAndRevalidate(expected);
                    if (!scope.current().allowed()) yield ApproverQualification.of(false);
                    require(scope.outcome() == CutoverApprovalProjectScopePort.Revalidation.VALID,
                            SOURCE_STALE, "APPROVER_FACT_STALE", "审批人项目范围版本已变化");
                }
                yield new ApproverQualification(true, actual);
            }
            default -> throw failure(OWNER_DATA_CORRUPTED, "未知审批节点");
        };
    }

    private ReassignmentCandidate lockReassignmentCandidate(CutoverApprovalInstanceDO root,
            CutoverApprovalNodeDO node, long targetUserId) {
        CutoverApprovalNodeDO qualification = switch (node.getNodeCode()) {
            case "INITIATOR" -> {
                var expected = projectScopePort.inspect(CutoverApprovalProjectScopePort.ScopeQuery.builder()
                        .tenantId(root.getTenantId()).projectId(root.getProjectId()).subjectUserId(targetUserId)
                        .requiredAction("ACTION_EDIT").build());
                var locked = projectScopePort.lockAndRevalidate(expected);
                require(locked.outcome() == CutoverApprovalProjectScopePort.Revalidation.VALID,
                        SOURCE_STALE, "APPROVER_FACT_STALE", "改派目标项目范围已变化");
                require(locked.current().allowed(), STATE_CONFLICT, "REASSIGN_TARGET_NOT_OPEN", "改派目标不具备项目编辑范围");
                yield CutoverApprovalNodeFacts.projectScope(locked.current());
            }
            case "SERVICE_MANAGER" -> {
                var expected = serviceManagerPort.inspectCurrent(root.getTenantId(), root.getProjectId(), LocalDateTime.now(clock));
                var locked = serviceManagerPort.lockAndRevalidate(expected);
                require(locked.outcome() == ProjectCutoverServiceManagerPort.Revalidation.VALID,
                        SOURCE_STALE, "APPROVER_FACT_STALE", "服务经理事实已变化");
                require(locked.current().outcome() == ProjectCutoverServiceManagerPort.Outcome.FOUND
                                && Objects.equals(locked.current().userId(), targetUserId),
                        STATE_CONFLICT, "REASSIGN_TARGET_NOT_OPEN", "改派目标不是当前唯一服务经理");
                yield CutoverApprovalNodeFacts.manager(locked.current());
            }
            case "SECOND_LINE", "RND" -> {
                var role = CutoverRoleApprovalSnapshot.from(node);
                require(role != null && !role.byRole(), STATE_CONFLICT, "REASSIGN_TARGET_NOT_OPEN", "角色审批不指定人员");
                var query = new CutoverApprovalRoleCandidatePort.ActorQuery(root.getTenantId(), targetUserId);
                require(role.currentActor(query, roleCandidatePort.lockAndInspectActor(query)) != null,
                        STATE_CONFLICT, "REASSIGN_TARGET_NOT_OPEN", "改派目标不是当前合格候选");
                var expected = projectScopePort.inspect(CutoverApprovalProjectScopePort.ScopeQuery.builder()
                        .tenantId(root.getTenantId()).projectId(root.getProjectId()).subjectUserId(targetUserId)
                        .requiredAction("ACTION_VIEW").build());
                var locked = projectScopePort.lockAndRevalidate(expected);
                require(locked.outcome() == CutoverApprovalProjectScopePort.Revalidation.VALID,
                        SOURCE_STALE, "APPROVER_FACT_STALE", "审批人项目范围版本已变化");
                require(locked.current().allowed(), STATE_CONFLICT, "REASSIGN_TARGET_NOT_OPEN", "改派目标不具备项目范围");
                yield CutoverApprovalNodeFacts.personalRole(CutoverApprovalNodeFacts.PersonalRole.builder()
                        .role(new CutoverApprovalRoleCandidatePort.RoleReference(root.getTenantId(), role.roleGroupId(), role.roleGroupCode()))
                        .scope(locked.current()).build());
            }
            default -> throw failure(OWNER_DATA_CORRUPTED, "未知审批节点");
        };
        qualification.setQualificationCheckTime(LocalDateTime.now(clock));
        return new ReassignmentCandidate(qualification);
    }

    private static CutoverApprovalViews.ApprovalReassignmentView reassignmentView(
            CutoverApprovalInstanceDO root, CutoverTaskDO task, List<CutoverApprovalNodeDO> nodes) {
        List<CutoverApprovalViews.ReassignmentNode> open = nodes.stream()
                .filter(node -> List.of("WAITING", "PENDING").contains(node.getStatusCode()))
                .map(node -> new CutoverApprovalViews.ReassignmentNode(node.getId(), node.getNodeNo(),
                        node.getNodeCode(), node.getStatusCode(), node.getCurrentApproverUserId(), node.getVersion()))
                .toList();
        return new CutoverApprovalViews.ApprovalReassignmentView("REASSIGNMENT_ONLY", root.getId(), root.getVersion(),
                task.getId(), root.getProjectId(), task.getTaskNo(), task.getTaskName(), root.getGradeCode(),
                root.getStatusCode(), root.getHoldReasonCode(), open, List.of("REASSIGN"));
    }

    private void insertReviewItems(CutoverApprovalNodeDO node, List<ReviewItemInput> items,
                                   CutoverOperationContext operation) {
        long actorId = operation.actorId();
        LocalDateTime now = operation.occurredAt();
        long tenantId = node.getTenantId(); long instanceId = node.getApprovalInstanceId(); long nodeId = node.getId();
        for (ReviewItemInput item : items) {
            CutoverApprovalReviewItemDO row = new CutoverApprovalReviewItemDO();
            row.setId(IDS.nextId()); row.setTenantId(tenantId); row.setApprovalInstanceId(instanceId);
            row.setApprovalNodeId(nodeId); row.setItemCode(item.itemCode()); row.setDecisionCode(item.decision());
            row.setUnreasonableReason(item.unreasonableReason()); row.setCreator(String.valueOf(actorId));
            row.setUpdater(String.valueOf(actorId)); row.setCreateTime(now); row.setUpdateTime(now);
            require(reviewItemMapper.insert(row) == 1, STATE_CONFLICT, "审批评审项创建失败");
        }
    }

    private void updateNode(CutoverApprovalNodeDO node, DecisionBusinessInput input, DecisionAuditContext context) {
        var operation = context.operation();
        String expectedStatus = node.getStatusCode();
        node.setStatusCode("APPROVE".equals(input.action()) ? "APPROVED" : "REJECTED");
        if (CutoverRoleApprovalSnapshot.isRole(node)) node.setCurrentApproverUserId(operation.actorId());
        node.setAssessmentReviewDecisionCode(input.assessmentReview() == null ? null : input.assessmentReview().decision());
        node.setAssessmentReviewReason(input.assessmentReview() == null ? null : input.assessmentReview().reason());
        node.setFeedback(input.feedback()); node.setDecisionAt(operation.occurredAt());
        node.setDecisionActorUserId(operation.actorId()); node.setDecisionCheckTime(operation.occurredAt());
        if (context.roleCandidate() != null) {
            node.setDecisionRoleId(context.roleCandidate().roleGroupId());
            node.setDecisionMembershipVersion(context.roleCandidate().roleMembershipVersion());
            node.setDecisionUserStatusVersion(context.roleCandidate().userStatusVersion());
        }
        node.setUpdater(String.valueOf(operation.actorId())); node.setUpdateTime(operation.occurredAt());
        require(nodeMapper.updateStatusIfMatch(new ApprovalNodeStatusUpdate(node, node.getVersion(), expectedStatus)) == 1,
                VERSION_CONFLICT, "审批节点并发变化");
    }

    private void cancelNode(CutoverApprovalNodeDO node, long actorId, LocalDateTime now) {
        String expectedStatus = node.getStatusCode(); node.setStatusCode("CANCELLED");
        node.setUpdater(String.valueOf(actorId)); node.setUpdateTime(now);
        require(nodeMapper.updateStatusIfMatch(new ApprovalNodeStatusUpdate(node, node.getVersion(), expectedStatus)) == 1,
                VERSION_CONFLICT, "审批节点并发变化");
    }

    private CutoverApprovalDecisionResult approveIntermediate(CutoverApprovalInstanceDO instance,
            CutoverTaskDO task, CutoverApprovalNodeDO decidedNode, CutoverApprovalNodeDO next, CutoverOperationContext operation) {
        int decidedNodeNo = decidedNode.getNodeNo();
        long actorId = operation.actorId(); String correlationId = operation.correlationId();
        LocalDateTime now = operation.occurredAt();
        require("WAITING".equals(next.getStatusCode()), STATE_CONFLICT, "下一审批节点状态异常");
        next.setStatusCode("PENDING"); next.setUpdater(String.valueOf(actorId)); next.setUpdateTime(now);
        require(nodeMapper.updateStatusIfMatch(new ApprovalNodeStatusUpdate(next, next.getVersion(), "WAITING")) == 1,
                VERSION_CONFLICT, "下一审批节点并发变化");
        instance.setCurrentNodeNo(next.getNodeNo()); instance.setUpdater(String.valueOf(actorId)); instance.setUpdateTime(now);
        updateInstance(instance);
        insertPendingNotification(instance, next, new CutoverOperationContext(actorId, correlationId, now));
        return decisionResult(instance, task, decidedNodeNo);
    }

    private CutoverApprovalDecisionResult rejectNew(CutoverApprovalInstanceDO instance, CutoverTaskDO task,
            CutoverApprovalNodeDO current, DecisionBusinessInput input, CutoverOperationContext operation) {
        String feedback = input.feedback();
        long actorId = operation.actorId(); String correlationId = operation.correlationId();
        LocalDateTime now = operation.occurredAt();
        List<CutoverApprovalNodeDO> future = nodeMapper.waitingAfter(new CutoverApprovalNodeMapper.FutureNodesQuery(instance.getTenantId(), instance.getId(), current.getNodeNo()));
        for (CutoverApprovalNodeDO node : future) cancelNode(node, actorId, now);
        instance.setStatusCode("REJECTED"); instance.setDecisionAt(now); instance.setRejectionReason(feedback);
        instance.setUpdater(String.valueOf(actorId)); instance.setUpdateTime(now);
        updateInstance(instance);
        transitionTask(task, CutoverTaskStageHistoryDO.builder()
                .toStage("P4")
                .toStatus("PLAN_DRAFTING")
                .triggerType("P5_APPROVAL_REJECTED")
                .triggerReferenceId(instance.getId())
                .actorId(actorId)
                .correlationId(correlationId)
                .occurredAt(now)
                .build());
        return decisionResult(instance, task, current.getNodeNo());
    }

    private CutoverApprovalDecisionResult approveFinal(CutoverApprovalInstanceDO instance, CutoverTaskDO task,
            CutoverApprovalNodeDO decidedNode, CutoverOperationContext operation) {
        int decidedNodeNo = decidedNode.getNodeNo();
        long actorId = operation.actorId(); String correlationId = operation.correlationId();
        LocalDateTime now = operation.occurredAt();
        instance.setStatusCode("APPROVED"); instance.setDecisionAt(now); instance.setRejectionReason(null);
        instance.setUpdater(String.valueOf(actorId)); instance.setUpdateTime(now);
        updateInstance(instance);
        transitionTask(task, CutoverTaskStageHistoryDO.builder()
                .toStage("P6")
                .toStatus("CLOSURE_IN_PROGRESS")
                .triggerType("P5_APPROVAL_APPROVED")
                .triggerReferenceId(instance.getId())
                .actorId(actorId)
                .correlationId(correlationId)
                .occurredAt(now)
                .build());
        taskFactEvents.approved(task);
        return decisionResult(instance, task, decidedNodeNo);
    }

    private void updateInstance(CutoverApprovalInstanceDO instance) {
        Long expectedVersion = instance.getVersion();
        require(instanceMapper.updateStateIfMatch(new ApprovalInstanceStateUpdate(instance.getTenantId(),
                instance.getId(), expectedVersion, instance.getStatusCode(), instance.getCurrentNodeNo(),
                instance.getDecisionAt(), instance.getRejectionReason(), instance.getReplacementApprovalInstanceId(),
                instance.getHoldReasonCode(), instance.getUpdater(), instance.getUpdateTime())) == 1,
                VERSION_CONFLICT, "审批实例并发变化");
        instance.setVersion(expectedVersion + 1);
    }

    private void transitionTask(CutoverTaskDO task, CutoverTaskStageHistoryDO history) {
        require(taskMapper.transitionFromApprovalIfMatch(new CutoverTaskApprovalTransitionUpdate(task.getTenantId(),
                task.getId(), task.getVersion(), history.getToStage(), history.getToStatus())) == 1,
                VERSION_CONFLICT, "任务阶段并发变化");
        Integer max = taskMapper.selectMaxStageHistorySequence(new CutoverTaskRowQuery(task.getTenantId(), task.getId()));
        history.setId(IDS.nextId()); history.setTenantId(task.getTenantId()); history.setCutoverTaskId(task.getId());
        history.setSequenceNo((max == null ? 0 : max) + 1); history.setFromStage("P5");
        history.setFromStatus("APPROVING"); history.setCreator(String.valueOf(history.getActorId()));
        history.setCreateTime(history.getOccurredAt());
        require(historyMapper.insert(history) == 1, STATE_CONFLICT, "审批阶段历史创建失败");
        task.setCurrentStage(history.getToStage()); task.setTaskStatus(history.getToStatus());
        task.setVersion(task.getVersion() + 1);
    }

    private void insertPendingNotification(CutoverApprovalInstanceDO instance, CutoverApprovalNodeDO node,
                                             CutoverOperationContext operation) {
        insertPendingNotification(instance, node, node.getVersion() + 1, operation);
    }

    private void insertPendingNotification(CutoverApprovalInstanceDO instance, CutoverApprovalNodeDO node,
                                             int committedNodeVersion, CutoverOperationContext operation) {
        insertPendingNotifications(instance, node, committedNodeVersion, operation, "下一节点通知创建失败");
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
            int decidedNodeNo) {
        return new CutoverApprovalDecisionResult(instance.getTenantId(), instance.getId(), instance.getVersion(),
                task.getId(), task.getVersion(), instance.getPlanRevisionId(), instance.getSourceSnapshotVersion(), instance.getStatusCode(),
                instance.getHoldReasonCode(), decidedNodeNo, instance.getCurrentNodeNo(), task.getCurrentStage(), task.getTaskStatus(),
                instance.getDecisionAt() == null ? null
                : instance.getDecisionAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
    }

    private static void validateDecisionCommand(DecisionBusinessInput input, String idempotencyKey, String correlationId) {
        Long tenantId = input.tenantId(); Long taskId = input.taskId();
        Long expectedTaskVersion = input.expectedTaskVersion(); Long expectedApprovalVersion = input.expectedApprovalVersion();
        var items = input.reviewItems(); String feedback = input.feedback();
        require(tenantId != null && tenantId > 0 && taskId != null && taskId > 0
                && input.approvalInstanceId() != null && input.approvalInstanceId() > 0
                && expectedTaskVersion != null && expectedTaskVersion >= 0
                && expectedApprovalVersion != null && expectedApprovalVersion >= 0,
                INVALID_REQUEST, "REQUEST_SCHEMA_INVALID", "审批命令身份或版本非法");
        require(nonBlank(idempotencyKey, 128) && nonBlank(correlationId, 128), INVALID_REQUEST,
                "HEADER_REQUIRED_OR_INVALID", "审批命令头非法");
        require(nonBlank(feedback, 1000), BUSINESS_INCOMPLETE, "FEEDBACK_REQUIRED", "审批反馈缺失");
        require(items != null && items.size() == 5, BUSINESS_INCOMPLETE,
                "REVIEW_ITEMS_INCOMPLETE", "审批评审项不完整");
        Set<String> codes = new java.util.LinkedHashSet<>();
        for (int index = 0; index < items.size(); index++) {
            ReviewItemInput item = items.get(index);
            require(item != null && CutoverApprovalRules.REVIEW_ITEM_CODES.get(index).equals(item.itemCode())
                    && List.of("YES", "NO").contains(item.decision()) && codes.add(item.itemCode()),
                    INVALID_REQUEST, "REQUEST_SCHEMA_INVALID", "审批评审项非法");
            require("YES".equals(item.decision()) ? item.unreasonableReason() == null
                    : nonBlank(item.unreasonableReason(), 1000), BUSINESS_INCOMPLETE,
                    "NO_REASON_REQUIRED", "否决项必须填写原因");
        }
    }

    private static void validateAssessmentReview(CutoverApprovalNodeDO node, DecisionBusinessInput decision) {
        String nodeCode = node.getNodeCode();
        AssessmentReviewInput input = decision.assessmentReview();
        boolean approve = "APPROVE".equals(decision.action());
        boolean anyNo = decision.reviewItems().stream().anyMatch(item -> "NO".equals(item.decision()));
        if (!"SERVICE_MANAGER".equals(nodeCode)) {
            require(input == null, INVALID_REQUEST, "REQUEST_SCHEMA_INVALID", "非服务经理节点不得复核P2");
            return;
        }
        require(input != null, BUSINESS_INCOMPLETE, "ASSESSMENT_REVIEW_REQUIRED", "服务经理必须复核P2");
        require(List.of("CONFIRMED", "NOT_REASONABLE").contains(input.decision()), INVALID_REQUEST,
                "REQUEST_SCHEMA_INVALID", "复核决定非法");
        require(("CONFIRMED".equals(input.decision()) && input.reason() == null)
                || ("NOT_REASONABLE".equals(input.decision()) && nonBlank(input.reason(), 1000)),
                BUSINESS_INCOMPLETE, "ASSESSMENT_REVIEW_REQUIRED", "P2复核原因不完整");
        require((approve && "CONFIRMED".equals(input.decision()))
                        || (!approve && (anyNo || "NOT_REASONABLE".equals(input.decision()))), BUSINESS_INCOMPLETE,
                "DECISION_ACTION_RESULT_MISMATCH", "审批动作与复核结果不一致");
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
                command.sourceSnapshotVersion(), command.planSubmittedAt(), command.previousApprovalInstanceId())));
    }
    private static String pauseDigest(CutoverApprovalPauseCommand command) {
        return sha256(JsonUtils.toJsonString(new PauseBusinessInput(command.tenantId(), command.approvalInstanceId(),
                command.expectedApprovalVersion(), command.planRevisionId(), command.expectedSourceSnapshotVersion(),
                command.reasonCode())));
    }


    private static void require(boolean condition, CutoverApprovalApplicationException.Code code, String message) {
        if (!condition) throw failure(code, message);
    }
    private static void require(boolean condition, CutoverApprovalApplicationException.Code code,
                                String reasonCode, String message) {
        if (!condition) throw failure(code, reasonCode, message);
    }
    private static CutoverApprovalApplicationException failure(CutoverApprovalApplicationException.Code code, String message) {
        return new CutoverApprovalApplicationException(code, message);
    }
    private static CutoverApprovalApplicationException failure(CutoverApprovalApplicationException.Code code,
                                                                 String reasonCode, String message) {
        return new CutoverApprovalApplicationException(code, reasonCode, null, null, null, message);
    }
    private static CutoverApprovalApplicationException failure(CutoverApprovalApplicationException.Code code,
            String reasonCode, String ownerContext, Long currentApprovalVersion, Long currentTaskVersion,
            String message) {
        return new CutoverApprovalApplicationException(code, reasonCode, ownerContext,
                currentApprovalVersion, currentTaskVersion, message);
    }
    private record ApproverQualification(boolean allowed, CutoverApprovalRoleCandidatePort.Candidate roleCandidate) {
        static ApproverQualification of(boolean allowed) { return new ApproverQualification(allowed, null); }
    }
    private record DecisionAuditContext(CutoverOperationContext operation,
                                       CutoverApprovalRoleCandidatePort.Candidate roleCandidate) { }
    private record NodeDraft(String nodeCode, CutoverApprovalNodeDO qualification, String unresolvedReason) {
        Long approverUserId() { return qualification.getCurrentApproverUserId(); }
        long treeVersion() { return qualification.getProjectScopeVersion(); }
    }
    private record StartBusinessInput(long tenantId, long taskId, Long expectedTaskVersion, long planRevisionId,
                                      int planRevisionNo, String grade, long assessmentId, Long assessmentVersion,
                                      Long checklistId, Long checklistVersion, int sourceSnapshotVersion,
                                      LocalDateTime planSubmittedAt, Long previousApprovalInstanceId) { }
    private record PauseBusinessInput(long tenantId, long approvalInstanceId, Long expectedApprovalVersion,
                                      long planRevisionId, int expectedSourceSnapshotVersion, String reasonCode) { }
    record DecisionBusinessInput(Long tenantId, Long taskId, Long approvalInstanceId, Long expectedTaskVersion,
                                         Long expectedApprovalVersion, String action,
                                         List<ReviewItemInput> reviewItems,
                                         AssessmentReviewInput assessmentReview, String feedback) { }
    private record ReassignmentBusinessInput(long tenantId, long taskId, Long expectedTaskVersion,
                                             long approvalInstanceId, Long expectedApprovalVersion,
                                             int nodeNo, long newApproverUserId, String reason) { }
    private record ReassignmentCandidate(CutoverApprovalNodeDO qualification) { }
}
