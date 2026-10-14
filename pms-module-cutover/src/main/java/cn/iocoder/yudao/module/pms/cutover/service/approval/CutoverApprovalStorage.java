package cn.iocoder.yudao.module.pms.cutover.service.approval;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.CutoverApprovalInstanceDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.planv2.CutoverPlanRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage.CutoverApprovalResultReferenceDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.CutoverChecklistItemMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.CutoverChecklistItemResultMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.query.CutoverChecklistResultVersionQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.query.CutoverPlanChildrenQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.CutoverApprovalResultReferenceMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.query.CutoverApprovalResultReferenceListQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverAssessmentMapper;
import cn.iocoder.yudao.module.pms.cutover.service.approval.domain.CutoverRuntimeApprovalSourceSnapshotCodec;
import cn.iocoder.yudao.module.pms.cutover.service.approval.domain.CutoverRuntimeApprovalSourceSnapshotCodec.*;
import cn.iocoder.yudao.module.pms.cutover.service.approval.leadtime.CutoverLeadTimeCompliance;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverChecklistResultStore;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverCustomResultStore;
import cn.iocoder.yudao.module.pms.cutover.service.plan.CutoverPlanStorage;
import cn.iocoder.yudao.module.pms.cutover.service.plan.domain.CutoverRuntimePlanContentCodec;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.CutoverTaskSnapshotStore;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Stream;

/** 审批提交来源保存在明确列及不可变版本关系，读取不追随当前清单结果或联系信息。 */
@Service
@RequiredArgsConstructor
public class CutoverApprovalStorage {
    private static final CutoverRuntimeApprovalSourceSnapshotCodec CODEC = new CutoverRuntimeApprovalSourceSnapshotCodec();
    private final CutoverApprovalResultReferenceMapper references;
    private final CutoverChecklistItemMapper items;
    private final CutoverChecklistItemResultMapper results;
    private final CutoverChecklistResultStore resultStorage;
    private final CutoverCustomResultStore customResults;
    private final CutoverAssessmentMapper assessments;
    private final CutoverPlanRevisionMapper plans;
    private final CutoverPlanStepMapper steps;
    private final CutoverSupportArrangementMapper supports;
    private final CutoverPlanStorage planStorage;

    @Builder
    public record ResultSetWrite(CutoverApprovalInstanceDO instance, ApprovalSourceSnapshot source,
                                 Long actorId, LocalDateTime time) {}

    /** 参数为已通过来源装配器校验的传输对象，不会持久化原JSON。 */
    public static ApprovalSourceSnapshot applySource(CutoverApprovalInstanceDO row, String sourceJson) {
        var source = CODEC.decode(sourceJson);
        if (!Objects.equals(row.getTaskId(), source.taskId()) || !Objects.equals(row.getProjectId(), source.project().projectId())
                || !Objects.equals(row.getPlanRevisionId(), source.plan().planRevisionId())
                || !Objects.equals(row.getPlanRevisionNo(), source.plan().planRevisionNo())
                || !Objects.equals(row.getAssessmentId(), source.assessment().assessmentId())
                || !Objects.equals(row.getAssessmentVersion(), source.assessment().assessmentVersion())
                || !Objects.equals(row.getGradeCode(), source.assessment().manualGrade())
                || !Objects.equals(row.getChecklistId(), source.checklistId())
                || !Objects.equals(row.getChecklistVersion(), source.checklistVersion())
                || !Objects.equals(row.getSourceSnapshotVersion(), source.snapshotVersion())) {
            throw new IllegalArgumentException("审批来源身份不一致");
        }
        row.setSourceTaskVersion(source.taskVersion()); row.setSourcePlanVersion(source.plan().planVersion());
        row.setSourceCutoverTypeCode(source.collectionAnalysis().cutoverType());
        row.setSourceNetworkModeCode(source.collectionAnalysis().networkMode());
        row.setSourceScheduleTime(time(source.collectionAnalysis().scheduledTime(), ZoneId.systemDefault()));
        var project = source.project();
        row.setProjectVersion((long) project.projectVersion()); row.setProjectCode(project.projectCode()); row.setProjectName(project.projectName());
        row.setCustomerId(project.customerId()); row.setCustomerCode(project.customerCode()); row.setCustomerName(project.customerName());
        row.setDepartmentId(project.departmentId()); row.setDepartmentCode(project.departmentCode()); row.setDepartmentName(project.departmentName());
        row.setProjectScopeVersion(project.projectScopeVersion());
        return source;
    }

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void insertResults(ResultSetWrite command) {
        var root = command.instance(); requireTenant(root);
        int order = 0;
        for (var source : Stream.concat(command.source().riskItems().stream(), command.source().businessSurveyItems().stream()).toList()) {
            var result = results.selectByVersion(CutoverChecklistResultVersionQuery.builder().tenantId(root.getTenantId())
                    .checklistItemId(source.checklistItemId()).resultVersion(source.itemResultVersion()).build());
            var item = items.selectById(source.checklistItemId());
            if (result == null || item == null || !Objects.equals(result.getTenantId(), root.getTenantId())
                    || !Objects.equals(result.getChecklistItemId(), source.checklistItemId())
                    || !Objects.equals(result.getResultVersion(), source.itemResultVersion())
                    || !Objects.equals(item.getTenantId(), root.getTenantId())
                    || !Objects.equals(item.getChecklistId(), root.getChecklistId())
                    || !Objects.equals(item.getStableItemKey(), source.stableItemKey())
                    || !Objects.equals(item.getItemTypeCode(), source.itemTypeCode())) {
                throw new IllegalArgumentException("审批结果集合引用不属于提交清单");
            }
            var row = CutoverApprovalResultReferenceDO.builder().tenantId(root.getTenantId()).approvalInstanceId(root.getId())
                    .checklistItemId(item.getId()).checklistResultId(result.getId()).resultVersion(result.getResultVersion())
                    .requiredFlag(source.required()).sortOrder(order++).createUserId(command.actorId()).createTime(command.time()).build();
            if (references.insert(row) != 1) throw new IllegalStateException("审批结果集合保存失败");
        }
    }

    public ApprovalSourceSnapshot source(CutoverApprovalInstanceDO root) {
        requireTenant(root);
        var assessment = assessments.selectById(root.getAssessmentId());
        var plan = plans.selectById(root.getPlanRevisionId());
        if (assessment == null || plan == null || !Objects.equals(assessment.getTenantId(), root.getTenantId())
                || !Objects.equals(plan.getTenantId(), root.getTenantId())
                || !Objects.equals(assessment.getCutoverTaskId(), root.getTaskId()) || !Objects.equals(plan.getCutoverTaskId(), root.getTaskId())
                || !Objects.equals(assessment.getAssessmentVersion(), root.getAssessmentVersion())
                || !Objects.equals(plan.getRevisionNo(), root.getPlanRevisionNo())) {
            throw new IllegalArgumentException("审批冻结评估或方案引用损坏");
        }
        List<ChecklistResultSnapshot> risks = new ArrayList<>(); List<ChecklistResultSnapshot> surveys = new ArrayList<>();
        for (var reference : references.selectListByParents(CutoverApprovalResultReferenceListQuery.builder()
                .tenantId(root.getTenantId()).approvalInstanceIds(Set.of(root.getId())).build())) {
            if (!Objects.equals(reference.getTenantId(), root.getTenantId())
                    || !Objects.equals(reference.getApprovalInstanceId(), root.getId()))
                throw new IllegalArgumentException("审批结果集合不属于当前审批");
            var snapshot = result(reference, root);
            if ("BUSINESS_SURVEY".equals(snapshot.itemTypeCode())) surveys.add(snapshot); else risks.add(snapshot);
        }
        var answers = CutoverTaskSnapshotStore.answers(assessment);
        return new ApprovalSourceSnapshot(root.getSourceSnapshotVersion(), root.getTaskId(), root.getSourceTaskVersion(),
                root.getChecklistId(), root.getChecklistVersion(),
                new ProjectApprovalSnapshot(root.getProjectId(), Math.toIntExact(root.getProjectVersion()), root.getProjectCode(),
                        root.getProjectName(), root.getCustomerId(), root.getCustomerCode(), root.getCustomerName(),
                        root.getDepartmentId(), root.getDepartmentCode(), root.getDepartmentName(), root.getProjectScopeVersion()),
                new CollectionAnalysisSnapshot(root.getSourceCutoverTypeCode(), root.getSourceNetworkModeCode(), millis(root.getSourceScheduleTime(), ZoneId.systemDefault())),
                risks, surveys, new AssessmentApprovalSnapshot(assessment.getId(), assessment.getAssessmentVersion(),
                        assessment.getQuestionnaireTemplateVersion(), answers.businessImportanceLevel(), answers.operationComplexityLevel(),
                        answers.hiddenRiskLevel(), answers.sparePartApplied(), assessment.getServiceLevelCode(), assessment.getManualGrade(),
                        assessment.getSubmittedBy(), millis(assessment.getSubmittedAt(), ZoneId.systemDefault())),
                new PlanApprovalSnapshot(plan.getId(), plan.getRevisionNo(), root.getSourcePlanVersion(),
                        planSource(plan), planContent(plan)));
    }

    public JsonNode sourceView(CutoverApprovalInstanceDO root) { return CODEC.toJson(source(root)); }

    private ChecklistResultSnapshot result(CutoverApprovalResultReferenceDO reference, CutoverApprovalInstanceDO root) {
        var item = items.selectById(reference.getChecklistItemId());
        var result = results.selectById(reference.getChecklistResultId());
        if (item == null || result == null || !Objects.equals(item.getTenantId(), reference.getTenantId())
                || !Objects.equals(result.getTenantId(), reference.getTenantId()) || !Objects.equals(result.getChecklistItemId(), item.getId())
                || !Objects.equals(item.getChecklistId(), root.getChecklistId())
                || !Objects.equals(result.getResultVersion(), reference.getResultVersion())) throw new IllegalArgumentException("审批冻结采集结果引用损坏");
        var manual = "MANUAL".equals(result.getResultSourceCode()) ? customResults.manualFile(result) : null;
        return new ChecklistResultSnapshot(item.getId(), item.getStableItemKey(), item.getItemDefinitionId(), item.getItemDefinitionVersion(),
                item.getItemTypeCode(), item.getItemName(), Boolean.TRUE.equals(reference.getRequiredFlag()), result.getResultVersion(),
                result.getResultSourceCode(), resultStorage.answer(new CutoverChecklistResultStore.Read(item, result)), result.getFactDescription(),
                result.getCollectionTaskId(), result.getCollectionResultReferenceId(), result.getCollectionResultVersion(),
                result.getExternalSourceCode(), manual == null ? null : manual.referenceKey());
    }

    private JsonNode planSource(CutoverPlanRevisionDO plan) {
        var source = (ObjectNode) planStorage.sourceView(plan);
        if (!source.has("checklistId")) source.putNull("checklistId");
        if (!source.has("checklistVersion")) source.putNull("checklistVersion");
        return source;
    }

    private ObjectNode planContent(CutoverPlanRevisionDO plan) {
        if ("FULL_FILE_UPLOAD".equals(plan.getEditModeCode())) {
            var root = JsonUtils.getObjectMapper().createObjectNode(); root.put("editMode", plan.getEditModeCode());
            var file = root.putObject("fileArtifactFact"); putWireLong(file, "artifactId", plan.getFileArtifactId());
            file.put("versionNo", plan.getFileVersionNo()); file.put("referenceKey", plan.getFileReferenceKey());
            file.set("fileFactVersion", JsonUtils.getObjectMapper().valueToTree(CutoverPlanStorage.fileVersion(plan)));
            putWireLong(file, "scopeVersion", plan.getFileScopeVersion()); file.put("sha256", plan.getFileSha256());
            root.put("ownershipConfirmed", Boolean.TRUE.equals(plan.getOwnershipConfirmed())); return root;
        }
        var query = new CutoverPlanChildrenQuery(plan.getTenantId(), plan.getId());
        var root = new CutoverRuntimePlanContentCodec().assembleStored(new CutoverRuntimePlanContentCodec.StoredPlanContent(
                planStorage.content(plan), steps.selectListByPlan(query)));
        if ("ONLINE_TEMPLATE_STANDARD".equals(plan.getEditModeCode())) {
            root.remove("customerContactReference");
            var rows = root.putArray("supportArrangements");
            supports.selectListByPlan(query).forEach(value -> rows.add(CutoverPlanStorage.supportContent(value)));
        }
        return root;
    }

    public static void applyLeadTime(CutoverApprovalInstanceDO row, CutoverLeadTimeCompliance fact) {
        row.setLeadTimeEnabled(fact != null);
        row.setLeadRuleVersion(fact == null ? null : fact.ruleVersion());
        row.setLeadTimezoneId(fact == null ? null : fact.timezoneId());
        row.setLeadPlanSubmitTime(fact == null ? null : time(fact.planSubmittedAt(), ZoneId.of(fact.timezoneId())));
        row.setLeadRequiredDays(fact == null ? null : fact.requiredDays());
        row.setLeadActualNaturalDays(fact == null ? null : fact.actualNaturalDays());
        row.setLeadLateSubmissionFlag(fact == null ? null : fact.lateSubmission());
    }

    public static CutoverLeadTimeCompliance leadTime(CutoverApprovalInstanceDO row) {
        if (Boolean.FALSE.equals(row.getLeadTimeEnabled())) {
            if (row.getLeadRuleVersion() != null || row.getLeadTimezoneId() != null || row.getLeadPlanSubmitTime() != null
                    || row.getLeadRequiredDays() != null || row.getLeadActualNaturalDays() != null || row.getLeadLateSubmissionFlag() != null)
                throw new IllegalArgumentException("禁用提前时间的审批包含计算事实");
            return null;
        }
        if (!Boolean.TRUE.equals(row.getLeadTimeEnabled()) || !List.of("A", "B").contains(row.getGradeCode()))
            throw new IllegalArgumentException("提前时间适用等级不一致");
        var zone = ZoneId.of(row.getLeadTimezoneId());
        return new CutoverLeadTimeCompliance(row.getLeadRuleVersion(), row.getLeadTimezoneId(), row.getSourceCutoverTypeCode(),
                millis(row.getSourceScheduleTime(), zone), millis(row.getLeadPlanSubmitTime(), zone),
                row.getLeadRequiredDays(), row.getLeadActualNaturalDays(), row.getLeadLateSubmissionFlag());
    }

    private static void requireTenant(CutoverApprovalInstanceDO root) {
        if (TenantContextHolder.isIgnore() || !Objects.equals(TenantContextHolder.getTenantId(), root.getTenantId()))
            throw new IllegalArgumentException("审批来源租户不一致");
    }
    private static LocalDateTime time(long millis, ZoneId zone) { return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone); }
    private static long millis(LocalDateTime time, ZoneId zone) { return time.atZone(zone).toInstant().toEpochMilli(); }
    private static void putWireLong(ObjectNode node, String field, long value) {
        if (value > -9_007_199_254_740_991L && value < 9_007_199_254_740_991L) node.put(field, value); else node.put(field, Long.toString(value));
    }
}
