package cn.iocoder.yudao.module.pms.cutover.service.plan;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.cutover.api.approval.CutoverApprovalFactApi;
import cn.iocoder.yudao.module.pms.cutover.api.approval.CutoverApprovalFactException;
import cn.iocoder.yudao.module.pms.cutover.api.approval.dto.ApprovalStatus;
import cn.iocoder.yudao.module.pms.cutover.api.approval.dto.CutoverApprovalFact;
import cn.iocoder.yudao.module.pms.cutover.api.approval.dto.CutoverApprovalFactQuery;
import cn.iocoder.yudao.module.pms.cutover.api.approval.dto.CutoverApprovalInspectResult;
import cn.iocoder.yudao.module.pms.cutover.api.approval.dto.InspectStatus;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.planv2.CutoverPlanRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.planv2.CutoverPlanStepDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.planv2.CutoverSupportArrangementDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.CutoverPlanRevisionMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.CutoverPlanStepMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.CutoverSupportArrangementMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.query.CutoverPlanChildrenQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.query.CutoverPlanHistoryQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.query.CutoverPlanRevisionQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.query.CutoverPlanSuccessorQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskMapper;
import cn.iocoder.yudao.module.pms.cutover.service.plan.domain.CutoverPlanContentCodec;
import cn.iocoder.yudao.module.pms.cutover.service.plan.port.CutoverPlanSourcePort;
import cn.iocoder.yudao.module.pms.cutover.service.plan.view.CutoverPlanView;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.domain.CutoverTaskRules;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverProjectScopePort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.module.pms.cutover.service.plan.CutoverPlanApplicationException.Code.NOT_FOUND;

/** Task 4只读详情内核；不注册生产Bean。 */
public class CutoverPlanQueryService {
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

    public CutoverPlanView detail(Long tenantId, Long actorId, Long taskId, PlanAccess access) {
        CutoverTaskDO task = taskMapper.selectById(taskId);
        if (task == null || !Objects.equals(task.getTenantId(), tenantId)) throw notFound();
        CutoverProjectScopePort.ProjectScopeFact scope = projectScopePort.inspect(actorId, task.getProjectId(), "ACTION_VIEW");
        if (scope == null || !scope.allowed()) throw notFound();
        CutoverPlanRevisionDO plan = selectReadablePlan(tenantId, task);
        boolean editAllowed = inspectEditAllowed(actorId, task);
        CutoverApprovalFact approval = inspectApprovalFact(tenantId, plan);
        List<String> actions = allowedActions(tenantId, actorId, task, plan, approval, access, editAllowed);
        if (plan == null) return new CutoverPlanView(taskId, task.getCurrentStage(), task.getVersion(),
                null, null, null, null, null, null, null, null, null,
                null, null, null, actions);
        boolean legacy = "LEGACY_FORWARD".equals(plan.getOriginCode());
        JsonNode sourceSnapshot = parseSourceSnapshot(plan);
        JsonNode content = assemble(tenantId, plan);
        return new CutoverPlanView(taskId, legacy ? null : task.getCurrentStage(), task.getVersion(),
                plan.getId(), plan.getRevisionNo(), plan.getVersion(), plan.getOriginCode(),
                legacy ? "LEGACY_READ_ONLY" : plan.getStatusCode(), plan.getLegacyPlanId(),
                plan.getLegacyStatusRaw(), plan.getSourcePlanRevisionId(), plan.getRevisionReasonCode(),
                sourceSnapshot, content, approvalView(approval), actions);
    }

    private CutoverPlanRevisionDO selectReadablePlan(Long tenantId, CutoverTaskDO task) {
        CutoverPlanRevisionQuery query = new CutoverPlanRevisionQuery(tenantId, task.getId(), null);
        if (!"LEGACY_FORWARD".equals(task.getTaskOrigin())) {
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
        List<CutoverPlanRevisionDO> legacy = planMapper.selectListLegacyByTask(query);
        if (legacy.size() > 1) throw corrupted("legacy方案身份不唯一");
        return legacy.isEmpty() ? null : legacy.getFirst();
    }

    private boolean inspectEditAllowed(Long actorId, CutoverTaskDO task) {
        if ("LEGACY_FORWARD".equals(task.getTaskOrigin())) return false;
        try {
            CutoverProjectScopePort.ProjectScopeFact fact = projectScopePort.inspect(actorId, task.getProjectId(), "ACTION_EDIT");
            return fact != null && fact.allowed();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private JsonNode assemble(Long tenantId, CutoverPlanRevisionDO plan) {
        if ("LEGACY_FORWARD".equals(plan.getOriginCode())) {
            return codec.assembleLegacy(stepMapper.selectListByPlan(new CutoverPlanChildrenQuery(tenantId, plan.getId()))
                    .stream().map(this::step).toList());
        }
        if ("FULL_FILE_UPLOAD".equals(plan.getEditModeCode())) {
            ObjectNode root = JsonUtils.getObjectMapper().createObjectNode(); root.put("editMode", plan.getEditModeCode());
            ObjectNode file = root.putObject("fileArtifactFact"); putWireLong(file, "artifactId", plan.getFileArtifactId());
            file.put("versionNo", plan.getFileVersionNo()); file.put("referenceKey", plan.getFileReferenceKey());
            file.set("fileFactVersion", JsonUtils.parseObject(plan.getFileFactVersion(), JsonNode.class));
            putWireLong(file, "scopeVersion", plan.getFileScopeVersion()); file.put("sha256", plan.getFileSha256());
            root.put("ownershipConfirmed", Boolean.TRUE.equals(plan.getOwnershipConfirmed())); return root;
        }
        ObjectNode root = (ObjectNode) JsonUtils.parseObject(plan.getContentSnapshot(), JsonNode.class);
        ArrayNode steps = root.putArray("steps");
        stepMapper.selectListByPlan(new CutoverPlanChildrenQuery(tenantId, plan.getId())).stream()
                .map(this::step).sorted(Comparator.comparingInt((CutoverPlanContentCodec.PlanStep value) ->
                        cn.iocoder.yudao.module.pms.cutover.service.plan.domain.CutoverPlanRules.STANDARD_SECTIONS
                                .indexOf(value.sectionCode())).thenComparingInt(CutoverPlanContentCodec.PlanStep::stepNo))
                .forEach(value -> {
                    ObjectNode row = steps.addObject(); row.put("sectionCode", value.sectionCode());
                    row.put("stepNo", value.stepNo()); row.put("content", value.content());
                });
        if ("ONLINE_TEMPLATE_STANDARD".equals(plan.getEditModeCode())) {
            ArrayNode support = root.putArray("supportArrangements");
            supportMapper.selectListByPlan(new CutoverPlanChildrenQuery(tenantId, plan.getId())).forEach(value -> {
                ObjectNode row = support.addObject(); putWireLong(row, "arrangementId", value.getId());
                row.put("roleCode", value.getRoleCode()); row.put("personName", value.getPersonName());
                row.put("dutyDescription", value.getDutyDescription()); row.put("phone", value.getPhone());
                row.put("arrivalTime", value.getArrivalTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
            });
        }
        return root;
    }

    private List<String> allowedActions(Long tenantId, Long actorId, CutoverTaskDO task,
                                        CutoverPlanRevisionDO plan, CutoverApprovalFact approval,
                                        PlanAccess access, boolean editAllowed) {
        List<String> actions = new ArrayList<>();
        boolean ownerP4 = CutoverTaskRules.ORIGIN_NEW_PLATFORM.equals(task.getTaskOrigin())
                && CutoverTaskRules.STAGE_P4.equals(task.getCurrentStage())
                && CutoverTaskRules.STATUS_PLAN_DRAFTING.equals(task.getTaskStatus())
                && Objects.equals(task.getOwnerUserId(), actorId);
        if (ownerP4 && editAllowed && plan == null && access.create()) actions.add("CREATE_DRAFT");
        if (ownerP4 && editAllowed && plan != null && "DRAFT".equals(plan.getStatusCode()) && access.save()
                && comparableSource(tenantId, actorId, task, plan)) actions.add("SAVE_DRAFT");
        if (ownerP4 && editAllowed && plan != null && "DRAFT".equals(plan.getStatusCode()) && access.submit()
                && approvalFactApi != null && comparableSource(tenantId, actorId, task, plan)
                && complete(tenantId, plan)) actions.add("SUBMIT_PLAN");
        if (ownerP4 && editAllowed && plan != null && access.save() && approval != null
                && canRevise(tenantId, actorId, task, plan, approval)) actions.add("REVISE_PLAN");
        boolean ownerP6 = CutoverTaskRules.ORIGIN_NEW_PLATFORM.equals(task.getTaskOrigin())
                && "P6".equals(task.getCurrentStage()) && "CLOSURE_IN_PROGRESS".equals(task.getTaskStatus())
                && Objects.equals(task.getOwnerUserId(), actorId);
        if (ownerP6 && editAllowed && plan != null && access.save() && "SUBMITTED".equals(plan.getStatusCode())
                && Objects.equals(plan.getCurrentMarker(), 1) && approval != null
                && approval.status() == ApprovalStatus.APPROVED) actions.add("UPDATE_APPROVED_CONTACTS");
        if (plan != null && "DRAFT".equals(plan.getStatusCode()) && access.download()) actions.add("DOWNLOAD_DRAFT");
        return List.copyOf(actions);
    }

    private boolean canRevise(Long tenantId, Long actorId, CutoverTaskDO task, CutoverPlanRevisionDO plan,
                              CutoverApprovalFact approval) {
        if (!positive(plan.getApprovalInstanceId()) || hasDirectSuccessor(tenantId, task.getId(), plan.getId())) {
            return false;
        }
        if ("SUBMITTED".equals(plan.getStatusCode()) && Objects.equals(plan.getCurrentMarker(), 1)) {
            return approval.status() == ApprovalStatus.REJECTED;
        }
        return "INVALIDATED".equals(plan.getStatusCode()) && plan.getCurrentMarker() == null
                && approval.status() == ApprovalStatus.PAUSED_SOURCE_INVALIDATED
                && sourceChanged(tenantId, actorId, task, plan);
    }

    private boolean complete(Long tenantId, CutoverPlanRevisionDO plan) {
        try {
            CutoverPlanSourcePort.SourceSnapshot snapshot = JsonUtils.parseObject(
                    plan.getSourceSnapshot(), CutoverPlanSourcePort.SourceSnapshot.class);
            CutoverPlanSourcePort.SourceFacts facts = new CutoverPlanSourcePort.SourceFacts(
                    snapshot, snapshot.failedRiskFacts());
            CutoverPlanContentCodec.DecodedContent content = codec.decodeWritable(assemble(tenantId, plan), facts);
            codec.validateComplete(content, facts);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private boolean sourceChanged(Long tenantId, Long actorId, CutoverTaskDO task, CutoverPlanRevisionDO plan) {
        try {
            CutoverPlanSourcePort.SourceFacts current = sourcePort.inspect(tenantId, actorId, task.getId());
            CutoverPlanSourcePort.SourceSnapshot frozen = JsonUtils.parseObject(plan.getSourceSnapshot(),
                    CutoverPlanSourcePort.SourceSnapshot.class);
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

    private static void requireApprovalIdentity(CutoverPlanRevisionDO plan, CutoverApprovalFact fact) {
        CutoverPlanSourcePort.SourceSnapshot source = JsonUtils.parseObject(
                plan.getSourceSnapshot(), CutoverPlanSourcePort.SourceSnapshot.class);
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

    private static JsonNode parseSourceSnapshot(CutoverPlanRevisionDO plan) {
        try {
            JsonNode snapshot = JsonUtils.parseObject(plan.getSourceSnapshot(), JsonNode.class);
            if (snapshot == null || !snapshot.isObject()) throw corrupted("方案来源快照损坏");
            if ("LEGACY_FORWARD".equals(plan.getOriginCode())) {
                requireExactKeys(snapshot, Set.of("sourceTable", "sourceId", "sourceTenantId", "sourceTaskId",
                        "sourceVersion", "sourceStatusRaw", "mappingVersion", "code", "name", "level", "remark"));
                LegacyPlanSourceSnapshot legacy = JsonUtils.parseObject(plan.getSourceSnapshot(), LegacyPlanSourceSnapshot.class);
                legacy.validate();
            } else {
                CutoverPlanSourcePort.SourceSnapshot source = JsonUtils.parseObject(
                        plan.getSourceSnapshot(), CutoverPlanSourcePort.SourceSnapshot.class);
                return JsonUtils.getObjectMapper().valueToTree(source);
            }
            return snapshot;
        } catch (CutoverPlanApplicationException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw corrupted("方案来源快照损坏");
        }
    }

    private static void requireExactKeys(JsonNode value, Set<String> expected) {
        Set<String> actual = value.properties().stream().map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toSet());
        if (!actual.equals(expected)) throw corrupted("方案来源快照字段损坏");
    }

    private record LegacyPlanSourceSnapshot(String sourceTable, Long sourceId, Long sourceTenantId,
                                            Long sourceTaskId, Integer sourceVersion, Integer sourceStatusRaw,
                                            String mappingVersion, String code, String name, String level,
                                            String remark) {
        private void validate() {
            if (!"pms_cut_plan".equals(sourceTable) || sourceId == null || sourceId <= 0
                    || sourceTenantId == null || sourceTenantId <= 0 || sourceTaskId == null || sourceTaskId <= 0
                    || sourceVersion == null || sourceVersion < 0 || sourceStatusRaw == null
                    || sourceStatusRaw < 0 || sourceStatusRaw > 4
                    || !"FCUT004_LEGACY_V1".equals(mappingVersion) || !validText(code, 64)
                    || !validText(name, 128) || !List.of("A", "B", "C", "D").contains(level)
                    || remark != null && (!remark.equals(remark.trim()) || remark.length() > 4000)) {
                throw corrupted("legacy方案来源快照损坏");
            }
        }
    }

    private static boolean validText(String value, int maxLength) {
        return value != null && !value.isBlank() && value.equals(value.trim()) && value.length() <= maxLength;
    }

    private boolean comparableSource(Long tenantId, Long actorId, CutoverTaskDO task, CutoverPlanRevisionDO plan) {
        try {
            CutoverPlanSourcePort.SourceFacts current = sourcePort.inspect(tenantId, actorId, task.getId());
            CutoverPlanSourcePort.SourceSnapshot frozen = JsonUtils.parseObject(plan.getSourceSnapshot(),
                    CutoverPlanSourcePort.SourceSnapshot.class);
            return current != null && current.snapshot().equals(frozen);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private CutoverPlanContentCodec.PlanStep step(CutoverPlanStepDO row) {
        return new CutoverPlanContentCodec.PlanStep(row.getSectionCode(), row.getStepNo(), row.getContent());
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

    public record PlanAccess(boolean create, boolean save, boolean submit, boolean download) {}
}
