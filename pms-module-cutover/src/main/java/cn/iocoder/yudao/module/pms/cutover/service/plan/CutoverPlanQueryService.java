package cn.iocoder.yudao.module.pms.cutover.service.plan;

import jakarta.annotation.Resource;
import cn.iocoder.yudao.module.pms.cutover.service.plan.context.CutoverPlanContext;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.cutover.api.approval.CutoverApprovalFactApi;
import cn.iocoder.yudao.module.pms.cutover.api.approval.CutoverApprovalFactException;
import cn.iocoder.yudao.module.pms.cutover.api.approval.dto.ApprovalStatus;
import cn.iocoder.yudao.module.pms.cutover.api.approval.dto.CutoverApprovalFact;
import cn.iocoder.yudao.module.pms.cutover.api.approval.dto.CutoverApprovalFactQuery;
import cn.iocoder.yudao.module.pms.cutover.api.approval.dto.CutoverApprovalInspectResult;
import cn.iocoder.yudao.module.pms.cutover.api.approval.dto.InspectStatus;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.plan.CutoverPlanRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.plan.CutoverPlanRevisionMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.plan.CutoverPlanStepMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.plan.CutoverSupportArrangementMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.plan.query.CutoverPlanChildrenQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.plan.query.CutoverPlanHistoryQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.plan.query.CutoverPlanRevisionQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.plan.query.CutoverPlanSuccessorQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.CutoverTaskMapper;
import cn.iocoder.yudao.module.pms.cutover.service.plan.domain.CutoverPlanContentCodec;
import cn.iocoder.yudao.module.pms.cutover.service.plan.port.CutoverPlanSourcePort;
import cn.iocoder.yudao.module.pms.cutover.service.plan.view.CutoverPlanView;
import cn.iocoder.yudao.module.pms.cutover.service.dashboard.model.CutoverDashboardCandidate;
import cn.iocoder.yudao.module.pms.cutover.service.dashboard.policy.CutoverP4ActionPolicy;
import cn.iocoder.yudao.module.pms.cutover.service.task.domain.CutoverTaskRules;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverProjectScopePort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.module.pms.cutover.service.plan.CutoverPlanApplicationException.Code.NOT_FOUND;

/** P4 方案运行查询，保留历史版本与批准事实。 */
public class CutoverPlanQueryService {
    private static final CutoverP4ActionPolicy ACTION_POLICY = new CutoverP4ActionPolicy();
    @Resource
    private CutoverPlanStorage storage;

    private final CutoverTaskMapper taskMapper;
    private final CutoverPlanRevisionMapper planMapper;
    private final CutoverPlanStepMapper stepMapper;
    private final CutoverSupportArrangementMapper supportMapper;
    private final CutoverProjectScopePort projectScopePort;
    private final CutoverPlanSourcePort sourcePort;
    private final CutoverApprovalFactApi approvalFactApi;
    private final CutoverPlanContentCodec codec;

    public CutoverPlanQueryService(CutoverTaskMapper taskMapper, CutoverPlanRevisionMapper planMapper,
                                   CutoverPlanStepMapper stepMapper, CutoverSupportArrangementMapper supportMapper,
                                   CutoverProjectScopePort projectScopePort, CutoverPlanSourcePort sourcePort,
                                   CutoverApprovalFactApi approvalFactApi, CutoverPlanContentCodec codec) {
        this.taskMapper = taskMapper; this.planMapper = planMapper; this.stepMapper = stepMapper;
        this.supportMapper = supportMapper; this.projectScopePort = projectScopePort;
        this.sourcePort = sourcePort; this.approvalFactApi = approvalFactApi; this.codec = codec;
    }

    public CutoverPlanView detail(
            CutoverPlanContext context,
            Long taskId) {
        Long tenantId = context.tenantId(); Long actorId = context.actorId();
        CutoverTaskDO task = taskMapper.selectById(taskId);
        if (task == null || !Objects.equals(task.getTenantId(), tenantId)) throw notFound();
        CutoverProjectScopePort.ProjectScopeFact scope = projectScopePort.inspect(actorId, task.getProjectId(), "ACTION_VIEW");
        if (scope == null || !scope.allowed()) throw notFound();
        CutoverPlanRevisionDO plan = selectReadablePlan(tenantId, task);
        boolean editAllowed = inspectEditAllowed(actorId, task);
        CutoverApprovalFact approval = inspectApprovalFact(tenantId, plan);
        List<String> actions = allowedActions(context, task, plan, approval, editAllowed);
        if (plan == null) return new CutoverPlanView(taskId, task.getCurrentStage(), task.getVersion(), null, null, null, null, null, null, null, null, null, null, actions);
        JsonNode sourceSnapshot = parseSourceSnapshot(plan);
        JsonNode content = assemble(tenantId, plan);
        return new CutoverPlanView(taskId, task.getCurrentStage(), task.getVersion(), plan.getId(), plan.getRevisionNo(), plan.getVersion(), plan.getOriginCode(), plan.getStatusCode(), plan.getSourcePlanRevisionId(), plan.getRevisionReasonCode(), sourceSnapshot, content, approvalView(approval), actions);
    }

    private CutoverPlanRevisionDO selectReadablePlan(Long tenantId, CutoverTaskDO task) {
        CutoverPlanRevisionQuery query = new CutoverPlanRevisionQuery(tenantId, task.getId(), null);
            CutoverPlanRevisionDO current = planMapper.selectCurrent(query);
            if (current != null || !CutoverTaskRules.STAGE_P4.equals(task.getCurrentStage())
                    || !CutoverTaskRules.STATUS_PLAN_DRAFTING.equals(task.getTaskStatus())) return current;
            List<CutoverPlanRevisionDO> history = planMapper.selectListHistory(
                    new CutoverPlanHistoryQuery(tenantId, task.getId()));
            if (history == null) return null;
            return history.reversed().stream().filter(plan -> "INVALIDATED".equals(plan.getStatusCode())
                            && plan.getCurrentMarker() == null && positive(plan.getApprovalInstanceId())
                            && !hasDirectSuccessor(tenantId, task.getId(), plan.getId()))
                    .findFirst().orElse(null);
    }

    private boolean inspectEditAllowed(Long actorId, CutoverTaskDO task) {
        try {
            CutoverProjectScopePort.ProjectScopeFact fact = projectScopePort.inspect(actorId, task.getProjectId(), "ACTION_EDIT");
            return fact != null && fact.allowed();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private JsonNode assemble(Long tenantId, CutoverPlanRevisionDO plan) {
        if ("FULL_FILE_UPLOAD".equals(plan.getEditModeCode())) {
            ObjectNode root = JsonUtils.getObjectMapper().createObjectNode(); root.put("editMode", plan.getEditModeCode());
            ObjectNode file = root.putObject("fileArtifactFact"); putWireLong(file, "artifactId", plan.getFileArtifactId());
            file.put("versionNo", plan.getFileVersionNo()); file.put("referenceKey", plan.getFileReferenceKey());
            file.set("fileFactVersion", JsonUtils.getObjectMapper().valueToTree(CutoverPlanStorage.fileVersion(plan)));
            putWireLong(file, "scopeVersion", plan.getFileScopeVersion()); file.put("sha256", plan.getFileSha256());
            root.put("ownershipConfirmed", Boolean.TRUE.equals(plan.getOwnershipConfirmed())); return root;
        }
        ObjectNode root = codec.assembleStored(new CutoverPlanContentCodec.StoredPlanContent(storage.content(plan),
                stepMapper.selectListByPlan(new CutoverPlanChildrenQuery(tenantId, plan.getId()))));
        if ("ONLINE_TEMPLATE_STANDARD".equals(plan.getEditModeCode())) {
            root.remove("customerContactReference");
            ArrayNode support = root.putArray("supportArrangements");
            supportMapper.selectListByPlan(new CutoverPlanChildrenQuery(tenantId, plan.getId())).stream()
                    .map(storage::currentSupport).forEach(value -> support.add(CutoverPlanStorage.supportContent(value)));
        }
        return root;
    }

    private List<String> allowedActions(CutoverPlanContext context, CutoverTaskDO task,
                                        CutoverPlanRevisionDO plan, CutoverApprovalFact approval, boolean editAllowed) {
        Long tenantId = context.tenantId(); Long actorId = context.actorId();
        boolean ownerP4 = CutoverTaskRules.ORIGIN_NEW_PLATFORM.equals(task.getTaskOrigin())
                && CutoverTaskRules.STAGE_P4.equals(task.getCurrentStage())
                && CutoverTaskRules.STATUS_PLAN_DRAFTING.equals(task.getTaskStatus())
                && Objects.equals(task.getOwnerUserId(), actorId);
        boolean draftActionRequested = ownerP4 && editAllowed && plan != null
                && "DRAFT".equals(plan.getStatusCode()) && (context.canSave() || context.canSubmit());
        boolean comparable = draftActionRequested && comparableSource(context, task, plan);
        boolean contentComplete = comparable && context.canSubmit() && approvalFactApi != null && complete(tenantId, plan);
        boolean revisable = ownerP4 && editAllowed && plan != null && context.canSave() && approval != null
                && canRevise(context, task, plan, approval);
        CutoverDashboardCandidate candidate = new CutoverDashboardCandidate(task.getId(), task.getTaskOrigin(),
                task.getCurrentStage(), task.getTaskStatus(), task.getOwnerUserId(), actorId, task.getManualGrade());
        var facts = cn.iocoder.yudao.module.pms.cutover.service.dashboard.model.CutoverDashboardActionFacts.ActionFacts
                .p4(plan == null ? null : plan.getStatusCode(), plan == null ? null : plan.getCurrentMarker(),
                        approval == null || approval.status() == null ? null : approval.status().name(), editAllowed,
                        comparable, contentComplete, revisable);
        var permissions = cn.iocoder.yudao.module.pms.cutover.service.dashboard.model.CutoverDashboardActionFacts.PermissionFacts
                .p4(context.canCreate(), context.canSave(), context.canSubmit());
        List<String> actions = new ArrayList<>(ACTION_POLICY.allowedActions(candidate, facts, permissions));
        if (plan != null && "DRAFT".equals(plan.getStatusCode()) && context.canDownload()) actions.add("DOWNLOAD_DRAFT");
        return List.copyOf(actions);
    }

    private boolean canRevise(CutoverPlanContext context, CutoverTaskDO task, CutoverPlanRevisionDO plan,
                              CutoverApprovalFact approval) {
        Long tenantId = context.tenantId(); Long actorId = context.actorId();
        if (!positive(plan.getApprovalInstanceId()) || hasDirectSuccessor(tenantId, task.getId(), plan.getId())) {
            return false;
        }
        if ("SUBMITTED".equals(plan.getStatusCode()) && Objects.equals(plan.getCurrentMarker(), 1)) {
            return approval.status() == ApprovalStatus.REJECTED;
        }
        return "INVALIDATED".equals(plan.getStatusCode()) && plan.getCurrentMarker() == null
                && approval.status() == ApprovalStatus.PAUSED_SOURCE_INVALIDATED
                && sourceChanged(context, task, plan);
    }

    private boolean complete(Long tenantId, CutoverPlanRevisionDO plan) {
        try {
            CutoverPlanSourcePort.SourceSnapshot snapshot = storage.source(plan);
            CutoverPlanSourcePort.SourceFacts facts = new CutoverPlanSourcePort.SourceFacts(
                    snapshot, snapshot.failedRiskFacts());
            CutoverPlanContentCodec.DecodedContent content = codec.decodeWritable(assemble(tenantId, plan), facts);
            codec.validateComplete(content, facts);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private boolean sourceChanged(CutoverPlanContext context, CutoverTaskDO task, CutoverPlanRevisionDO plan) {
        Long tenantId = context.tenantId(); Long actorId = context.actorId();
        try {
            CutoverPlanSourcePort.SourceFacts current = sourcePort.inspect(tenantId, actorId, task.getId());
            CutoverPlanSourcePort.SourceSnapshot frozen = storage.source(plan);
            return current != null && !current.snapshot().equals(frozen);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private boolean hasDirectSuccessor(Long tenantId, Long taskId, Long planRevisionId) {
        List<CutoverPlanRevisionDO> successors = planMapper.selectListDirectSuccessors(
                new CutoverPlanSuccessorQuery(tenantId, taskId, planRevisionId));
        return successors != null && !successors.isEmpty();
    }

    private CutoverApprovalFact inspectApprovalFact(Long tenantId, CutoverPlanRevisionDO plan) {
        if (plan == null || !positive(plan.getApprovalInstanceId())) return null;
        if (approvalFactApi == null) throw cut05Unavailable();
        try {
            CutoverApprovalInspectResult result = approvalFactApi.inspect(new CutoverApprovalFactQuery(
                    tenantId, plan.getCutoverTaskId(), plan.getId()));
            CutoverApprovalFact fact = result == null || result.status() != InspectStatus.FOUND ? null : result.fact();
            requireApprovalIdentity(plan, fact);
            return fact;
        } catch (CutoverApprovalFactException ex) {
            if (ex.code() == CutoverApprovalFactException.Code.PROVIDER_UNAVAILABLE) {
                throw cut05Unavailable();
            }
            throw corrupted("CUT-05审批事实损坏");
        } catch (CutoverPlanApplicationException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw corrupted("CUT-05审批事实损坏");
        }
    }

    private void requireApprovalIdentity(CutoverPlanRevisionDO plan, CutoverApprovalFact fact) {
        CutoverPlanSourcePort.SourceSnapshot source = storage.source(plan);
        if (fact == null || !Objects.equals(fact.approvalInstanceId(), plan.getApprovalInstanceId())
                || !Objects.equals(fact.taskId(), plan.getCutoverTaskId())
                || !Objects.equals(fact.planRevisionId(), plan.getId())
                || !Objects.equals(fact.planRevisionNo(), plan.getRevisionNo())
                || !Objects.equals(fact.sourceSnapshotVersion(), source.snapshotVersion())) {
            throw corrupted("CUT-05审批事实身份损坏");
        }
    }

    private static CutoverPlanView.ApprovalFactView approvalView(CutoverApprovalFact fact) {
        return fact == null ? null : new CutoverPlanView.ApprovalFactView(fact.approvalInstanceId(),
                fact.approvalVersion(), fact.status().name(), fact.decisionAt(), fact.rejectionReason());
    }

    private JsonNode parseSourceSnapshot(CutoverPlanRevisionDO plan) {
        try {
            return storage.sourceView(plan);
        } catch (CutoverPlanApplicationException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw corrupted("方案来源快照损坏");
        }
    }

    private boolean comparableSource(CutoverPlanContext context, CutoverTaskDO task, CutoverPlanRevisionDO plan) {
        Long tenantId = context.tenantId(); Long actorId = context.actorId();
        try {
            CutoverPlanSourcePort.SourceFacts current = sourcePort.inspect(tenantId, actorId, task.getId());
            CutoverPlanSourcePort.SourceSnapshot frozen = storage.source(plan);
            return current != null && current.snapshot().equals(frozen);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static void putWireLong(ObjectNode node, String field, Long value) {
        if (value > -9_007_199_254_740_991L && value < 9_007_199_254_740_991L) node.put(field, value);
        else node.put(field, Long.toString(value));
    }

    private static CutoverPlanApplicationException notFound() {
        return new CutoverPlanApplicationException(NOT_FOUND, "方案不可见");
    }

    private static CutoverPlanApplicationException corrupted(String message) {
        return new CutoverPlanApplicationException(
                CutoverPlanApplicationException.Code.OWNER_DATA_CORRUPTED, message);
    }

    private static CutoverPlanApplicationException cut05Unavailable() {
        return new CutoverPlanApplicationException(CutoverPlanApplicationException.Code.OWNER_PROVIDER_UNAVAILABLE,
                "CUT05_PROVIDER_UNAVAILABLE", "CUT", null, null, null, "CUT-05审批Provider不可用");
    }

    private static boolean positive(Long value) { return value != null && value > 0; }

}
