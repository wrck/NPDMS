package cn.iocoder.yudao.module.pms.project.service.projectplan;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectNodeExecutionDO;
import cn.iocoder.yudao.module.pms.project.domain.projectmanual.ProjectInstantiation;
import cn.iocoder.yudao.module.pms.project.domain.projectmanual.TemplateInstantiator;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateDefinitionContent;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import cn.iocoder.yudao.module.pms.project.service.deliveryconfiguration.DeliveryDefinitionModels.Issue;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Merge only project-specific parent choices; existing template instantiation owns tree validation and paths. */
final class ProjectPlanTaskHierarchy {
    record Resolution(TemplateDefinitionContent content, List<Issue> issues) { }

    static Resolution resolve(TemplateExecutionSnapshot before, TemplateExecutionSnapshot after,
                              List<ProjectNodeExecutionDO> rounds, List<ProjectTaskInstanceDO> tasks) {
        var content = after.toRuntimeContent();
        var previous = before.getTasks().stream().collect(Collectors.toMap(
                TemplateExecutionSnapshot.TaskContract::getNodeKey, Function.identity()));
        var oldKeysByCode = before.getTasks().stream().collect(Collectors.toMap(
                TemplateExecutionSnapshot.TaskContract::getCode, TemplateExecutionSnapshot.TaskContract::getNodeKey));
        var newKeysByCode = after.getTasks().stream().collect(Collectors.toMap(
                TemplateExecutionSnapshot.TaskContract::getCode, TemplateExecutionSnapshot.TaskContract::getNodeKey));
        var nextByKey = content.getTasks().stream().collect(Collectors.toMap(
                TemplateDefinitionContent.TaskDef::getSourceNodeKey, Function.identity()));
        Map<String, Long> idsByKey = new HashMap<>();
        Map<Long, String> keysById = new HashMap<>();
        rounds.stream().filter(row -> "TASK".equals(row.getNodeKind())).forEach(row -> {
            idsByKey.put(row.getNodeKey(), row.getNodeInstanceId());
            keysById.put(row.getNodeInstanceId(), row.getNodeKey());
        });
        var actual = tasks.stream().collect(Collectors.toMap(ProjectTaskInstanceDO::getId, Function.identity()));
        List<Issue> issues = new ArrayList<>();
        for (var next : content.getTasks()) {
            var old = previous.get(next.getSourceNodeKey());
            if (old == null) continue;
            // Compare node identities: renaming the parent is not a request to move its children.
            if (!Objects.equals(oldKeysByCode.get(old.getParentTaskCode()), newKeysByCode.get(next.getParentTaskCode())))
                continue;
            var current = actual.get(idsByKey.get(next.getSourceNodeKey()));
            if (current == null) {
                issues.add(issue(next.getSourceNodeKey(), "TASK_HIERARCHY_RUNTIME_MISSING", "当前任务层级缺少对应实例"));
                continue;
            }
            if (current.getParentTaskId() == null) {
                next.setParentTaskCode(null);
                continue;
            }
            var parent = nextByKey.get(keysById.get(current.getParentTaskId()));
            if (parent == null) {
                issues.add(issue(next.getSourceNodeKey(), "TASK_HIERARCHY_PARENT_REMOVED",
                        "当前父任务不在新计划中，请在草稿中明确调整该任务的父任务"));
                continue;
            }
            next.setParentTaskCode(parent.getTaskCode());
        }
        if (issues.isEmpty()) {
            // Reuse the normal task-tree builder, with transient IDs solely for validating the combined hierarchy.
            var structure = new ProjectInstantiation();
            long id = 1;
            for (var definition : content.getTasks()) {
                var task = new ProjectTaskInstanceDO(); task.setId(id++);
                task.setTaskCode(definition.getTaskCode()); task.setParentTaskCode(definition.getParentTaskCode());
                structure.getTasks().add(task);
            }
            try { TemplateInstantiator.buildTaskStructure(structure); }
            catch (IllegalArgumentException invalid) {
                issues.add(issue("$tasks", "TASK_HIERARCHY_CONFLICT", "草稿与当前任务层级合并后无效：" + invalid.getMessage()));
            }
        }
        return new Resolution(content, List.copyOf(issues));
    }

    private static Issue issue(String key, String code, String message) {
        return new Issue("nodes." + key + ".parentTaskCode", code, message);
    }
}
