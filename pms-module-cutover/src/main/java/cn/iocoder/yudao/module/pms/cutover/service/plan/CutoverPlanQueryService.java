package cn.iocoder.yudao.module.pms.cutover.service.plan;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.planv2.CutoverPlanRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.planv2.CutoverPlanStepDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.planv2.CutoverSupportArrangementDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.CutoverPlanRevisionMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.CutoverPlanStepMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.CutoverSupportArrangementMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.query.CutoverPlanChildrenQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.query.CutoverPlanRevisionQuery;
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
import java.util.Objects;

import static cn.iocoder.yudao.module.pms.cutover.service.plan.CutoverPlanApplicationException.Code.NOT_FOUND;

/** Task 4只读详情内核；不注册生产Bean。 */
public class CutoverPlanQueryService {
    private final CutoverTaskMapper taskMapper;
    private final CutoverPlanRevisionMapper planMapper;
    private final CutoverPlanStepMapper stepMapper;
    private final CutoverSupportArrangementMapper supportMapper;
    private final CutoverProjectScopePort projectScopePort;
    private final CutoverPlanSourcePort sourcePort;
    private final CutoverPlanContentCodec codec;

    public CutoverPlanQueryService(CutoverTaskMapper taskMapper, CutoverPlanRevisionMapper planMapper,
                                   CutoverPlanStepMapper stepMapper, CutoverSupportArrangementMapper supportMapper,
                                   CutoverProjectScopePort projectScopePort, CutoverPlanSourcePort sourcePort,
                                   CutoverPlanContentCodec codec) {
        this.taskMapper = taskMapper; this.planMapper = planMapper; this.stepMapper = stepMapper;
        this.supportMapper = supportMapper; this.projectScopePort = projectScopePort;
        this.sourcePort = sourcePort; this.codec = codec;
    }

    public CutoverPlanView detail(Long tenantId, Long actorId, Long taskId, PlanAccess access) {
        CutoverTaskDO task = taskMapper.selectById(taskId);
        if (task == null || !Objects.equals(task.getTenantId(), tenantId)) throw notFound();
        CutoverProjectScopePort.ProjectScopeFact scope = projectScopePort.inspect(actorId, task.getProjectId(), "ACTION_VIEW");
        if (scope == null || !scope.allowed()) throw notFound();
        CutoverPlanRevisionDO plan = planMapper.selectCurrent(new CutoverPlanRevisionQuery(tenantId, taskId, null));
        List<String> actions = allowedActions(tenantId, actorId, task, plan, access);
        if (plan == null) return new CutoverPlanView(taskId, task.getVersion(), null, null, null,
                null, null, null, actions);
        JsonNode content = assemble(tenantId, plan);
        return new CutoverPlanView(taskId, task.getVersion(), plan.getId(), plan.getRevisionNo(), plan.getVersion(),
                plan.getEditModeCode(), plan.getStatusCode(), content, actions);
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
                                        CutoverPlanRevisionDO plan, PlanAccess access) {
        List<String> actions = new ArrayList<>();
        boolean ownerP4 = CutoverTaskRules.ORIGIN_NEW_PLATFORM.equals(task.getTaskOrigin())
                && CutoverTaskRules.STAGE_P4.equals(task.getCurrentStage())
                && CutoverTaskRules.STATUS_PLAN_DRAFTING.equals(task.getTaskStatus())
                && Objects.equals(task.getOwnerUserId(), actorId);
        if (ownerP4 && plan == null && access.create()) actions.add("CREATE_DRAFT");
        if (ownerP4 && plan != null && "DRAFT".equals(plan.getStatusCode()) && access.save()
                && comparableSource(tenantId, actorId, task, plan)) actions.add("SAVE_DRAFT");
        if (plan != null && "DRAFT".equals(plan.getStatusCode()) && access.download()) actions.add("DOWNLOAD_DRAFT");
        return List.copyOf(actions);
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

    public record PlanAccess(boolean create, boolean save, boolean download) {}
}
