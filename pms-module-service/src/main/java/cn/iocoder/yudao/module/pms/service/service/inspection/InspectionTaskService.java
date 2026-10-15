package cn.iocoder.yudao.module.pms.service.service.inspection;

import cn.hutool.core.util.IdUtil;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.asset.api.device.ProjectDeviceSelectionApi;
import cn.iocoder.yudao.module.pms.asset.api.producttype.inspection.InspectionAssetProductTypeApi;
import cn.iocoder.yudao.module.pms.asset.api.producttype.dto.AuthorizedDeviceProductTypeQuery;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.*;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.*;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.query.InspectionTaskRows;
import cn.iocoder.yudao.module.pms.service.domain.inspection.InspectionTaskStateMachine;
import cn.iocoder.yudao.module.pms.service.service.inspectionrule.InspectionRuleQueryService;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.module.pms.service.service.inspection.InspectionException.require;

@Service
@RequiredArgsConstructor
public class InspectionTaskService {
    private final InspectionTaskMapper tasks;
    private final InspectionTaskDeviceMapper devices;
    private final InspectionRuleSelectionMapper selections;
    private final InspectionAttemptMapper attempts;
    private final InspectionTaskRuleSnapshotMapper snapshots;
    private final InspectionTaskEventMapper events;
    private final InspectionExecutionMapper executions;
    private final InspectionAccessService access;
    private final ProjectDeviceSelectionApi deviceSelection;
    private final InspectionAssetProductTypeApi productTypes;
    private final InspectionRuleQueryService ruleQueries;

    @Builder
    public record Create(InspectionRequestContext context, String taskName, Long projectId,
                         List<Long> deviceIds, String background, LocalDateTime plannedAt, String mode) { }
    public record RuleChoice(Long deviceId, Long ruleRevisionId) { }
    @Builder
    public record Preparation(InspectionRequestContext context, Long taskId, String taskName,
                              String background, LocalDateTime plannedAt, String mode, List<RuleChoice> rules) { }
    @Builder
    public record Start(InspectionRequestContext context, Long taskId, String reason) { }
    public record Assignment(InspectionRequestContext context, Long taskId, Long ownerUserId, String reason) { }
    public record Cancellation(InspectionRequestContext context, Long taskId, String reason) { }
    public record Change(InspectionRequestContext context, InspectionTaskDO task,
                         InspectionTaskStateMachine.Transition transition, String description) { }
    public record Save(InspectionRequestContext context, InspectionTaskDO task, String action, String previousStatus,
                       String description) { }

    @Transactional(rollbackFor = Exception.class)
    public InspectionViews.TaskSummary create(Create command) {
        var context = command.context();
        access.permission(context, "pms:inspection-task:create");
        validateText(command.taskName(), 200, "任务名称");
        validateText(command.background(), 8000, "巡检背景");
        validateMode(command.mode());
        require(command.deviceIds() != null && !command.deviceIds().isEmpty() && command.deviceIds().size() <= 100
                && new HashSet<>(command.deviceIds()).size() == command.deviceIds().size(), "请选择1至100台不同设备");
        var project = access.project(new InspectionAccessService.ProjectAccess(context, command.projectId(), true));
        require(project.customerId() != null && project.customerName() != null, "项目尚未关联客户资料");
        var selected = deviceSelection.validateSelection(command.projectId(), command.deviceIds());
        require(selected.size() == command.deviceIds().size(), "设备与项目或关联合同不匹配");
        var types = productTypes.getAuthorizedDeviceProductType(new AuthorizedDeviceProductTypeQuery(
                context.actorId(), command.deviceIds())).stream().collect(Collectors.toMap(t -> t.deviceId(), t -> t));
        var task = new InspectionTaskDO();
        task.setTenantId(context.tenantId()); task.setVersion(1L);
        task.setTaskCode("INS-" + IdUtil.getSnowflakeNextId()); task.setTaskName(command.taskName().trim());
        task.setProjectId(project.projectId()); task.setProjectCode(project.projectCode()); task.setProjectName(project.projectName());
        task.setCustomerId(project.customerId()); task.setCustomerCode(project.customerCode()); task.setCustomerName(project.customerName());
        task.setOwnerUserId(context.actorId()); task.setBackground(command.background().trim());
        task.setPlannedAt(command.plannedAt()); task.setMode(command.mode()); task.setStatusCode("PREPARING");
        tasks.insert(task);
        for (var device : selected) {
            var type = types.get(device.equipmentId());
            require(type != null && type.enabled() && type.productTypeCode() != null && type.displayName() != null,
                    "设备产品类型未由权威来源解析或已停用，请先完善设备资料");
            require(device.sn() != null && !device.sn().isBlank(), "巡检设备缺少序列号");
            var row = new InspectionTaskDeviceDO();
            row.setTenantId(context.tenantId()); row.setVersion(1L); row.setTaskId(task.getId());
            row.setDeviceId(device.equipmentId()); row.setSerialNumber(device.sn()); row.setDeviceName(device.name());
            row.setProductTypeCode(type.productTypeCode()); row.setProductTypeName(type.displayName());
            row.setProductModel(device.productModel()); row.setContractNo(device.contractNo()); devices.insert(row);
        }
        recordEvent(new Save(context, task, "CREATE", null, "手工创建巡检任务"));
        return summary(task);
    }

    @Transactional(rollbackFor = Exception.class)
    public InspectionViews.TaskSummary savePreparation(Preparation command) {
        var task = access.task(new InspectionAccessService.TaskAccess(command.context(), command.taskId(),
                "pms:inspection-task:prepare", true, false));
        requirePreparing(task);
        validateText(command.taskName(), 200, "任务名称");
        validateText(command.background(), 8000, "巡检背景");
        validateMode(command.mode());
        var choices = command.rules() == null ? List.<RuleChoice>of() : List.copyOf(command.rules());
        require(choices.size() <= 3000, "选择的检测项过多");
        validateChoices(new SelectionValidation(command.context(), task, choices, false));
        task.setTaskName(command.taskName().trim()); task.setBackground(command.background().trim());
        task.setPlannedAt(command.plannedAt()); task.setMode(command.mode());
        selections.clearPreparation(new InspectionTaskRows(task.getTenantId(), task.getId()));
        for (var choice : choices) {
            var row = new InspectionRuleSelectionDO(); row.setTenantId(task.getTenantId()); row.setVersion(1L);
            row.setTaskId(task.getId()); row.setDeviceId(choice.deviceId()); row.setRuleRevisionId(choice.ruleRevisionId());
            selections.insert(row);
        }
        save(new Save(command.context(), task, "SAVE_PREPARATION", task.getStatusCode(), "保存巡检准备资料"));
        return summary(task);
    }

    @Transactional(rollbackFor = Exception.class)
    public InspectionViews.TaskSummary start(Start command) {
        var context = command.context();
        var task = access.task(new InspectionAccessService.TaskAccess(context, command.taskId(),
                "pms:inspection-task:execute", true, false));
        var rows = new InspectionTaskRows(context.tenantId(), task.getId());
        var choices = selections.selectForTask(rows).stream()
                .map(row -> new RuleChoice(row.getDeviceId(), row.getRuleRevisionId())).toList();
        validateChoices(new SelectionValidation(context, task, choices, true));
        var history = attempts.selectForTask(rows);
        if (!history.isEmpty()) validateText(command.reason(), 2000, "重新执行原因");
        require(executions.selectForTask(rows).stream().noneMatch(row -> Objects.equals(row.getAttemptId(), task.getCurrentAttemptId())
                && Set.of("PENDING", "RUNNING", "COLLECTED", "CANCEL_REQUESTED").contains(row.getStatusCode())), "当前仍有采集或解析在进行，请先完成或取消后再新建尝试");
        var action = "OFFLINE".equals(task.getMode()) ? InspectionTaskStateMachine.Action.START_OFFLINE
                : InspectionTaskStateMachine.Action.START_ONLINE;
        String next = InspectionTaskStateMachine.next(new InspectionTaskStateMachine.Transition(
                task.getStatusCode(), action, false, false, false, 0, false, false));
        var attempt = new InspectionAttemptDO(); attempt.setTenantId(context.tenantId()); attempt.setVersion(1L);
        for (var previous : history) if ("ACTIVE".equals(previous.getStatusCode())) {
            previous.setStatusCode("SUPERSEDED"); previous.setFinishedAt(LocalDateTime.now()); attempts.updateById(previous);
        }
        attempt.setTaskId(task.getId()); attempt.setAttemptNo(history.stream().mapToInt(InspectionAttemptDO::getAttemptNo).max().orElse(0) + 1);
        attempt.setMode(task.getMode()); attempt.setStatusCode("ACTIVE"); attempt.setReason(command.reason());
        attempt.setStartedBy(context.actorId()); attempt.setStartedAt(LocalDateTime.now()); attempts.insert(attempt);
        for (var choice : choices) {
            var frozen = new InspectionTaskRuleSnapshotDO(); frozen.setTenantId(context.tenantId()); frozen.setVersion(1L);
            frozen.setTaskId(task.getId()); frozen.setAttemptId(attempt.getId()); frozen.setDeviceId(choice.deviceId());
            frozen.setRuleRevisionId(choice.ruleRevisionId()); snapshots.insert(frozen);
        }
        String previous = task.getStatusCode(); task.setCurrentAttemptId(attempt.getId()); task.setStatusCode(next);
        save(new Save(context, task, action.name(), previous, command.reason()));
        return summary(task);
    }

    @Transactional(rollbackFor = Exception.class)
    public InspectionViews.TaskSummary assign(Assignment command) {
        var task = access.task(new InspectionAccessService.TaskAccess(command.context(), command.taskId(),
                "pms:inspection-task:assign", true, true));
        validateText(command.reason(), 2000, "分配原因");
        access.responsible(new InspectionAccessService.Subject(command.context(), task.getProjectId(),
                command.ownerUserId(), "pms:inspection-task:execute"));
        task.setOwnerUserId(command.ownerUserId());
        task.setArchiveRequestedBy(null); task.setArchiveRequestedAt(null);
        save(new Save(command.context(), task, "ASSIGN", task.getStatusCode(), command.reason()));
        return summary(task);
    }

    @Transactional(rollbackFor = Exception.class)
    public InspectionViews.TaskSummary cancel(Cancellation command) {
        var task = access.task(new InspectionAccessService.TaskAccess(command.context(), command.taskId(),
                "pms:inspection-task:cancel", true, true));
        validateText(command.reason(), 2000, "取消原因"); task.setCancellationReason(command.reason());
        transition(new Change(command.context(), task, new InspectionTaskStateMachine.Transition(task.getStatusCode(),
                InspectionTaskStateMachine.Action.CANCEL, false, false, false, 0, false, false), command.reason()));
        return summary(task);
    }

    public void transition(Change change) {
        String previous = change.task().getStatusCode();
        require(Objects.equals(previous, change.transition().current()), "状态机输入与任务状态不一致");
        change.task().setStatusCode(InspectionTaskStateMachine.next(change.transition()));
        save(new Save(change.context(), change.task(), change.transition().action().name(), previous, change.description()));
    }

    public void save(Save change) {
        if (tasks.updateById(change.task()) != 1)
            throw new InspectionException(InspectionException.Reason.VERSION_CONFLICT, "巡检任务已更新，请刷新后重试");
        recordEvent(change);
    }

    private void recordEvent(Save change) {
        var event = new InspectionTaskEventDO(); event.setTenantId(change.context().tenantId()); event.setVersion(1L);
        event.setTaskId(change.task().getId()); event.setActionCode(change.action()); event.setFromStatus(change.previousStatus());
        event.setToStatus(change.task().getStatusCode()); event.setActorId(change.context().actorId());
        event.setDescription(change.description()); event.setOccurredAt(LocalDateTime.now()); events.insert(event);
    }

    private record SelectionValidation(InspectionRequestContext context, InspectionTaskDO task,
                                       List<RuleChoice> choices, boolean complete) { }
    private void validateChoices(SelectionValidation input) {
        require(input.choices().stream().allMatch(c -> c != null && c.deviceId() != null && c.deviceId() > 0
                && c.ruleRevisionId() != null && c.ruleRevisionId() > 0), "请选择有效的设备和规则修订");
        var rows = devices.selectForTask(new InspectionTaskRows(input.task().getTenantId(), input.task().getId()));
        var deviceMap = rows.stream().collect(Collectors.toMap(InspectionTaskDeviceDO::getDeviceId, d -> d));
        var currentTypes = productTypes.getAuthorizedDeviceProductType(new AuthorizedDeviceProductTypeQuery(
                input.context().actorId(), List.copyOf(deviceMap.keySet()))).stream().collect(Collectors.toMap(t -> t.deviceId(), t -> t));
        var published = ruleQueries.lockPublished(new InspectionRuleQueryService.PublishedSelection(input.context(),
                input.choices().stream().map(RuleChoice::ruleRevisionId).toList())).stream()
                .collect(Collectors.toMap(InspectionRuleQueryService.RuleView::getRevisionId, r -> r));
        Set<RuleChoice> unique = new HashSet<>(); Set<Long> covered = new HashSet<>();
        for (var choice : input.choices()) {
            require(choice != null && unique.add(choice) && deviceMap.containsKey(choice.deviceId()), "检测项重复或设备不属于本任务");
            var rule = published.get(choice.ruleRevisionId());
            require(rule != null, "巡检规则不存在");
            require("PUBLISHED".equals(rule.getStatusCode()), "只能选择当前已发布的巡检规则");
            var type = currentTypes.get(choice.deviceId());
            require(type != null && type.enabled() && type.productTypeCode() != null, "设备当前产品类型不可用，请先完善设备资料");
            require(rule.getProductTypes().stream().anyMatch(p -> p.productTypeCode().equals(type.productTypeCode())),
                    "巡检规则不适用于所选设备产品类型");
            covered.add(choice.deviceId());
        }
        if (input.complete()) require(covered.equals(deviceMap.keySet()), "每台巡检设备至少选择一个适用检测项");
    }

    public static InspectionViews.TaskSummary summary(InspectionTaskDO task) {
        return BeanUtils.toBean(task, InspectionViews.TaskSummary.class);
    }
    public static void validateText(String text, int max, String name) {
        require(text != null && !text.isBlank() && text.length() <= max, name + "不能为空且不能超过" + max + "字");
    }
    private static void validateMode(String mode) { require(mode != null && Set.of("ONLINE", "OFFLINE").contains(mode), "请选择巡检方式"); }
    private static void requirePreparing(InspectionTaskDO task) {
        if (!Set.of("PREPARING", "PRECHECK_PENDING", "EXECUTING", "REPORT_PENDING").contains(task.getStatusCode()))
            throw new InspectionException(InspectionException.Reason.STATE_CONFLICT, "当前阶段不能修改准备资料");
    }
}
