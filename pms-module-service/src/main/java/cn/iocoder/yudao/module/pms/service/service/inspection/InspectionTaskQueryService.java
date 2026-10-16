package cn.iocoder.yudao.module.pms.service.service.inspection;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.asset.api.device.ProjectDeviceShipmentApi;
import cn.iocoder.yudao.module.pms.project.api.reference.ProjectBusinessContextApi;
import cn.iocoder.yudao.module.pms.platform.api.file.FileActionCodes;
import cn.iocoder.yudao.module.pms.platform.api.file.FileArtifactApi;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetCollectionQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetKey;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.*;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.*;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.query.*;
import cn.iocoder.yudao.module.pms.service.service.inspectionrule.InspectionRuleQueryService;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InspectionTaskQueryService {
    private final InspectionAccessService access;
    private final InspectionTaskMapper tasks;
    private final InspectionTaskDeviceMapper devices;
    private final InspectionRuleSelectionMapper selections;
    private final InspectionAttemptMapper attempts;
    private final InspectionExecutionMapper executions;
    private final InspectionCollectionReceiptMapper collectionReceipts;
    private final InspectionDetectionResultMapper results;
    private final InspectionReportRevisionMapper reports;
    private final InspectionReportResultMapper reportResults;
    private final InspectionReportFileMapper reportFiles;
    private final InspectionAnnotationMapper annotations;
    private final ServiceIssueMapper issues;
    private final ServiceIssueRemediationMapper remediations;
    private final InspectionEvidenceMapper evidence;
    private final InspectionTaskEventMapper events;
    private final InspectionRuleFeedbackMapper ruleFeedback;
    private final InspectionRuleQueryService rules;
    private final ProjectDeviceShipmentApi shipments;
    private final ProjectBusinessContextApi projectContexts;
    private final AdminUserApi users;
    private final FileArtifactApi files;
    private final cn.iocoder.yudao.module.system.api.permission.PermissionApi permissions;

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class PageQuery extends PageParam {
        private String keyword;
        private Long projectId;
        private String mode;
        private String statusCode;
    }
    public record Detail(InspectionRequestContext context, Long taskId) { }
    public record ProjectOptions(InspectionRequestContext context, String keyword) { }
    public record DeviceOptions(InspectionRequestContext context, Long projectId) { }
    public record ProjectOption(Long id, String code, String name, String customerName) { }
    public record DeviceOption(Long id, String serialNumber, String name, String productModel) { }

    public PageResult<InspectionViews.TaskSummary> page(PageQuery request) {
        var context = InspectionRequestContext.current();
        access.permission(context, "pms:inspection-task:query");
        var query = BeanUtils.toBean(request, InspectionTaskPageQuery.class);
        query.setTenantId(context.tenantId()); query.setActorId(context.actorId());
        var scope = access.queryScope(context);
        query.setVisibleProjectIds(scope.visibleProjectIds()); query.setManagedProjectIds(scope.serviceManagedProjectIds());
        long total = tasks.selectPageCount(query);
        if (total == 0) return PageResult.empty();
        var rows = tasks.selectPageList(query);
        return new PageResult<>(summaries(rows), total);
    }

    public InspectionViews.Workbench workbench(InspectionRequestContext context) {
        boolean taskQuery = permissions.hasAnyPermissions(context.actorId(), "pms:inspection-task:query");
        boolean issueQuery = permissions.hasAnyPermissions(context.actorId(), "pms:service-issue:query");
        if (!taskQuery && !issueQuery) throw new InspectionException(InspectionException.Reason.FORBIDDEN, "没有巡检工作台查询权限");
        var scope = taskQuery ? access.queryScope(context) : null;
        var visible = taskQuery ? scope.visibleProjectIds() : access.visibleProjects(context);
        var query = new InspectionTaskPageQuery();
        query.setTenantId(context.tenantId()); query.setActorId(context.actorId()); query.setVisibleProjectIds(visible);
        query.setManagedProjectIds(taskQuery ? scope.serviceManagedProjectIds() : Set.of());
        query.setPageNo(1); query.setPageSize(8);
        var counts = taskQuery ? tasks.selectStateCounts(query) : List.<cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.projection.InspectionStateCount>of();
        query.setWorkbenchSection(InspectionTaskPageQuery.WorkbenchSection.PENDING);
        var pending = taskQuery ? summaries(tasks.selectPageList(query)) : List.<InspectionViews.TaskSummary>of();
        query.setWorkbenchSection(InspectionTaskPageQuery.WorkbenchSection.CREATED);
        var created = taskQuery ? summaries(tasks.selectPageList(query)) : List.<InspectionViews.TaskSummary>of();
        return new InspectionViews.Workbench(counts, pending, created,
                issueQuery ? issues.selectWorkbench(new ServiceIssueWorkbenchQuery(context.tenantId(), context.actorId(), visible, 20)).stream()
                        .map(row -> BeanUtils.toBean(row, InspectionViews.IssueSummary.class)).toList() : List.of());
    }

    public InspectionViews.TaskDetail detail(Detail request) {
        var task = access.task(new InspectionAccessService.TaskAccess(request.context(), request.taskId(),
                "pms:inspection-task:query", false, false));
        var query = new InspectionTaskRows(task.getTenantId(), task.getId());
        var view = BeanUtils.toBean(task, InspectionViews.TaskDetail.class);
        view.setOwner(Objects.equals(task.getOwnerUserId(), request.context().actorId()));
        view.setManager(access.manager(new InspectionAccessService.ProjectAccess(request.context(), task.getProjectId(), false)));
        view.setDevices(devices.selectForTask(query).stream().map(row -> BeanUtils.toBean(row, InspectionViews.DeviceView.class)).toList());
        view.setSelections(selections.selectForTask(query).stream().map(row -> BeanUtils.toBean(row, InspectionViews.SelectionView.class)).toList());
        view.setAttempts(attempts.selectForTask(query).stream().map(row -> BeanUtils.toBean(row, InspectionViews.AttemptView.class)).toList());
        view.setExecutions(executions.selectForTask(query).stream().map(row -> BeanUtils.toBean(row, InspectionViews.ExecutionView.class)).toList());
        view.setCollectionReceipts(collectionReceipts.selectForTask(query).stream().map(row -> BeanUtils.toBean(row, InspectionViews.CollectionReceiptView.class)).toList());
        Map<Long, InspectionRuleQueryService.RuleView> ruleCache = new HashMap<>();
        view.setResults(results.selectForTask(query).stream().map(row -> {
            var result = BeanUtils.toBean(row, InspectionViews.ResultView.class);
            var rule = ruleCache.computeIfAbsent(row.getRuleRevisionId(), rules::detail);
            result.setRuleName(rule.getRuleNameSnapshot()); result.setCategoryCode(rule.getCategoryCode());
            result.setCategoryName(rule.getCategoryNameSnapshot()); result.setSeverityCode(rule.getSeverityCode());
            result.setSeverityName(rule.getSeverityNameSnapshot()); return result;
        }).toList());
        var files = reportFiles.selectForTask(query); var reportInputs = reportResults.selectForTask(query);
        view.setReports(reports.selectForTask(query).stream().map(row -> {
            var report = BeanUtils.toBean(row, InspectionViews.ReportView.class);
            report.setFiles(files.stream().filter(f -> f.getReportId().equals(row.getId()))
                    .map(f -> BeanUtils.toBean(f, InspectionViews.ReportFileView.class)).toList());
            report.setResultIds(reportInputs.stream().filter(r -> r.getReportId().equals(row.getId())).map(InspectionReportResultDO::getResultId).toList());
            return report;
        }).toList());
        view.setAnnotations(annotations.selectForTask(query).stream().map(row -> BeanUtils.toBean(row, InspectionViews.AnnotationView.class)).toList());
        view.setIssues(issues.selectForTask(query).stream().map(row -> BeanUtils.toBean(row, InspectionViews.IssueView.class)).toList());
        view.setRemediations(remediations.selectForTask(query).stream().map(row -> BeanUtils.toBean(row, InspectionViews.RemediationView.class)).toList());
        view.setEvidence(evidence.selectForTask(query).stream().map(row -> BeanUtils.toBean(row, InspectionViews.EvidenceView.class)).toList());
        view.setEvents(events.selectForTask(query).stream().map(row -> BeanUtils.toBean(row, InspectionViews.EventView.class)).toList());
        view.setRuleFeedback(ruleFeedback.selectForTask(query).stream().map(row -> BeanUtils.toBean(row, InspectionViews.RuleFeedbackView.class)).toList());
        enrichPeople(view);
        return view;
    }

    private void enrichPeople(InspectionViews.TaskDetail view) {
        Set<Long> userIds = new HashSet<>();
        userIds.add(view.getOwnerUserId());
        view.getIssues().forEach(row -> userIds.add(row.getResponsibleUserId()));
        view.getRemediations().forEach(row -> userIds.add(row.getActorId()));
        view.getEvents().forEach(row -> userIds.add(row.getActorId()));
        view.getRuleFeedback().forEach(row -> userIds.add(row.getReportedBy()));
        userIds.remove(null);
        var people = users.getUserMap(userIds);
        var owner = people.get(view.getOwnerUserId());
        if (owner != null) view.setOwnerName(owner.getNickname());
        view.getIssues().forEach(row -> {
            var person = people.get(row.getResponsibleUserId());
            if (person != null) row.setResponsibleName(person.getNickname());
        });
        view.getRemediations().forEach(row -> {
            var person = people.get(row.getActorId());
            if (person != null) row.setActorName(person.getNickname());
        });
        view.getEvents().forEach(row -> {
            var person = people.get(row.getActorId());
            if (person != null) row.setActorName(person.getNickname());
        });
        view.getRuleFeedback().forEach(row -> {
            var person = people.get(row.getReportedBy());
            if (person != null) row.setReportedName(person.getNickname());
        });
    }

    public List<InspectionViews.AttachmentView> attachments(Detail request) {
        var task = access.task(new InspectionAccessService.TaskAccess(request.context(), request.taskId(),
                "pms:inspection-task:query", false, false));
        access.permission(request.context(), "pms:file:query");
        var key = new FileReferenceSetKey("SRV", "INSPECTION_TASK", task.getId().toString(), "ATTACHMENT");
        return files.inspectReferenceSets(new FileReferenceSetCollectionQuery(List.of(key), FileActionCodes.READ)).stream()
                .flatMap(set -> set.activeFacts().stream()).map(file -> new InspectionViews.AttachmentView(
                        file.artifactId(), file.versionNo(), file.referenceKey(), file.name(), file.sizeBytes(), file.availabilityStatus())).toList();
    }

    public List<ProjectOption> projects(ProjectOptions request) {
        access.permission(request.context(), "pms:inspection-task:query");
        return projectContexts.searchOptions(ProjectBusinessContextApi.OptionsQuery.builder()
                .tenantId(request.context().tenantId()).actorId(request.context().actorId()).keyword(request.keyword()).build())
                .stream().map(row -> new ProjectOption(row.id(), row.code(), row.name(), row.customerName())).toList();
    }

    private List<InspectionViews.TaskSummary> summaries(List<InspectionTaskDO> rows) {
        if (rows.isEmpty()) return List.of();
        Set<Long> userIds = rows.stream().map(InspectionTaskDO::getOwnerUserId).collect(Collectors.toSet());
        rows.stream().map(InspectionTaskQueryService::creatorId).filter(Objects::nonNull).forEach(userIds::add);
        var people = users.getUserMap(userIds);
        return rows.stream().map(task -> {
            var view = InspectionTaskService.summary(task);
            var owner = people.get(task.getOwnerUserId());
            if (owner != null) view.setOwnerName(owner.getNickname());
            var creatorId = creatorId(task);
            var creator = creatorId == null ? null : people.get(creatorId);
            if (creator != null) view.setCreatorName(creator.getNickname());
            return view;
        }).toList();
    }

    private static Long creatorId(InspectionTaskDO task) {
        try { return Long.valueOf(task.getCreator()); }
        catch (NumberFormatException ignored) { return null; }
    }

    public List<DeviceOption> devices(DeviceOptions request) {
        access.permission(request.context(), "pms:inspection-task:query");
        access.project(new InspectionAccessService.ProjectAccess(request.context(), request.projectId(), false));
        List<DeviceOption> options = new ArrayList<>();
        for (int pageNo = 1; ; pageNo++) {
            var page = shipments.pageDevices(ProjectDeviceShipmentApi.DeviceQuery.builder()
                    .tenantId(request.context().tenantId()).actorId(request.context().actorId())
                    .projectId(request.projectId()).pageNo(pageNo).pageSize(100).build());
            options.addAll(page.list().stream().map(row -> new DeviceOption(row.deviceId(), row.serialNumber(), null, row.productModel())).toList());
            if (page.list().isEmpty() || options.size() >= page.total()) break;
        }
        return List.copyOf(options);
    }
}
