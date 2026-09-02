package cn.iocoder.yudao.module.pms.cutover.service.approval;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.cutover.api.approval.dto.CutoverApprovalStartCommand;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.checklist.CutoverChecklistDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.checklist.CutoverChecklistItemDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.checklist.CutoverChecklistItemResultDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.planv2.CutoverPlanRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.planv2.CutoverPlanStepDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.planv2.CutoverSupportArrangementDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverAssessmentDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.CutoverChecklistItemMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.CutoverChecklistItemResultMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.CutoverChecklistMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.query.CutoverChecklistItemsQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.query.CutoverChecklistResultsQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.query.CutoverChecklistRowQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.CutoverPlanRevisionMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.CutoverPlanStepMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.CutoverSupportArrangementMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.query.CutoverPlanChildrenQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.query.CutoverPlanRevisionQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverAssessmentMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.query.CutoverAssessmentRowQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.query.CutoverTaskRowQuery;
import cn.iocoder.yudao.module.pms.cutover.service.approval.domain.CutoverApprovalSourceSnapshotCodec;
import cn.iocoder.yudao.module.pms.cutover.service.approval.domain.CutoverApprovalSourceSnapshotCodec.*;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.domain.CutoverAssessmentAnswers;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverProjectContextPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static cn.iocoder.yudao.module.pms.cutover.service.approval.CutoverApprovalApplicationException.Code.*;

/** 从同一CUT事务内已锁定的P2/P3/P4事实组装审批不可变来源。 */
public class CutoverApprovalSourceAssembler {
    private static final long MAX_SAFE_WIRE_LONG = 9_007_199_254_740_991L;
    private final CutoverTaskMapper taskMapper;
    private final CutoverAssessmentMapper assessmentMapper;
    private final CutoverChecklistMapper checklistMapper;
    private final CutoverChecklistItemMapper itemMapper;
    private final CutoverChecklistItemResultMapper resultMapper;
    private final CutoverPlanRevisionMapper planMapper;
    private final CutoverPlanStepMapper stepMapper;
    private final CutoverSupportArrangementMapper supportMapper;
    private final CutoverApprovalSourceSnapshotCodec codec;

    public CutoverApprovalSourceAssembler(CutoverTaskMapper taskMapper, CutoverAssessmentMapper assessmentMapper,
            CutoverChecklistMapper checklistMapper, CutoverChecklistItemMapper itemMapper,
            CutoverChecklistItemResultMapper resultMapper, CutoverPlanRevisionMapper planMapper,
            CutoverPlanStepMapper stepMapper, CutoverSupportArrangementMapper supportMapper,
            CutoverApprovalSourceSnapshotCodec codec) {
        this.taskMapper = taskMapper; this.assessmentMapper = assessmentMapper; this.checklistMapper = checklistMapper;
        this.itemMapper = itemMapper; this.resultMapper = resultMapper; this.planMapper = planMapper;
        this.stepMapper = stepMapper; this.supportMapper = supportMapper; this.codec = codec;
    }

    public LockedSource lockAndAssemble(CutoverApprovalStartCommand command) {
        CutoverTaskDO task = taskMapper.selectForUpdate(new CutoverTaskRowQuery(command.tenantId(), command.taskId()));
        require(task != null && Objects.equals(task.getVersion(), command.expectedTaskVersion())
                && "P4".equals(task.getCurrentStage()) && "PLAN_DRAFTING".equals(task.getTaskStatus()),
                VERSION_CONFLICT, "割接任务版本或阶段已变化");
        CutoverAssessmentDO assessment = assessmentMapper.selectForUpdate(new CutoverAssessmentRowQuery(
                command.tenantId(), command.taskId(), command.assessmentId()));
        require(assessment != null && "SUBMITTED".equals(assessment.getAssessmentStatus())
                && Objects.equals(assessment.getAssessmentVersion(), command.assessmentVersion())
                && Objects.equals(assessment.getManualGrade(), command.grade()), SOURCE_STALE, "P2评估事实已变化");
        CutoverChecklistDO checklist = null;
        List<ChecklistResultSnapshot> risks = List.of();
        List<ChecklistResultSnapshot> surveys = List.of();
        if (!"D".equals(command.grade())) {
            checklist = checklistMapper.selectCurrentForUpdate(new CutoverChecklistRowQuery(
                    command.tenantId(), command.taskId(), command.checklistId()));
            require(checklist != null && "SUBMITTED".equals(checklist.getStatusCode())
                    && Objects.equals(checklist.getChecklistVersion(), command.checklistVersion()),
                    SOURCE_STALE, "P3清单事实已变化");
            List<CutoverChecklistItemDO> items = itemMapper.selectListForUpdate(
                    new CutoverChecklistItemsQuery(command.tenantId(), checklist.getId()));
            List<CutoverChecklistItemResultDO> results = resultMapper.selectCurrentByChecklistForUpdate(
                    new CutoverChecklistResultsQuery(command.tenantId(), checklist.getId()));
            Map<Long, CutoverChecklistItemResultDO> resultByItem = new HashMap<>();
            results.forEach(value -> resultByItem.put(value.getChecklistItemId(), value));
            List<ChecklistResultSnapshot> riskRows = new ArrayList<>();
            List<ChecklistResultSnapshot> surveyRows = new ArrayList<>();
            for (CutoverChecklistItemDO item : items) {
                if (!Boolean.TRUE.equals(item.getApplicableFlag())) continue;
                CutoverChecklistItemResultDO result = resultByItem.get(item.getId());
                require(result != null, BUSINESS_INCOMPLETE, "清单存在未完成项");
                ChecklistResultSnapshot row = snapshot(item, result);
                if ("BUSINESS_SURVEY".equals(item.getItemTypeCode())) surveyRows.add(row);
                else if (List.of("RISK", "DUAL_MACHINE_CHECK").contains(item.getItemTypeCode())) riskRows.add(row);
            }
            risks = List.copyOf(riskRows); surveys = List.copyOf(surveyRows);
        }
        CutoverPlanRevisionDO plan = planMapper.selectByIdForUpdate(new CutoverPlanRevisionQuery(
                command.tenantId(), command.taskId(), command.planRevisionId()));
        require(plan != null && "DRAFT".equals(plan.getStatusCode())
                && Objects.equals(plan.getRevisionNo(), command.planRevisionNo())
                && Objects.equals(plan.getGradeCode(), command.grade()), SOURCE_STALE, "P4方案事实已变化");

        CutoverProjectContextPort.ProjectContextFact project = JsonUtils.parseObject(
                task.getProjectContextSnapshot(), CutoverProjectContextPort.ProjectContextFact.class);
        CutoverAssessmentAnswers answers = JsonUtils.parseObject(assessment.getAnswerSnapshot(), CutoverAssessmentAnswers.class);
        JsonNode context = JsonUtils.parseTree(assessment.getContextSnapshot());
        String serviceLevel = context.path("customerServiceLevel").path("serviceLevelCode").asText();
        ApprovalSourceSnapshot snapshot = new ApprovalSourceSnapshot(command.sourceSnapshotVersion(), task.getId(),
                task.getVersion(), command.checklistId(), command.checklistVersion(),
                new ProjectApprovalSnapshot(project.projectId(), project.projectVersion(), project.projectCode(),
                        project.projectName(), project.customerId(), project.customerCode(), project.customerName(),
                        project.departmentId(), project.departmentCode(), project.departmentName(),
                        project.projectScopeVersion()),
                new CollectionAnalysisSnapshot(task.getCutoverType(), task.getNetworkMode(),
                        task.getScheduledTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()),
                risks, surveys,
                new AssessmentApprovalSnapshot(assessment.getId(), assessment.getAssessmentVersion(),
                        assessment.getQuestionnaireTemplateVersion(), answers.businessImportanceLevel(),
                        answers.operationComplexityLevel(), answers.hiddenRiskLevel(), answers.sparePartApplied(),
                        serviceLevel, assessment.getManualGrade(), assessment.getSubmittedBy(),
                        assessment.getSubmittedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()),
                new PlanApprovalSnapshot(plan.getId(), plan.getRevisionNo(), plan.getVersion(),
                        normalizedPlanSource(plan.getSourceSnapshot()), completePlanContent(task.getTenantId(), plan)));
        return new LockedSource(task, assessment, checklist, plan, codec.encode(snapshot));
    }

    private JsonNode normalizedPlanSource(String sourceSnapshot) {
        ObjectNode source = (ObjectNode) JsonUtils.parseTree(sourceSnapshot);
        if (!source.has("checklistId")) source.putNull("checklistId");
        if (!source.has("checklistVersion")) source.putNull("checklistVersion");
        return source;
    }

    private JsonNode completePlanContent(Long tenantId, CutoverPlanRevisionDO plan) {
        if ("FULL_FILE_UPLOAD".equals(plan.getEditModeCode())) {
            ObjectNode content = JsonUtils.getObjectMapper().createObjectNode();
            content.put("editMode", plan.getEditModeCode());
            ObjectNode file = content.putObject("fileArtifactFact");
            putWireLong(file, "artifactId", plan.getFileArtifactId());
            file.put("versionNo", plan.getFileVersionNo());
            file.put("referenceKey", plan.getFileReferenceKey());
            file.set("fileFactVersion", JsonUtils.parseTree(plan.getFileFactVersion()));
            putWireLong(file, "scopeVersion", plan.getFileScopeVersion());
            file.put("sha256", plan.getFileSha256());
            content.put("ownershipConfirmed", Boolean.TRUE.equals(plan.getOwnershipConfirmed()));
            return content;
        }
        ObjectNode content = (ObjectNode) JsonUtils.parseTree(plan.getContentSnapshot());
        CutoverPlanChildrenQuery query = new CutoverPlanChildrenQuery(tenantId, plan.getId());
        var steps = content.putArray("steps");
        for (CutoverPlanStepDO row : stepMapper.selectListByPlanForUpdate(query)) {
            steps.addObject().put("sectionCode", row.getSectionCode()).put("stepNo", row.getStepNo())
                    .put("content", row.getContent());
        }
        if ("ONLINE_TEMPLATE_STANDARD".equals(plan.getEditModeCode())) {
            var supports = content.putArray("supportArrangements");
            for (CutoverSupportArrangementDO row : supportMapper.selectListByPlanForUpdate(query)) {
                ObjectNode support = supports.addObject();
                putWireLong(support, "arrangementId", row.getId()); support.put("roleCode", row.getRoleCode());
                support.put("personName", row.getPersonName()); support.put("dutyDescription", row.getDutyDescription());
                support.put("phone", row.getPhone());
                support.put("arrivalTime", row.getArrivalTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
            }
        }
        return content;
    }

    private static void putWireLong(ObjectNode node, String field, long value) {
        if (value > -MAX_SAFE_WIRE_LONG && value < MAX_SAFE_WIRE_LONG) node.put(field, value);
        else node.put(field, Long.toString(value));
    }

    private static ChecklistResultSnapshot snapshot(CutoverChecklistItemDO item, CutoverChecklistItemResultDO result) {
        return new ChecklistResultSnapshot(item.getId(), item.getStableItemKey(), item.getItemDefinitionId(),
                item.getItemDefinitionVersion(), item.getItemTypeCode(), item.getItemName(),
                Boolean.TRUE.equals(item.getRequiredFlag()), result.getResultVersion(), result.getResultSourceCode(),
                result.getAnswerSnapshot(), result.getFactDescription(), result.getCollectionTaskId(),
                result.getCollectionResultReferenceId(), result.getCollectionResultVersion(),
                result.getExternalSourceCode(), result.getManualEvidenceFileReference());
    }

    private static void require(boolean condition, CutoverApprovalApplicationException.Code code, String message) {
        if (!condition) throw new CutoverApprovalApplicationException(code, message);
    }

    public record LockedSource(CutoverTaskDO task, CutoverAssessmentDO assessment, CutoverChecklistDO checklist,
                               CutoverPlanRevisionDO plan, String sourceSnapshot) { }
}
