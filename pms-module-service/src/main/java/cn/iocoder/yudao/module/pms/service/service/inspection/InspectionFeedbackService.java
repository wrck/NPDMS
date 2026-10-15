package cn.iocoder.yudao.module.pms.service.service.inspection;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.*;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.*;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.projection.*;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.query.*;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.pms.service.service.inspectionrule.InspectionRuleQueryService;
import cn.iocoder.yudao.module.pms.service.service.inspectionrule.InspectionRuleRevisionService;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.format.annotation.DateTimeFormat;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import static cn.iocoder.yudao.module.pms.service.service.inspection.InspectionException.require;

@Service
@RequiredArgsConstructor
public class InspectionFeedbackService {
    private final InspectionAccessService access;
    private final PermissionApi permissions;
    private final InspectionFeedbackQueryMapper queries;
    private final InspectionAnnotationMapper annotations;
    private final InspectionFeedbackConfirmationMapper confirmations;
    private final InspectionTaskMapper taskMapper;
    private final InspectionTaskService tasks;
    private final InspectionTaskDeviceMapper devices;
    private final InspectionRuleFeedbackMapper missing;
    private final InspectionDetectionResultMapper results;
    private final InspectionRuleQueryService rules;
    private final InspectionRuleRevisionService ruleRevisions;
    private final InspectionRuleOptimizationMapper optimizations;

    @Data @EqualsAndHashCode(callSuper = true)
    public static class PageQuery extends PageParam {
        private Long ruleRevisionId;
        private String detectionId, categoryCode, severityCode, productModel, confirmation;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) private LocalDateTime fromAt;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) private LocalDateTime toAt;
        private Boolean currentOnly;
    }
    public record ScopeQuery(InspectionRequestContext context, PageQuery filter) { }
    public record FeedbackPage(PageResult<InspectionFeedbackRow> page, List<InspectionFeedbackStatistic> statistics,
                               LocalDateTime dataAsOf, LocalDateTime fromAt, LocalDateTime toAt, boolean administrator, boolean details) { }
    public record Confirm(InspectionRequestContext context, Long annotationId, boolean valid, String reason) { }
    public record Confirmation(Long annotationId, Integer version, Long confirmationId, boolean valid, String reason) { }
    public record Missing(InspectionRequestContext context, Long taskId, Long deviceId, String description) { }
    public record Optimize(InspectionRequestContext context, Long annotationId, String reason) { }

    @Transactional(readOnly = true)
    public FeedbackPage page(ScopeQuery request) {
        var query = scope(request); long total = queries.count(query);
        var rows = total == 0 ? List.<InspectionFeedbackRow>of() : queries.selectPage(query);
        if (query.isDetails()) {
            var commandCache = new HashMap<Long, String>();
            for (var row : rows) row.setCommands(commandCache.computeIfAbsent(row.getRuleRevisionId(), id -> rules.detail(id).getCommands().stream()
                    .sorted(Comparator.comparingInt(c -> c.executionOrder())).map(c -> c.commandContent())
                    .collect(java.util.stream.Collectors.joining("\n"))));
        }
        var statistics = query.isAdministrator() ? queries.statistics(query) : List.<InspectionFeedbackStatistic>of();
        for (var row : statistics) row.setFalsePositiveRate(row.getDetectionCount() == 0 ? null :
                BigDecimal.valueOf(row.getConfirmedFalsePositiveCount()).divide(BigDecimal.valueOf(row.getDetectionCount()), 6, RoundingMode.HALF_UP));
        return new FeedbackPage(new PageResult<>(rows, total), statistics, query.getDataAsOf(), query.getFromAt(), query.getToAt(), query.isAdministrator(), query.isDetails());
    }
    public InspectionFeedbackQuery scope(ScopeQuery request) {
        access.permission(request.context(), "pms:inspection-feedback:query");
        var filter = request.filter(); require(filter != null, "缺少误报筛选条件");
        require(filter.getFromAt() == null || filter.getToAt() == null || !filter.getFromAt().isAfter(filter.getToAt()), "检出时间范围不正确");
        require(filter.getConfirmation() == null || Set.of("PENDING", "VALID", "INVALID").contains(filter.getConfirmation()), "确认状态无效");
        var query = BeanUtils.toBean(filter, InspectionFeedbackQuery.class);
        query.setTenantId(request.context().tenantId()); query.setActorId(request.context().actorId());
        query.setVisibleProjectIds(access.visibleProjects(request.context()));
        query.setAdministrator(permissions.hasAnyPermissions(request.context().actorId(), "pms:inspection-feedback:analyze"));
        query.setDetails(permissions.hasAnyPermissions(request.context().actorId(), "pms:inspection-feedback:details"));
        require(query.isDetails() || filter.getProductModel() == null || filter.getProductModel().isBlank(), "没有设备明细字段查询权限");
        query.setDataAsOf(LocalDateTime.now()); return query;
    }

    @Transactional(rollbackFor = Exception.class)
    public Confirmation confirm(Confirm command) {
        access.permission(command.context(), "pms:inspection-feedback:confirm");
        access.permission(command.context(), "pms:inspection-feedback:analyze");
        InspectionTaskService.validateText(command.reason(), 2000, "误报确认依据");
        var annotation = annotations.selectById(command.annotationId());
        require(annotation != null && Objects.equals(annotation.getTenantId(), command.context().tenantId())
                && "FALSE_POSITIVE".equals(annotation.getKind()), "误报标注不存在或不可见");
        var task = taskMapper.selectById(annotation.getTaskId());
        require(task != null && Objects.equals(task.getTenantId(), command.context().tenantId()), "误报任务不存在");
        access.project(new InspectionAccessService.ProjectAccess(command.context(), task.getProjectId(), true));
        taskMapper.selectForUpdate(new InspectionTaskRows(command.context().tenantId(), task.getId()));
        var latest = confirmations.selectForTaskForUpdate(new InspectionTaskRows(task.getTenantId(), task.getId())).stream()
                .filter(c -> Objects.equals(c.getAnnotationId(), annotation.getId())).max(Comparator.comparingInt(InspectionFeedbackConfirmationDO::getRevisionNo));
        int version = latest.map(InspectionFeedbackConfirmationDO::getRevisionNo).orElse(0);
        if (command.context().requireVersion() != version)
            throw new InspectionException(InspectionException.Reason.VERSION_CONFLICT, "误报确认已更新，请刷新后重试", (long) version);
        var row = new InspectionFeedbackConfirmationDO(); row.setTenantId(task.getTenantId()); row.setVersion(1L);
        row.setTaskId(task.getId()); row.setAnnotationId(annotation.getId()); row.setRevisionNo(version + 1); row.setValidFlag(command.valid());
        row.setReason(command.reason().trim()); row.setConfirmedBy(command.context().actorId()); row.setConfirmedAt(LocalDateTime.now()); confirmations.insert(row);
        return new Confirmation(annotation.getId(), row.getRevisionNo(), row.getId(), command.valid(), row.getReason());
    }

    @Transactional(rollbackFor = Exception.class)
    public InspectionViews.TaskSummary reportMissing(Missing command) {
        var task = access.task(new InspectionAccessService.TaskAccess(command.context(), command.taskId(), "pms:inspection-task:prepare", true, false));
        InspectionTaskService.validateText(command.description(), 8000, "规则缺失反馈");
        if (command.deviceId() != null) require(devices.selectForTask(new InspectionTaskRows(task.getTenantId(), task.getId())).stream()
                .anyMatch(d -> Objects.equals(d.getDeviceId(), command.deviceId())), "反馈设备不属于当前任务");
        var row = new InspectionRuleFeedbackDO(); row.setTenantId(task.getTenantId()); row.setVersion(1L); row.setTaskId(task.getId());
        row.setDeviceId(command.deviceId()); row.setDescription(command.description().trim()); row.setReportedBy(command.context().actorId());
        row.setReportedAt(LocalDateTime.now()); missing.insert(row);
        tasks.save(new InspectionTaskService.Save(command.context(), task, "RULE_MISSING_FEEDBACK", task.getStatusCode(), "提交规则缺失反馈"));
        return InspectionTaskService.summary(task);
    }

    @Transactional(rollbackFor = Exception.class)
    public InspectionRuleRevisionService.DraftResult createRuleDraft(Optimize command) {
        access.permission(command.context(), "pms:inspection-feedback:analyze");
        access.permission(command.context(), "pms:inspection-rule:manage");
        InspectionTaskService.validateText(command.reason(), 2000, "规则优化原因");
        var annotation = annotations.selectById(command.annotationId());
        require(annotation != null && Objects.equals(annotation.getTenantId(), command.context().tenantId())
                && "FALSE_POSITIVE".equals(annotation.getKind()), "误报标注不存在或不可见");
        var task = taskMapper.selectById(annotation.getTaskId());
        require(task != null && Objects.equals(task.getTenantId(), command.context().tenantId()), "误报任务不存在");
        access.project(new InspectionAccessService.ProjectAccess(command.context(), task.getProjectId(), true));
        var result = results.selectById(annotation.getResultId());
        require(result != null && Objects.equals(result.getTenantId(), task.getTenantId()) && Objects.equals(result.getTaskId(), task.getId()), "误报来源结果不完整");
        var source = rules.detail(result.getRuleRevisionId());
        if (source.getVersion().longValue() != command.context().requireVersion())
            throw new InspectionException(InspectionException.Reason.VERSION_CONFLICT, "来源规则版本已变化，请刷新后重试", source.getVersion().longValue());
        var draft = ruleRevisions.copyRevision(source.getRevisionId());
        var link = new InspectionRuleOptimizationDO(); link.setTenantId(task.getTenantId()); link.setVersion(1L);
        link.setSourceAnnotationId(annotation.getId()); link.setSourceRuleRevisionId(source.getRevisionId()); link.setDraftRuleRevisionId(draft.revisionId());
        link.setReason(command.reason().trim()); link.setCreatedBy(command.context().actorId()); link.setCreatedAt(LocalDateTime.now()); optimizations.insert(link);
        return draft;
    }
}
