package cn.iocoder.yudao.module.pms.cutover.service.plan;

import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverProjectScopePort;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskDeviceScopeDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.checklist.CutoverChecklistDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.query.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.query.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration.CutoverConfigurationRevisionMapper;
import cn.iocoder.yudao.module.pms.cutover.service.plan.port.CutoverPlanSourcePort;
import cn.iocoder.yudao.module.pms.cutover.service.plan.port.CutoverPlanOwnerFactException;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.*;
import cn.iocoder.yudao.module.pms.cutover.service.configuration.CutoverConfigurationMetadataStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import java.util.*;
import java.util.stream.Collectors;
import static cn.iocoder.yudao.module.pms.cutover.service.plan.port.CutoverPlanOwnerFactException.Code.*;

/** P4消费本模块冻结事实，跨模块重验仅经PROJ/AST公开端口。 */
@Service
@RequiredArgsConstructor
public class CutoverRuntimePlanSource implements CutoverPlanSourcePort {
    private final CutoverTaskMapper tasks;
    private final CutoverAssessmentMapper assessments;
    private final CutoverTaskDeviceScopeMapper devices;
    private final CutoverFrozenDeviceMapper frozenDevices;
    private final CutoverChecklistMapper checklists;
    private final CutoverChecklistItemMapper items;
    private final CutoverChecklistItemResultMapper results;
    private final CutoverConfigurationRevisionMapper configurations;
    private final CutoverProjectScopePort scopes;
    private final CutoverProjectContextPort projects;
    private final CutoverDeviceScopePort assetDevices;
    private final CutoverConfigurationMetadataStore configurationMetadata;
    @jakarta.annotation.Resource
    private cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverChecklistResultStore resultStorage;

    @Override
    public SourceFacts inspect(Long tenant, Long actor, Long taskId) {
        return assemble(tenant, actor, taskId, false);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public SourceFacts lockAndRevalidate(Long tenant, Long actor, SourceFacts expected) {
        var before = expected.snapshot();
        var scope = scopes.lockAndRevalidate(CutoverProjectScopePort.RevalidationQuery.builder()
                .actorId(actor)
                .projectId(before.projectId())
                .action("ACTION_EDIT")
                .expectedProjectScopeVersion(before.projectScopeVersion())
                .build());
        if (!scope.allowed() || scope.projectScopeVersion() != before.projectScopeVersion()) throw stale(PROJECT_OR_DEVICE_STALE);
        var project = projects.inspect(CutoverProjectContextPort.Inspection.builder()
                .tenantId(tenant)
                .projectId(before.projectId())
                .expectedProjectScopeVersion(before.projectScopeVersion())
                .build());
        if (project.projectVersion() != before.projectVersion()) throw stale(PROJECT_OR_DEVICE_STALE);
        projects.lockAndRevalidate(project);
        assetDevices.lockAndRevalidate(before.projectId(), before.devices().stream().map(device ->
                new CutoverDeviceScopePort.DeviceFact(device.deviceId(), device.serialNumber(), before.projectId(),
                        device.projectAssignmentVersion())).toList());
        var current = assemble(tenant, actor, before.taskId(), true);
        if (!expected.equals(current)) throw stale(SOURCE_STALE);
        return current;
    }

    private SourceFacts assemble(Long tenant, Long actor, Long taskId, boolean lock) {
        var task = lock ? tasks.selectForUpdate(new CutoverTaskRowQuery(tenant, taskId)) : tasks.selectById(taskId);
        if (task == null || !Objects.equals(task.getTenantId(), tenant)) throw stale(SOURCE_STALE);
        var scope = scopes.inspect(actor, task.getProjectId(), "ACTION_VIEW");
        if (!scope.allowed()) throw stale(PROJECT_OR_DEVICE_STALE);
        var project = projects.inspect(CutoverProjectContextPort.Inspection.builder()
                .tenantId(tenant)
                .projectId(task.getProjectId())
                .expectedProjectScopeVersion(scope.projectScopeVersion())
                .build());
        var assessment = lock ? assessments.selectForUpdate(new CutoverAssessmentRowQuery(tenant, taskId, task.getCurrentAssessmentId()))
                : assessments.selectById(task.getCurrentAssessmentId());
        if (assessment == null || !Objects.equals(assessment.getTenantId(), tenant)
                || !Objects.equals(assessment.getCutoverTaskId(), taskId) || !"SUBMITTED".equals(assessment.getAssessmentStatus())) throw stale(ASSESSMENT_STALE);
        String grade = assessment.getManualGrade();
        CutoverChecklistDO checklist = null;
        List<RiskFactSnapshot> risks = new ArrayList<>();
        if (!"D".equals(grade)) {
            var query = new CutoverChecklistRowQuery(tenant, taskId, null);
            checklist = lock ? checklists.selectCurrentForUpdate(query) : checklists.selectCurrent(query);
            if (checklist == null || !"SUBMITTED".equals(checklist.getStatusCode())) throw stale(CHECKLIST_STALE);
            var itemQuery = new CutoverChecklistItemsQuery(tenant, checklist.getId());
            var resultQuery = new CutoverChecklistResultsQuery(tenant, checklist.getId());
            var checklistItems = lock ? items.selectListForUpdate(itemQuery) : items.selectListByChecklist(itemQuery);
            var currentResults = lock ? results.selectCurrentByChecklistForUpdate(resultQuery) : results.selectCurrentByChecklist(resultQuery);
            var byItem = currentResults.stream().collect(Collectors.toMap(value -> value.getChecklistItemId(), value -> value));
            for (var item : checklistItems) {
                if (!Boolean.TRUE.equals(item.getApplicableFlag()) || "BUSINESS_SURVEY".equals(item.getItemTypeCode())) continue;
                var result = byItem.get(item.getId());
                var feedback = result == null ? null : resultStorage.feedback(result);
                boolean failed = feedback != null ? Boolean.FALSE.equals(feedback.getCheckNormalFlag())
                        : result != null && hasFailedDecision(JsonUtils.getObjectMapper().valueToTree(resultStorage.value(
                        new cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverChecklistResultStore.Read(item, result))));
                if (failed) {
                    String description = result.getFactDescription();
                    if (description == null || description.isBlank()) description = feedback == null ? null : feedback.getRemark();
                    if (description == null || description.isBlank()) description = resultStorage.answer(
                            new cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverChecklistResultStore.Read(item, result));
                    risks.add(new RiskFactSnapshot(item.getId(), item.getStableItemKey(), result.getResultVersion(), item.getItemName(),
                            "NOT_PASSED", description.length() > 4000 ? description.substring(0, 4000) : description));
                }
            }
        }
        var configuration = configurations.selectById(task.getConfigurationRevisionId());
        if (configuration == null || !Objects.equals(configuration.getTenantId(), tenant)
                || !Set.of("PUBLISHED", "DISABLED").contains(configuration.getStatusCode())) throw stale(CONFIGURATION_OR_TEMPLATE_STALE);
        var sections = new ArrayList<TemplateSectionSnapshot>();
        for (var value : configurationMetadata.sectionViews(configuration.getId())) {
            var types = value.getCutoverTypeCodes();
            var grades = value.getLevelCodes();
            if ((!types.isEmpty() && !types.contains(task.getCutoverType())) || !grades.contains(grade)) continue;
            sections.add(new TemplateSectionSnapshot(value.getStableSectionKey(), value.getTitle(),
                    value.getSortOrder(), types.isEmpty() ? List.of(task.getCutoverType()) : types, grades, Boolean.TRUE.equals(value.getRequired())));
        }
        var rows = lock ? devices.selectActiveByTaskForUpdate(new CutoverTaskDeviceListQuery(tenant, taskId))
                : frozenDevices.byTask(new CutoverTaskDeviceListQuery(tenant, taskId));
        var snapshot = new SourceSnapshot(1, taskId, task.getVersion(), assessment.getId(), assessment.getAssessmentVersion(), grade,
                checklist == null ? null : checklist.getId(), checklist == null ? null : checklist.getChecklistVersion(),
                task.getProjectId(), project.projectVersion(), scope.projectScopeVersion(), rows.stream().map(value -> new DeviceSnapshot(
                        value.getDeviceId(), value.getSerialNumberSnapshot(), value.getProjectAssignmentVersion(),
                        value.getDeviceTypeCodeSnapshot(), value.getDeviceTypeSourceVersionSnapshot())).toList(),
                configuration.getId(), configuration.getConfigurationCode(), configuration.getRevisionNo(), sections, risks);
        return new SourceFacts(snapshot, risks);
    }

    static boolean hasFailedDecision(JsonNode answer) {
        if (answer == null || answer.isNull()) return false;
        if (answer.isTextual()) {
            String value = answer.asText().trim();
            if (Set.of("NO", "FAILED", "NOT_PASSED").contains(value)) return true;
            if (value.startsWith("{") || value.startsWith("[")) {
                try { return hasFailedDecision(JsonUtils.parseTree(value)); } catch (RuntimeException ignored) { return false; }
            }
            return false;
        }
        if (answer.isArray()) {
            for (var row : answer) if (hasFailedDecision(row)) return true;
        } else if (answer.isObject()) {
            for (String field : List.of("value", "rows", "analysis", "resultCode", "decision", "passed", "normal")) {
                var value = answer.get(field);
                if (value != null && ((Set.of("passed", "normal").contains(field) && value.isBoolean() && !value.asBoolean())
                        || hasFailedDecision(value))) return true;
            }
        }
        return false;
    }

    private static CutoverPlanOwnerFactException stale(CutoverPlanOwnerFactException.Code code) {
        return new CutoverPlanOwnerFactException(code, "方案来源事实已变化或尚未完成，请刷新前序环节");
    }
}
