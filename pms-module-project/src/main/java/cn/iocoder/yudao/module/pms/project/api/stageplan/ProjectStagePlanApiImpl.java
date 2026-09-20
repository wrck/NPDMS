package cn.iocoder.yudao.module.pms.project.api.stageplan;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectStageInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectStageInstanceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Objects;
import cn.iocoder.yudao.module.pms.project.domain.deliveryconfiguration.StageTransitionTargetResolver;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectRuntimeRuleEvaluator;

/**
 * 阶段计划契约实现；写路径仅允许修改计划起止日期字段。
 * <p>
 * 阶段事实权威载体为 {@code proj_project_stage}（pms_project_phase_retired 已随 pms_* 旧域退役冻结）。
 * 契约 status 延续旧阶段 Integer 口径的字符串编码（0未开始/1进行中/2已完成/3已跳过），
 * 新阶段 String 状态域按 PENDING→0、ACTIVE→1、DONE→2 映射；未知状态返回 null，
 * 消费方按未知状态保守计入未完成。
 */
@Service
@Validated
@RequiredArgsConstructor
public class ProjectStagePlanApiImpl implements ProjectStagePlanApi {
    private final ProjectStageInstanceMapper stageInstanceMapper;
    private final ProjectMasterMapper projectMasterMapper;
    private final cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectPlanVersionMapper planVersionMapper;
    private final cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectTaskInstanceMapper taskInstanceMapper;
    @jakarta.annotation.Resource
    private ProjectRuntimeRuleEvaluator ruleEvaluator;
    @jakarta.annotation.Resource
    private cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.ProjectRuntimeGraphMapper runtimeGraphMapper;
    @jakarta.annotation.Resource
    private cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectGateReferenceInstanceMapper referenceMapper;

    @Override
    public ScheduleCalculation calculateSchedule(Long tenantId, Long projectId, java.time.LocalDate start, java.time.LocalDate end) {
        requireProject(tenantId, projectId);
        var project = projectMasterMapper.selectById(projectId);
        if (start == null || end == null || end.isBefore(start)) throw new IllegalArgumentException("请先录入有效项目工期");
        var plan = project.getActivePlanVersionId() == null ? null : planVersionMapper.selectById(project.getActivePlanVersionId());
        if (plan == null || !Objects.equals(plan.getTenantId(), tenantId) || !Objects.equals(plan.getProjectId(), projectId))
            throw new IllegalArgumentException("项目尚无冻结计划，不能推算施工日期");
        var snapshot = cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseObject(plan.getExecutionSnapshot(),
                cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot.class);
        if (snapshot == null) throw new IllegalArgumentException("项目冻结计划不可读");
        var stages = stageInstanceMapper.selectListByProjectId(projectId);
        var tasks = taskInstanceMapper.selectListByProjectId(projectId);
        var definitions = snapshot.getStages().stream().collect(java.util.stream.Collectors.toMap(
                cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot.StageContract::getCode, value -> value));
        var starts = snapshot.getStages().stream().filter(value -> Boolean.TRUE.equals(value.getStart())).toList();
        if (starts.size() != 1) throw new IllegalArgumentException("冻结计划起点不唯一，无法推算");
        var ordered = new java.util.ArrayList<cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot.StageContract>();
        var seen = new java.util.HashSet<String>();
        var pathInputs = new java.util.ArrayList<java.util.Map<String, Object>>();
        var current = starts.getFirst();
        while (current != null) {
            if (!seen.add(current.getCode())) throw new IllegalArgumentException("冻结计划路径存在循环");
            ordered.add(current);
            if (Boolean.TRUE.equals(current.getTerminal())) break;
            String code = current.getCode();
            var outgoing = snapshot.getTransitions().stream().filter(edge -> code.equals(edge.getFromStageCode())).toList();
            String target;
            if (outgoing.size() == 1 && !conditional(outgoing.getFirst())) {
                target = outgoing.getFirst().getToStageCode();
            } else {
                target = resolveTarget(snapshot, project, stages, tasks, code, pathInputs);
            }
            current = definitions.get(target);
            if (current == null) throw new IllegalArgumentException("冻结路径目标阶段不存在");
        }
        var allocations = new java.util.ArrayList<cn.iocoder.yudao.module.pms.project.domain.projectschedule.ProjectStageScheduleRules.StageAllocation>();
        var constraints = new java.util.LinkedHashMap<Long, java.time.LocalDate>();
        var inputStages = new java.util.ArrayList<java.util.Map<String, Object>>();
        for (var definition : ordered) {
            if (definition.getSchedulePercentage() == null) continue;
            var stage = stages.stream().filter(value -> definition.getCode().equals(value.getCode())).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("参与计划的阶段实例不存在"));
            allocations.add(new cn.iocoder.yudao.module.pms.project.domain.projectschedule.ProjectStageScheduleRules.StageAllocation(
                    stage.getId(), stage.getCode(), definition.getSchedulePercentage()));
            java.time.LocalDate deadline = stage.getAcceptanceTime() == null ? null : stage.getAcceptanceTime().toLocalDate();
            var taskInputs = new java.util.ArrayList<java.util.Map<String, Object>>();
            for (var task : tasks) {
                if (!stage.getCode().equals(task.getStageCode())) continue;
                var taskInput = new java.util.LinkedHashMap<String, Object>();
                taskInput.put("taskId", task.getId()); taskInput.put("taskCode", task.getCode());
                taskInput.put("acceptanceTime", task.getAcceptanceTime() == null ? null : task.getAcceptanceTime().toString()); taskInput.put("version", task.getVersion());
                taskInputs.add(taskInput);
            }
            if (deadline != null) constraints.put(stage.getId(), deadline);
            var input = new java.util.LinkedHashMap<String, Object>();
            input.put("stageId", stage.getId()); input.put("stageCode", stage.getCode());
            input.put("percentage", definition.getSchedulePercentage()); input.put("acceptanceTime", stage.getAcceptanceTime() == null ? null : stage.getAcceptanceTime().toString());
            input.put("tasks", taskInputs); inputStages.add(input);
        }
        if (allocations.isEmpty()) throw new IllegalArgumentException("冻结项目计划未配置施工阶段工期占比，请在计划修订中补齐后重新推算");
        java.time.LocalDate anchor = end;
        if ("DIRECT_SIGN".equals(project.getSigningMethod())) {
            // The final participating stage owns the project deadline; earlier acceptance nodes stay local constraints.
            String finalStageCode = allocations.getLast().stageCode();
            var finalStage = stages.stream().filter(value -> finalStageCode.equals(value.getCode())).findFirst().orElseThrow();
            anchor = finalStage.getAcceptanceTime() == null ? null : finalStage.getAcceptanceTime().toLocalDate();
            if (anchor == null) {
                var deadlines = tasks.stream().filter(task -> finalStageCode.equals(task.getStageCode()) && task.getAcceptanceTime() != null)
                        .map(task -> task.getAcceptanceTime().toLocalDate()).distinct().toList();
                if (deadlines.size() != 1) throw new IllegalArgumentException("最终阶段的回款计划验收时间缺失或不唯一，请同步或明确验收节点");
                anchor = deadlines.getFirst();
            }
        } else if (project.getSigningMethod() == null || project.getSigningMethod().isBlank()) {
            throw new IllegalArgumentException("项目签约方式未明确，不能选择工期基准");
        }
        int days = Math.toIntExact(java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1);
        var result = cn.iocoder.yudao.module.pms.project.domain.projectschedule.ProjectStageScheduleRules.calculate(anchor, days, allocations);
        for (var value : result.stages()) {
            var deadline = constraints.get(value.stageId());
            if (deadline != null && value.endDate().isAfter(deadline)) throw new IllegalArgumentException(value.stageCode() + " 计划结束晚于计划验收时间，请调整工期配置");
        }
        var inputs = new java.util.LinkedHashMap<String, Object>();
        inputs.put("sourcePlanVersionId", plan.getId()); inputs.put("signingMethod", project.getSigningMethod());
        inputs.put("durationStart", start); inputs.put("durationEnd", end); inputs.put("anchorEnd", anchor);
        inputs.put("stages", inputStages);
        inputs.put("pathConditions", pathInputs);
        return new ScheduleCalculation(plan.getId(), result.startDate(), result.endDate(), result.stages().stream()
                .map(value -> new StagePlanDate(value.stageId(), value.startDate(), value.endDate())).toList(),
                cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(inputs));
    }

    private static boolean conditional(TemplateExecutionSnapshot.TransitionContract edge) {
        return edge.getConditionRuleKey() != null && !edge.getConditionRuleKey().isBlank()
                || edge.getConditionRule() != null && !edge.getConditionRule().isNull();
    }

    private String resolveTarget(TemplateExecutionSnapshot snapshot,
            cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO project,
            List<ProjectStageInstanceDO> stages,
            List<cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskInstanceDO> tasks,
            String code, List<java.util.Map<String, Object>> pathInputs) {
        var matched = new java.util.ArrayList<TemplateExecutionSnapshot.TransitionContract>();
        var defaults = new java.util.ArrayList<TemplateExecutionSnapshot.TransitionContract>();
        var query = new cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.query.ProjectRuntimeGraphQuery(project.getTenantId(), project.getId());
        if (ruleEvaluator == null || runtimeGraphMapper == null) throw new IllegalArgumentException("冻结路径条件求值不可用");
        var gates = runtimeGraphMapper.selectGates(query);
        var references = gates.isEmpty() ? List.<cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectGateReferenceInstanceDO>of()
                : referenceMapper.selectOrdered(new cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectGateReferenceForUpdateQuery(
                        project.getTenantId(), gates.stream().map(cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectGateInstanceDO::getId).toList()));
        var stage = stages.stream().filter(value -> code.equals(value.getCode())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("条件所属阶段实例缺失"));
        var facts = new ProjectRuntimeRuleEvaluator.Facts(project, stage, tasks, gates, references, false);
        for (int i = 0; i < snapshot.getTransitions().size(); i++) {
            var edge = snapshot.getTransitions().get(i);
            if (!code.equals(edge.getFromStageCode())) continue;
            if (edge.getPriority() == null || edge.getDefaultBranch() == null || edge.getCode() == null)
                throw new IllegalArgumentException("冻结路径关系配置不完整");
            if (Boolean.TRUE.equals(edge.getDefaultBranch())) { defaults.add(edge); continue; }
            if (!conditional(edge)) { matched.add(edge); continue; }
            StageTransitionTargetResolver.ConditionStatus status;
            if (edge.getConditionRuleKey() != null && !edge.getConditionRuleKey().isBlank()) {
                var program = snapshot.getRulePrograms().get(edge.getConditionRuleKey());
                if (program == null) throw new IllegalArgumentException("冻结路径条件程序缺失：" + edge.getConditionRuleKey());
                var evaluation = ruleEvaluator.evaluate("plan:" + project.getActivePlanVersionId() + ":" + edge.getConditionRuleKey(), program, facts);
                status = switch (evaluation.outcome()) {
                    case MATCHED -> StageTransitionTargetResolver.ConditionStatus.SATISFIED;
                    case NOT_MATCHED -> StageTransitionTargetResolver.ConditionStatus.UNSATISFIED;
                    case UNKNOWN -> StageTransitionTargetResolver.ConditionStatus.UNAVAILABLE;
                };
            } else status = ruleEvaluator.evaluate(edge.getConditionRule(), facts);
            pathInputs.add(java.util.Map.of("transition", edge.getCode(), "result", status.name()));
            if (status == StageTransitionTargetResolver.ConditionStatus.UNAVAILABLE)
                throw new IllegalArgumentException("冻结路径条件事实不可用：" + edge.getCode());
            if (status == StageTransitionTargetResolver.ConditionStatus.SATISFIED) matched.add(edge);
        }
        // The frozen template compiler owns graph validation. Its custom stage codes are not
        // constrained by the legacy S0-S6 graph validator; preserve the same priority semantics.
        if (defaults.size() > 1) throw new IllegalArgumentException("冻结路径默认分支不唯一：" + code);
        if (matched.isEmpty()) {
            if (defaults.size() == 1) return defaults.getFirst().getToStageCode();
            throw new IllegalArgumentException("冻结路径无匹配分支：" + code);
        }
        int priority = matched.stream().mapToInt(TemplateExecutionSnapshot.TransitionContract::getPriority).min().orElseThrow();
        var winners = matched.stream().filter(edge -> edge.getPriority() == priority).toList();
        if (winners.size() != 1) throw new IllegalArgumentException("冻结路径同优先级分支同时命中：" + code);
        return winners.getFirst().getToStageCode();
    }


    @Override
    public List<TaskPlan> listTaskPlans(Long tenantId, Long projectId) {
        requireProject(tenantId, projectId);
        return taskInstanceMapper.selectListByProjectId(projectId).stream().map(task -> new TaskPlan(
                task.getId(), task.getParentTaskId(), task.getStageCode(), task.getName(), task.getVersion(),
                task.getPlanStartTime() == null ? null : task.getPlanStartTime().toLocalDate(),
                task.getPlanEndTime() == null ? null : task.getPlanEndTime().toLocalDate(),
                task.getAcceptanceTime() == null ? null : task.getAcceptanceTime().toLocalDate())).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int applyTaskPlanDates(Long tenantId, Long projectId, List<TaskPlan> dates) {
        requireProject(tenantId, projectId);
        var current = taskInstanceMapper.selectListByProjectId(projectId).stream().collect(java.util.stream.Collectors.toMap(
                cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskInstanceDO::getId, task -> task));
        var stages = stageInstanceMapper.selectListByProjectId(projectId).stream().collect(java.util.stream.Collectors.toMap(ProjectStageInstanceDO::getCode, stage -> stage));
        var seen = new java.util.HashSet<Long>();
        for (var date : dates) {
            var task = current.get(date.taskId()); var stage = task == null ? null : stages.get(task.getStageCode());
            if (task == null || !seen.add(date.taskId()) || !Objects.equals(task.getTenantId(), tenantId)
                    || !Objects.equals(task.getVersion(), date.version()) || !Objects.equals(task.getStageCode(), date.stageCode())
                    || stage == null || date.planStart() == null || date.planEnd() == null || date.planEnd().isBefore(date.planStart())
                    || stage.getPlanStartTime() == null || stage.getPlanEndTime() == null
                    || date.planStart().isBefore(stage.getPlanStartTime().toLocalDate()) || date.planEnd().isAfter(stage.getPlanEndTime().toLocalDate())
                    || task.getAcceptanceTime() != null && date.planEnd().isAfter(task.getAcceptanceTime().toLocalDate()))
                throw new IllegalArgumentException("任务范围、版本或计划日期已变化，请重新制定计划");
        }
        for (var date : dates) {
            if (taskInstanceMapper.updateSchedule(new cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectNodeScheduleUpdate(tenantId, projectId, date.taskId(), date.version(), date.planStart(), date.planEnd())) != 1) throw new IllegalStateException("TASK_PLAN_UPDATE_CONFLICT");
        }
        return dates.size();
    }

    @Override
    public List<StagePlanFact> listStages(Long tenantId, Long projectId) {
        requireProject(tenantId, projectId);
        return stageInstanceMapper.selectListByProjectId(projectId).stream()
                .map(ProjectStagePlanApiImpl::toFact)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int applyPlanDates(Long tenantId, Long projectId, List<StagePlanDate> dates) {
        requireProject(tenantId, projectId);
        if (dates == null || dates.isEmpty()) return 0;
        var stageIds = stageInstanceMapper.selectListByProjectId(projectId).stream()
                .filter(stage -> Objects.equals(stage.getTenantId(), tenantId))
                .map(ProjectStageInstanceDO::getId).collect(java.util.stream.Collectors.toSet());
        var supplied = new java.util.HashSet<Long>();
        for (StagePlanDate date : dates) {
            if (date == null || !stageIds.contains(date.stageId()) || !supplied.add(date.stageId())
                    || date.planStartTime() == null || date.planEndTime() == null
                    || date.planEndTime().isBefore(date.planStartTime())) {
                throw new IllegalArgumentException("STAGE_PLAN_STAGE_SCOPE_OR_DATE_INVALID");
            }
        }
        var versions = stageInstanceMapper.selectListByProjectId(projectId).stream().collect(java.util.stream.Collectors.toMap(ProjectStageInstanceDO::getId, ProjectStageInstanceDO::getVersion));
        int updated = 0;
        for (StagePlanDate date : dates) {
            if (stageInstanceMapper.updateSchedule(new cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectNodeScheduleUpdate(tenantId, projectId, date.stageId(), versions.get(date.stageId()), date.planStartTime(), date.planEndTime())) != 1) {
                throw new IllegalStateException("STAGE_PLAN_STAGE_UPDATE_CONFLICT");
            }
            updated++;
        }
        return updated;
    }

    private static StagePlanFact toFact(ProjectStageInstanceDO stage) {
        return new StagePlanFact(stage.getId(), stage.getCode(), stage.getName(), stage.getSortOrder(),
                stage.getSuggestedStartTime() == null ? null : stage.getSuggestedStartTime().toLocalDate(),
                stage.getSuggestedEndTime() == null ? null : stage.getSuggestedEndTime().toLocalDate(),
                stage.getPlanStartTime() == null ? null : stage.getPlanStartTime().toLocalDate(),
                stage.getPlanEndTime() == null ? null : stage.getPlanEndTime().toLocalDate(),
                legacyStatusCode(stage.getStatus()), stage.getVersion());
    }

    private static String legacyStatusCode(String stageStatus) {
        if (stageStatus == null) return null;
        return switch (stageStatus) {
            case "PENDING" -> "0";
            case "ACTIVE" -> "1";
            case "DONE" -> "2";
            default -> null;
        };
    }

    private void requireProject(Long tenantId, Long projectId) {
        if (tenantId == null || projectId == null || projectId <= 0) {
            throw new IllegalArgumentException("STAGE_PLAN_PROJECT_INVALID");
        }
        var project = projectMasterMapper.selectById(projectId);
        if (project == null || !Objects.equals(project.getTenantId(), tenantId)) {
            throw new IllegalArgumentException("STAGE_PLAN_PROJECT_INVALID");
        }
    }
}
