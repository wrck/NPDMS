package cn.iocoder.yudao.module.pms.project.domain.projectmanual;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectGateInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectGateReferenceInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMilestoneInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectStageInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.taskworkbench.ProjectTaskTreePathDO;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateDefinitionContent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * 模板实例化器（F-PM01 / BR-4：按创建时绑定版本实例化，模板后续新版本不影响已建项目）
 * <p>
 * 纯函数：输入 F-PM03 冻结版本内容 + 项目ID，输出实例 DO + 门禁引用行。
 * 初始状态：唯一S0阶段 ACTIVE、其余 PENDING；
 * 任务 PENDING_ASSIGN；里程碑/交付件/门禁 PENDING；交付件 required 冻结快照；
 * 门禁 validation_summary 由引用行生成摘要（`TYPE:code;` 精简拼接，≤1000 字符）。
 */
public final class TemplateInstantiator {

    /** validation_summary 列宽上限（V57 VARCHAR(1000)） */
    public static final int VALIDATION_SUMMARY_MAX_LENGTH = 1000;

    private TemplateInstantiator() {
    }

    /**
     * 按冻结状态机版本实例化任务结构。任务ID在落库前确定，以便根任务可一次写入自引用root_task_id。
     */
    public static ProjectInstantiation instantiate(TemplateDefinitionContent content, Long projectId,
                                                   Long stateMachineRevisionId, LongSupplier taskIdSupplier) {
        if (stateMachineRevisionId == null || stateMachineRevisionId <= 0) {
            throw new IllegalArgumentException("任务状态机版本不能为空");
        }
        Objects.requireNonNull(taskIdSupplier, "任务ID生成器不能为空");
        requireSingleS0(content);
        ProjectInstantiation instantiation = new ProjectInstantiation();
        instantiateStages(content, projectId, instantiation);
        instantiateTasks(content, projectId, instantiation, stateMachineRevisionId, taskIdSupplier);
        instantiateMilestones(content, projectId, instantiation);
        instantiateGates(content, projectId, instantiation);
        return instantiation;
    }

    /** V1.8正式创建必须从唯一S0阶段开始。 */
    public static void requireSingleS0(TemplateDefinitionContent content) {
        if (content == null || content.getStages() == null
                || content.getStages().stream().filter(Objects::nonNull)
                .filter(stage -> ProjectRules.STATUS_S0.equals(stage.getStageCode())).count() != 1) {
            throw new IllegalArgumentException("模板必须包含且仅包含一个S0初始阶段");
        }
    }

    private static void instantiateStages(TemplateDefinitionContent content, Long projectId,
                                          ProjectInstantiation instantiation) {
        List<TemplateDefinitionContent.StageDef> stages = content.getStages();
        if (stages == null || stages.isEmpty()) {
            return;
        }
        for (TemplateDefinitionContent.StageDef stage : stages) {
            if (stage == null) {
                continue;
            }
            ProjectStageInstanceDO instance = new ProjectStageInstanceDO();
            instance.setProjectId(projectId);
            instance.setStageCode(stage.getStageCode());
            instance.setName(stage.getName());
            instance.setSortOrder(stage.getSortOrder());
            instance.setEntryCriteria(stage.getEntryCriteria());
            instance.setExitCriteria(stage.getExitCriteria());
            instance.setSourceDefinitionId(null);
            instance.setStatus(ProjectRules.STATUS_S0.equals(stage.getStageCode())
                    ? ProjectRules.STAGE_STATUS_ACTIVE
                    : ProjectRules.STAGE_STATUS_PENDING);
            instantiation.getStages().add(instance);
        }
    }

    private static void instantiateTasks(TemplateDefinitionContent content, Long projectId,
                                         ProjectInstantiation instantiation, Long stateMachineRevisionId,
                                         LongSupplier taskIdSupplier) {
        List<TemplateDefinitionContent.TaskDef> tasks = content.getTasks();
        if (tasks == null) {
            return;
        }
        for (TemplateDefinitionContent.TaskDef task : tasks) {
            if (task == null) {
                continue;
            }
            ProjectTaskInstanceDO instance = new ProjectTaskInstanceDO();
            instance.setId(taskIdSupplier.getAsLong());
            instance.setStateMachineRevisionId(stateMachineRevisionId);
            instance.setProjectId(projectId);
            instance.setTaskCode(task.getTaskCode());
            instance.setName(task.getName());
            instance.setParentTaskCode(task.getParentTaskCode());
            instance.setStageCode(task.getStageCode());
            instance.setPriority(task.getPriority());
            instance.setSortOrder(task.getSortOrder());
            instance.setEstimatedHours(task.getEstimatedHours());
            instance.setSatisfactionTiming(task.getSatisfactionTiming());
            instance.setDescription(task.getDescription());
            instance.setSourceDefinitionId(task.getId());
            instance.setStatus(ProjectRules.TASK_STATUS_PENDING_ASSIGN);
            instantiation.getTasks().add(instance);
        }
        buildTaskStructure(instantiation);
    }

    private static void buildTaskStructure(ProjectInstantiation instantiation) {
        Map<String, ProjectTaskInstanceDO> tasksByCode = new LinkedHashMap<>();
        Map<String, Integer> taskOrderByCode = new LinkedHashMap<>();
        for (ProjectTaskInstanceDO task : instantiation.getTasks()) {
            if (task.getTaskCode() == null || task.getTaskCode().isBlank()
                    || tasksByCode.putIfAbsent(task.getTaskCode(), task) != null) {
                throw new IllegalArgumentException("模板任务码不能为空且必须唯一");
            }
            taskOrderByCode.put(task.getTaskCode(), taskOrderByCode.size());
        }
        Map<String, Integer> resolveStates = new LinkedHashMap<>();
        for (ProjectTaskInstanceDO task : instantiation.getTasks()) {
            resolveTaskStructure(task, tasksByCode, resolveStates, instantiation);
        }
        instantiation.getTasks().sort(Comparator
                .comparingInt((ProjectTaskInstanceDO task) -> task.getTreeDepth())
                .thenComparingInt(task -> taskOrderByCode.get(task.getTaskCode())));
    }

    private static void resolveTaskStructure(ProjectTaskInstanceDO task,
                                             Map<String, ProjectTaskInstanceDO> tasksByCode,
                                             Map<String, Integer> resolveStates,
                                             ProjectInstantiation instantiation) {
        Integer state = resolveStates.get(task.getTaskCode());
        if (Integer.valueOf(2).equals(state)) {
            return;
        }
        if (Integer.valueOf(1).equals(state)) {
            throw new IllegalArgumentException("模板任务父子关系存在循环：" + task.getTaskCode());
        }
        resolveStates.put(task.getTaskCode(), 1);
        String parentTaskCode = task.getParentTaskCode();
        if (parentTaskCode == null || parentTaskCode.isBlank()) {
            task.setParentTaskId(null);
            task.setRootTaskId(task.getId());
            task.setTreeDepth(0);
        } else {
            ProjectTaskInstanceDO parent = tasksByCode.get(parentTaskCode);
            if (parent == null) {
                throw new IllegalArgumentException("模板任务父节点不存在：" + parentTaskCode);
            }
            resolveTaskStructure(parent, tasksByCode, resolveStates, instantiation);
            task.setParentTaskId(parent.getId());
            task.setRootTaskId(parent.getRootTaskId());
            task.setTreeDepth(parent.getTreeDepth() + 1);
            instantiation.getTaskTreePaths().stream()
                    .filter(path -> parent.getId().equals(path.getDescendantTaskId()))
                    .toList().forEach(path -> instantiation.getTaskTreePaths().add(
                            taskTreePath(task.getProjectId(), path.getAncestorTaskId(), task.getId(),
                                    path.getDistance() + 1)));
        }
        instantiation.getTaskTreePaths().add(taskTreePath(
                task.getProjectId(), task.getId(), task.getId(), 0));
        resolveStates.put(task.getTaskCode(), 2);
    }

    private static ProjectTaskTreePathDO taskTreePath(Long projectId, Long ancestorTaskId,
                                                       Long descendantTaskId, int distance) {
        ProjectTaskTreePathDO path = new ProjectTaskTreePathDO();
        path.setProjectId(projectId);
        path.setAncestorTaskId(ancestorTaskId);
        path.setDescendantTaskId(descendantTaskId);
        path.setDistance(distance);
        path.setVersion(0);
        return path;
    }

    private static void instantiateMilestones(TemplateDefinitionContent content, Long projectId,
                                              ProjectInstantiation instantiation) {
        List<TemplateDefinitionContent.MilestoneDef> milestones = content.getMilestones();
        if (milestones == null) {
            return;
        }
        for (TemplateDefinitionContent.MilestoneDef milestone : milestones) {
            if (milestone == null) {
                continue;
            }
            ProjectMilestoneInstanceDO instance = new ProjectMilestoneInstanceDO();
            instance.setProjectId(projectId);
            instance.setMilestoneCode(milestone.getMilestoneCode());
            instance.setName(milestone.getName());
            instance.setStageCode(milestone.getStageCode());
            instance.setTiming(milestone.getTiming());
            instance.setCriteria(milestone.getCriteria());
            instance.setSourceDefinitionId(null);
            instance.setStatus(ProjectRules.MILESTONE_STATUS_PENDING);
            instantiation.getMilestones().add(instance);
        }
    }

    private static void instantiateGates(TemplateDefinitionContent content, Long projectId,
                                         ProjectInstantiation instantiation) {
        List<TemplateDefinitionContent.GateDef> gates = content.getGates();
        if (gates == null) {
            return;
        }
        for (TemplateDefinitionContent.GateDef gate : gates) {
            if (gate == null) {
                continue;
            }
            ProjectGateInstanceDO instance = new ProjectGateInstanceDO();
            instance.setProjectId(projectId);
            instance.setGateCode(gate.getGateCode());
            instance.setName(gate.getName());
            instance.setGateType(gate.getGateType());
            instance.setStageCode(gate.getStageCode());
            instance.setDescription(gate.getDescription());
            instance.setValidationSummary(buildValidationSummary(gate.getReferences()));
            instance.setSourceDefinitionId(null);
            instance.setStatus(ProjectRules.GATE_STATUS_PENDING);
            instantiation.getGates().add(instance);
            // 引用行结构化三元组冻结复制（gate_id 为落库回填槽位）
            if (gate.getReferences() != null) {
                List<ProjectGateReferenceInstanceDO> references = instantiation
                        .getGateReferencesByGateCode()
                        .computeIfAbsent(gate.getGateCode(), code -> new ArrayList<>());
                for (TemplateDefinitionContent.GateRef ref : gate.getReferences()) {
                    if (ref == null) {
                        continue;
                    }
                    ProjectGateReferenceInstanceDO reference = new ProjectGateReferenceInstanceDO();
                    reference.setGateId(null);
                    reference.setRefType(ref.getRefType());
                    reference.setRefCode(ref.getRefCode());
                    reference.setRefVersion(ref.getRefVersion());
                    references.add(reference);
                }
            }
        }
    }

    /**
     * 校验内容摘要：`TYPE:code;` 精简拼接（如 `TASK:T1;DELIVERABLE:D1`），超过 1000 字符截断。
     */
    static String buildValidationSummary(List<TemplateDefinitionContent.GateRef> references) {
        if (references == null || references.isEmpty()) {
            return null;
        }
        StringBuilder summary = new StringBuilder();
        for (TemplateDefinitionContent.GateRef ref : references) {
            if (ref == null || ref.getRefType() == null || ref.getRefCode() == null) {
                continue;
            }
            if (summary.length() > 0) {
                summary.append(';');
            }
            summary.append(ref.getRefType()).append(':').append(ref.getRefCode());
        }
        if (summary.isEmpty()) {
            return null;
        }
        return summary.length() > VALIDATION_SUMMARY_MAX_LENGTH
                ? summary.substring(0, VALIDATION_SUMMARY_MAX_LENGTH)
                : summary.toString();
    }
}
