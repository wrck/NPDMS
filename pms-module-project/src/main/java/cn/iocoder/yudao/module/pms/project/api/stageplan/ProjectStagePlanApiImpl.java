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
    private final cn.iocoder.yudao.module.pms.project.dal.mysql.projectschedule.StageSuggestionRuleMapper suggestionRuleMapper;
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
        // 并行分支（如模板"计划与方案并行、部署要求二者均完成"）都必须参与计划：
        // 工作队列覆盖全部可达阶段，条件边按冻结事实求值后并行纳入，默认分支仅在无其他可用转移时兜底
        var queue = new java.util.ArrayDeque<cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot.StageContract>();
        var usableEdges = new java.util.ArrayList<cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot.TransitionContract>();
        seen.add(starts.getFirst().getCode());
        queue.add(starts.getFirst());
        while (!queue.isEmpty()) {
            var current = queue.poll();
            ordered.add(current);
            if (Boolean.TRUE.equals(current.getTerminal())) continue;
            var outgoing = snapshot.getTransitions().stream()
                    .filter(edge -> current.getCode().equals(edge.getFromStageCode())).toList();
            List<cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot.TransitionContract> usable =
                    outgoing.size() == 1 && !conditional(outgoing.getFirst())
                            ? List.of(outgoing.getFirst())
                            : resolveTargets(snapshot, project, stages, tasks, current.getCode(), pathInputs);
            for (var edge : usable) {
                var next = definitions.get(edge.getToStageCode());
                if (next == null) throw new IllegalArgumentException("冻结路径目标阶段不存在");
                usableEdges.add(edge);
                if (seen.add(next.getCode())) queue.add(next);
            }
        }
        // 多分支汇聚回同一阶段是合法图；用拓扑消化区分汇聚与真正的环，成环仍拒绝推算
        var indegree = new java.util.HashMap<String, Integer>();
        for (String code : seen) indegree.put(code, 0);
        for (var edge : usableEdges) indegree.merge(edge.getToStageCode(), 1, Integer::sum);
        var outgoingByFrom = usableEdges.stream().collect(java.util.stream.Collectors.groupingBy(
                cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot.TransitionContract::getFromStageCode));
        var acyclic = seen.stream().filter(code -> indegree.get(code) == 0)
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayDeque::new));
        while (!acyclic.isEmpty()) {
            for (var edge : outgoingByFrom.getOrDefault(acyclic.poll(), List.of())) {
                if (indegree.merge(edge.getToStageCode(), -1, Integer::sum) == 0) acyclic.add(edge.getToStageCode());
            }
        }
        if (indegree.values().stream().anyMatch(value -> value > 0)) throw new IllegalArgumentException("冻结计划路径存在循环");
        // Demo 页面9 / Excel 3.1：建议最迟完成按签约方式维护的倒排规则解析（V355 配置），
        // 无验收带入或参照断链回退工期要求锚（规则涉及阶段必有建议）；
        // 未覆盖的阶段回退阶段既有建议；各阶段计划起止仍为逐行直接输入
        var orderedFacts = new java.util.ArrayList<cn.iocoder.yudao.module.pms.project.domain.projectschedule.StageSuggestionRules.StageFacts>();
        for (var definition : ordered) {
            var stage = stages.stream().filter(value -> definition.getCode().equals(value.getCode())).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("参与计划的阶段实例不存在"));
            orderedFacts.add(new cn.iocoder.yudao.module.pms.project.domain.projectschedule.StageSuggestionRules.StageFacts(
                    stage.getCode(), stage.getAcceptanceTime() == null ? null : stage.getAcceptanceTime().toLocalDate()));
        }
        var ruleQuery = new cn.iocoder.yudao.module.pms.project.dal.mysql.projectschedule.query.StageSuggestionRuleListQuery();
        ruleQuery.setSigningMethod(project.getSigningMethod());
        var adviceEnds = cn.iocoder.yudao.module.pms.project.domain.projectschedule.StageSuggestionRules.resolveAdviceEnds(
                orderedFacts, suggestionRuleMapper.selectActiveRules(ruleQuery).stream()
                        .map(rule -> new cn.iocoder.yudao.module.pms.project.domain.projectschedule.StageSuggestionRules.RuleFacts(
                                rule.getStageCode(), rule.getSigningMethod(), rule.getSourceType(), rule.getReferenceStageCode(),
                                rule.getOffsetMonths(), rule.getOffsetDays(), Boolean.TRUE.equals(rule.getEnabled())))
                        .toList(),
                project.getSigningMethod(),
                // 工期要求锚点：工勘要求结束日期（Demo 工前准备带入）未登记时回退计划域本版工期（倒排截止）；
                // 无验收带入或参照断链时该锚同样是兜底锚
                project.getProjectEndDate() != null ? project.getProjectEndDate() : end);
        // Demo 3.1：各阶段计划起止为逐行直接输入；不再按工期占比分配
        var inputStages = new java.util.ArrayList<java.util.Map<String, Object>>();
        var planDates = new java.util.ArrayList<StagePlanDate>();
        for (var definition : ordered) {
            var stage = stages.stream().filter(value -> definition.getCode().equals(value.getCode())).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("参与计划的阶段实例不存在"));
            var taskInputs = new java.util.ArrayList<java.util.Map<String, Object>>();
            for (var task : tasks) {
                if (!stage.getCode().equals(task.getStageCode())) continue;
                var taskInput = new java.util.LinkedHashMap<String, Object>();
                taskInput.put("taskId", task.getId()); taskInput.put("taskCode", task.getCode());
                taskInput.put("acceptanceTime", task.getAcceptanceTime() == null ? null : task.getAcceptanceTime().toString()); taskInput.put("version", task.getVersion());
                taskInputs.add(taskInput);
            }
            java.time.LocalDate deadline = stage.getAcceptanceTime() == null ? null : stage.getAcceptanceTime().toLocalDate();
            var input = new java.util.LinkedHashMap<String, Object>();
            input.put("stageId", stage.getId()); input.put("stageCode", stage.getCode());
            input.put("acceptanceTime", stage.getAcceptanceTime() == null ? null : stage.getAcceptanceTime().toString());
            input.put("tasks", taskInputs); inputStages.add(input);
            var adviceEnd = adviceEnds.get(stage.getCode());
            if (adviceEnd == null) adviceEnd = stage.getSuggestedEndTime() == null ? null : stage.getSuggestedEndTime().toLocalDate();
            // 验收日与规则/既有建议比对：有验收的节点不直接以验收日为结束，建议结束不得晚于计划验收时间
            if (deadline != null && adviceEnd != null && adviceEnd.isAfter(deadline))
                throw new IllegalArgumentException(stage.getCode() + " 计划结束晚于计划验收时间，请调整工期配置");
            planDates.add(new StagePlanDate(stage.getId(),
                    stage.getSuggestedStartTime() == null ? null : stage.getSuggestedStartTime().toLocalDate(), adviceEnd));
        }
        var inputs = new java.util.LinkedHashMap<String, Object>();
        inputs.put("sourcePlanVersionId", plan.getId()); inputs.put("signingMethod", project.getSigningMethod());
        inputs.put("durationStart", start); inputs.put("durationEnd", end);
        inputs.put("stages", inputStages);
        inputs.put("pathConditions", pathInputs);
        // 冻结路径的串行边进入快照：计划校验按模板准入要求判定次序，串行边允许当天首尾衔接，并行分支（无边）允许重叠
        var stageIdByCode = stages.stream().collect(java.util.stream.Collectors.toMap(
                cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectStageInstanceDO::getCode,
                cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectStageInstanceDO::getId, (a, b) -> a));
        var serialEdges = new java.util.ArrayList<java.util.Map<String, Object>>();
        for (var edge : usableEdges) {
            var edgeInput = new java.util.LinkedHashMap<String, Object>();
            edgeInput.put("fromStageId", stageIdByCode.get(edge.getFromStageCode()));
            edgeInput.put("toStageId", stageIdByCode.get(edge.getToStageCode()));
            serialEdges.add(edgeInput);
        }
        inputs.put("serialEdges", serialEdges);
        return new ScheduleCalculation(plan.getId(), start, end, planDates,
                cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(inputs));
    }

    private static boolean conditional(TemplateExecutionSnapshot.TransitionContract edge) {
        return edge.getConditionRuleKey() != null && !edge.getConditionRuleKey().isBlank()
                || edge.getConditionRule() != null && !edge.getConditionRule().isNull();
    }

    private List<cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot.TransitionContract> resolveTargets(
            TemplateExecutionSnapshot snapshot,
            cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO project,
            List<ProjectStageInstanceDO> stages,
            List<cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskInstanceDO> tasks,
            String code, List<java.util.Map<String, Object>> pathInputs) {
        var matched = new java.util.ArrayList<cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot.TransitionContract>();
        var defaults = new java.util.ArrayList<cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot.TransitionContract>();
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
            if (defaults.size() == 1) return List.of(defaults.getFirst());
            throw new IllegalArgumentException("冻结路径无匹配分支：" + code);
        }
        // 并行分支全部纳入推算；priority 升序仅决定推算顺序（稳定排序保持快照内相对次序）
        return matched.stream()
                .sorted(java.util.Comparator.comparingInt(cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot.TransitionContract::getPriority))
                .toList();
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
