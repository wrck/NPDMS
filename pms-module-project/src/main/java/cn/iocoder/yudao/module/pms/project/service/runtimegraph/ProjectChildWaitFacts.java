package cn.iocoder.yudao.module.pms.project.service.runtimegraph;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectChildClosureMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeHierarchyMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeVersionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.query.ProjectWaitTreeQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.query.ProjectChildClosureFactsQuery;
import cn.iocoder.yudao.module.pms.project.domain.rule.ChildProjectWaitCondition;
import cn.iocoder.yudao.module.pms.project.domain.rule.RuleFact;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import java.util.Objects;
import java.util.stream.Collectors;

/** Reads project-owned closure facts only. It never closes a parent or child. */
@Component
@RequiredArgsConstructor
public class ProjectChildWaitFacts {
    private final ProjectTreeVersionMapper versions;
    private final ProjectTreeHierarchyMapper paths;
    private final ProjectChildClosureMapper projects;

    public RuleFact resolve(ProjectMasterDO parent, JsonNode parameters) {
        try {
            var condition = ChildProjectWaitCondition.parse(parameters);
            Long rootId = parent.getRootId() == null ? parent.getId() : parent.getRootId();
            var version = versions.selectLatestActive(rootId);
            if (version == null || !Objects.equals(version.getTenantId(), parent.getTenantId()))
                return RuleFact.unknown("CHILD_TREE_UNAVAILABLE");
            var subtree = paths.selectWaitSubtree(new ProjectWaitTreeQuery(parent.getTenantId(), rootId,
                    version.getTreeVersion(), parent.getId()));
            if (subtree.stream().noneMatch(path -> Objects.equals(path.getDistance(), 0)
                    && Objects.equals(path.getDescendantProjectId(), parent.getId())))
                return RuleFact.unknown("CHILD_TREE_UNAVAILABLE");
            var ids = subtree.stream().filter(path -> path.getDistance() > 0
                    && (condition.scope() == ChildProjectWaitCondition.Scope.DESCENDANTS || path.getDistance() == 1))
                    .map(path -> path.getDescendantProjectId()).collect(Collectors.toSet());
            var children = projects.selectClosureFacts(new ProjectChildClosureFactsQuery(parent.getTenantId(), ids));
            if (children.size() != ids.size() || children.stream().anyMatch(child -> !ids.contains(child.getId())
                    || !Objects.equals(child.getTenantId(), parent.getTenantId())))
                return RuleFact.unknown("CHILD_CLOSURE_FACT_UNAVAILABLE");
            return condition.evaluate(children.stream().map(ProjectMasterDO::getLifecycleStatus).toList());
        } catch (RuntimeException unavailable) {
            return RuleFact.unknown("CHILD_CLOSURE_FACT_UNAVAILABLE");
        }
    }
}
