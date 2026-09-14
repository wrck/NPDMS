package cn.iocoder.yudao.module.pms.project.service.projectplan;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMilestoneInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMilestoneInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectPlanProjectionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.query.ProjectPlanScopeQuery;
import cn.iocoder.yudao.module.pms.project.domain.projectmanual.TemplateInstantiator;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import cn.iocoder.yudao.module.pms.project.service.deliveryconfiguration.DeliveryDefinitionModels.Issue;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_PLAN_VERSION_CONFLICT;

/** Current milestone definitions only. Completed evidence and frozen project plans are never rewritten. */
@Service
@RequiredArgsConstructor
public class ProjectPlanMilestoneInstaller {
    private final ProjectPlanProjectionMapper projections;
    private final ProjectMilestoneInstanceMapper milestones;
    public record Change(String nodeKey, String action, Long instanceId, Integer expectedVersion,
                         String fromCode, String toCode) { }
    public record Write(Change change, ProjectMilestoneInstanceDO definition) { }
    public record Plan(List<Change> changes, List<Write> writes, List<Issue> issues) { }

    /** Caller holds the authorized project lock and compares the resulting version tokens before apply. */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public Plan inspect(ProjectPlanScopeQuery scope, TemplateExecutionSnapshot before, TemplateExecutionSnapshot after) {
        return plan(scope, before, after, projections.selectMilestonesForUpdate(scope));
    }

    Plan plan(ProjectPlanScopeQuery scope, TemplateExecutionSnapshot before, TemplateExecutionSnapshot after,
              List<ProjectMilestoneInstanceDO> current) {
        var oldNodes = before.getMilestones().stream().collect(Collectors.toMap(
                TemplateExecutionSnapshot.MilestoneContract::getNodeKey, Function.identity()));
        var nextNodes = after.getMilestones().stream().collect(Collectors.toMap(
                TemplateExecutionSnapshot.MilestoneContract::getNodeKey, Function.identity()));
        Map<String, ProjectMilestoneInstanceDO> rows = new HashMap<>();
        List<Issue> issues = new ArrayList<>();
        for (var row : current) {
            if (!Objects.equals(scope.tenantId(), row.getTenantId()) || !Objects.equals(scope.projectId(), row.getProjectId())
                    || row.getId() == null || row.getVersion() == null || rows.putIfAbsent(row.getMilestoneCode(), row) != null)
                issues.add(issue("$milestones", "MILESTONE_RUNTIME_CONFLICT", "里程碑实例身份或版本不一致"));
        }
        if (!issues.isEmpty()) return new Plan(List.of(), List.of(), List.copyOf(issues));
        var desired = TemplateInstantiator.instantiateMilestones(after.toRuntimeContent(), scope.projectId()).stream()
                .collect(Collectors.toMap(ProjectMilestoneInstanceDO::getMilestoneCode, Function.identity()));
        List<Write> writes = new ArrayList<>();
        Set<Long> managed = new HashSet<>();
        for (var old : before.getMilestones()) {
            var row = rows.get(old.getCode());
            if (row == null) {
                issues.add(issue(old.getNodeKey(), "MILESTONE_RUNTIME_MISSING", "有效计划里程碑缺少实例，不能通过改版静默重建"));
                continue;
            }
            managed.add(row.getId());
            var next = nextNodes.get(old.getNodeKey());
            if (next == null) {
                if (!"PENDING".equals(row.getStatus()))
                    issues.add(issue(old.getNodeKey(), "ACHIEVED_MILESTONE_DELETE_FORBIDDEN", "已达成里程碑不能直接删除，原有结果必须保留"));
                writes.add(new Write(new Change(old.getNodeKey(), "REMOVE", row.getId(), row.getVersion(), row.getMilestoneCode(), null), null));
                continue;
            }
            var target = desired.get(next.getCode());
            target.setName(choose(old.getName(), next.getName(), row.getName()));
            target.setStageCode(choose(old.getStageCode(), next.getStageCode(), row.getStageCode()));
            target.setTiming(choose(old.getTiming(), next.getTiming(), row.getTiming()));
            target.setCriteria(choose(old.getCriteria(), next.getCriteria(), row.getCriteria()));
            if (!metadata(row).equals(metadata(target)))
                writes.add(new Write(new Change(old.getNodeKey(), "UPDATE", row.getId(), row.getVersion(), row.getMilestoneCode(), target.getMilestoneCode()), target));
        }
        for (var next : after.getMilestones()) {
            var occupant = rows.get(next.getCode());
            if (occupant != null && !managed.contains(occupant.getId()))
                issues.add(issue(next.getNodeKey(), "MILESTONE_CODE_OCCUPIED", "编码被计划外的里程碑占用，不能覆盖该记录"));
            if (!oldNodes.containsKey(next.getNodeKey()))
                writes.add(new Write(new Change(next.getNodeKey(), "ADD", null, null, null, next.getCode()), desired.get(next.getCode())));
        }
        return new Plan(writes.stream().map(Write::change).toList(), List.copyOf(writes), List.copyOf(issues));
    }

    /** Part of the same authorized plan transaction; the caller supplies the freshly inspected plan. */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void install(ProjectPlanScopeQuery scope, Plan plan, Long actorId) {
        if (!plan.issues().isEmpty()) throw exception(PROJECT_PLAN_VERSION_CONFLICT);
        for (var write : plan.writes()) if (write.definition() == null)
            requireOne(projections.retirePendingMilestone(change(scope, write.change(), actorId)));
        for (var write : plan.writes()) if ("UPDATE".equals(write.change().action())
                && !Objects.equals(write.change().fromCode(), write.change().toCode()))
            requireOne(projections.milestoneCodeForRename(change(scope, write.change(), actorId)));
        for (var write : plan.writes()) {
            if (write.definition() == null) continue;
            if (write.change().instanceId() == null) {
                var row = write.definition(); row.setTenantId(scope.tenantId()); row.setProjectId(scope.projectId());
                row.setVersion(0); row.setCreator(actorId.toString()); row.setUpdater(actorId.toString());
                requireOne(milestones.insert(row));
            } else requireOne(projections.updateMilestoneDefinition(new ProjectPlanProjectionMapper.MilestoneDefinitionUpdate(
                    scope.tenantId(), scope.projectId(), write.change().instanceId(), write.change().expectedVersion(), write.definition(), actorId.toString())));
        }
    }

    private ProjectPlanProjectionMapper.NodeProjectionChange change(ProjectPlanScopeQuery scope, Change change, Long actorId) {
        return new ProjectPlanProjectionMapper.NodeProjectionChange(scope.tenantId(), scope.projectId(), change.instanceId(), change.expectedVersion(), actorId.toString());
    }
    private static List<?> metadata(ProjectMilestoneInstanceDO row) {
        return Arrays.asList(row.getMilestoneCode(), row.getName(), row.getStageCode(), row.getTiming(), row.getCriteria());
    }
    private static <T> T choose(T before, T after, T actual) { return Objects.equals(before, after) ? actual : after; }
    private static Issue issue(String key, String code, String message) { return new Issue("nodes." + key, code, message); }
    private void requireOne(int count) { if (count != 1) throw exception(PROJECT_PLAN_VERSION_CONFLICT); }
}
