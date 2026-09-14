package cn.iocoder.yudao.module.pms.project.service.acceptance.application;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.acceptance.AccProjectDeliverableDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptance.AccProjectDeliverableMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptance.query.ProjectDeliverableIdLockQuery;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;

/** ACC交付件初始化；必须参与PROJ已开启的同库事务。 */
@Service
public class ProjectDeliverableInitializationApplicationServiceImpl
        implements ProjectDeliverableInitializationApplicationService {

    @Resource
    private AccProjectDeliverableMapper mapper;

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public DeliverableInitializationResult initialize(InitializeProjectDeliverablesCommand command) {
        if (command == null || command.projectId() == null || command.templateRevisionId() == null
                || command.definitions() == null) {
            throw new IllegalArgumentException("交付件初始化命令不完整");
        }
        List<AccProjectDeliverableDO> rows = command.definitions().stream()
                .map(definition -> toDataObject(command.projectId(), definition))
                .toList();
        if (!rows.isEmpty() && !Boolean.TRUE.equals(mapper.insertBatch(rows))) {
            throw new IllegalStateException("ACC交付件批量写入失败");
        }
        long persisted = mapper.selectCountByProjectId(command.projectId());
        if (persisted != rows.size()) {
            throw new IllegalStateException("ACC交付件初始化数量不完整");
        }
        return new DeliverableInitializationResult(rows.size(), Math.toIntExact(persisted));
    }

    @Override
    public List<DeliverableView> getByProjectId(Long projectId) {
        if (projectId == null) {
            throw new IllegalArgumentException("项目ID不能为空");
        }
        return mapper.selectListByProjectId(projectId).stream().map(this::view).toList();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public DeliverablePlanState inspectPlanDefinitions(Long projectId) {
        if (projectId == null || projectId <= 0) throw new IllegalArgumentException("项目ID不能为空");
        var query = new cn.iocoder.yudao.module.pms.project.dal.mysql.acceptance.query.ProjectDeliverablePlanScopeQuery(
                TenantContextHolder.getRequiredTenantId(), projectId);
        var definitions = mapper.selectPlanDefinitionsForUpdate(query).stream().map(this::view).toList();
        return new DeliverablePlanState(definitions, java.util.Set.copyOf(mapper.selectRetirablePlanDefinitionIds(query)));
    }

    private DeliverableView view(AccProjectDeliverableDO row) {
        return new DeliverableView(row.getId(), row.getProjectId(), row.getDeliverableCode(), row.getName(),
                row.getStageCode(), row.getTaskCode(), row.getRequired(), row.getSourceDefinitionId(), row.getStatus(), row.getVersion());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void applyPlanChanges(ApplyDeliverablePlanChanges command) {
        if (command == null || command.projectId() == null || command.projectId() <= 0
                || command.actorId() == null || command.actorId() <= 0 || command.changes() == null)
            throw new IllegalArgumentException("交付件改版命令不完整");
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        var ids = new HashSet<Long>(); var codes = new HashSet<String>();
        var existing = new ArrayList<AccProjectDeliverableMapper.PlanDefinitionChange>();
        var renamed = new ArrayList<AccProjectDeliverableMapper.PlanDefinitionChange>();
        var added = new ArrayList<AccProjectDeliverableDO>();
        // Validate before any write; locking in id order agrees with Owner record operations.
        for (var change : command.changes()) {
            if (change == null || (change.id() == null && (change.definition() == null || change.expectedVersion() != null))
                    || (change.id() != null && (change.id() <= 0 || change.expectedVersion() == null
                        || change.expectedVersion() < 0 || !ids.add(change.id()))))
                throw new IllegalArgumentException("交付件改版身份无效");
            if (change.definition() != null) {
                var definition = change.definition();
                if (definition.deliverableCode() == null || definition.deliverableCode().isBlank()
                        || definition.name() == null || definition.name().isBlank()
                        || definition.stageCode() == null || definition.stageCode().isBlank()
                        || !codes.add(definition.deliverableCode())) throw new IllegalArgumentException("交付件改版定义无效");
            }
        }
        var ordered = command.changes().stream().sorted(java.util.Comparator.comparing(
                DeliverablePlanChange::id, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))).toList();
        for (var change : ordered) {
            var desired = change.definition() == null ? null : toDataObject(command.projectId(), change.definition());
            if (desired != null) {
                desired.setTenantId(tenantId); desired.setCreator(command.actorId().toString()); desired.setUpdater(command.actorId().toString());
            }
            if (change.id() == null) { added.add(desired); continue; }
            var current = mapper.selectByIdForUpdate(new ProjectDeliverableIdLockQuery(tenantId, change.id()));
            if (current == null || !Objects.equals(tenantId, current.getTenantId())
                    || !Objects.equals(command.projectId(), current.getProjectId()) || !Objects.equals(change.expectedVersion(), current.getVersion()))
                throw new IllegalStateException("DELIVERABLE_PLAN_VERSION_CONFLICT");
            var update = new AccProjectDeliverableMapper.PlanDefinitionChange(tenantId, command.projectId(), change.id(),
                    change.expectedVersion(), desired, command.actorId().toString());
            existing.add(update);
            if (desired != null && !Objects.equals(current.getDeliverableCode(), desired.getDeliverableCode())) renamed.add(update);
        }
        for (var change : existing) {
            if (change.definition() == null && mapper.retireUnhandledForPlan(change) != 1)
                throw new IllegalStateException("DELIVERABLE_PLAN_HANDLING_HISTORY_PROTECTED");
        }
        for (var change : renamed) requirePlanWrite(mapper.stagePlanCodeForRename(change));
        for (var change : existing) if (change.definition() != null) requirePlanWrite(mapper.updatePlanDefinition(change));
        for (var row : added) requirePlanWrite(mapper.insert(row));
    }

    private void requirePlanWrite(int affected) {
        if (affected != 1) throw new IllegalStateException("DELIVERABLE_PLAN_VERSION_CONFLICT");
    }

    private AccProjectDeliverableDO toDataObject(Long projectId, DeliverableDefinition definition) {
        if (definition == null || definition.deliverableCode() == null || definition.name() == null
                || definition.stageCode() == null) {
            throw new IllegalArgumentException("交付件定义不完整");
        }
        AccProjectDeliverableDO row = new AccProjectDeliverableDO();
        row.setProjectId(projectId);
        row.setDeliverableCode(definition.deliverableCode());
        row.setName(definition.name());
        row.setStageCode(definition.stageCode());
        row.setTaskCode(definition.taskCode());
        row.setRequired(definition.required());
        row.setSourceDefinitionId(definition.sourceDefinitionId());
        row.setStatus("PENDING");
        row.setVersion(0);
        return row;
    }
}
