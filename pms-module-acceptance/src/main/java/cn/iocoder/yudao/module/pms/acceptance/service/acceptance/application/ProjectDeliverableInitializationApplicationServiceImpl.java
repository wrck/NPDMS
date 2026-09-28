package cn.iocoder.yudao.module.pms.acceptance.service.acceptance.application;

import cn.iocoder.yudao.module.pms.acceptance.api.deliverable.ProjectDeliverableInitializationApplicationService;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** ACC交付件初始化（统一交付件平台 TEMPLATE_FROZEN 承接）；必须参与PROJ已开启的同库事务。 */
@Service
public class ProjectDeliverableInitializationApplicationServiceImpl
        implements ProjectDeliverableInitializationApplicationService {

    @Resource
    private PlatformDeliveryRequirementApi platform;

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public DeliverableInitializationResult initialize(InitializeProjectDeliverablesCommand command) {
        if (command == null || command.projectId() == null || command.templateRevisionId() == null
                || command.definitions() == null) {
            throw new IllegalArgumentException("交付件初始化命令不完整");
        }
        List<Long> ids = platform.instantiateTemplateFrozen(command.projectId(), null,
                command.definitions().stream().map(this::definition).toList());
        return new DeliverableInitializationResult(command.definitions().size(), ids.size());
    }

    @Override
    public List<DeliverableView> getByProjectId(Long projectId) {
        if (projectId == null) {
            throw new IllegalArgumentException("项目ID不能为空");
        }
        return platform.listByProject(projectId).stream().map(this::view).toList();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public DeliverablePlanState inspectPlanDefinitions(Long projectId) {
        if (projectId == null || projectId <= 0) throw new IllegalArgumentException("项目ID不能为空");
        var rows = platform.lockByProject(projectId);
        List<DeliverableView> definitions = rows.stream().map(this::view).toList();
        // 退役资格与平台护栏同口径：有任何材料或提交历史（含已撤回）即不可退役。
        Set<Long> retirable = new HashSet<>();
        for (var row : rows) {
            if (platform.listMaterials(row.id()).isEmpty() && platform.listSubmissions(row.id()).isEmpty()) {
                retirable.add(row.id());
            }
        }
        return new DeliverablePlanState(definitions, retirable);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void applyPlanChanges(ApplyDeliverablePlanChanges command) {
        if (command == null || command.projectId() == null || command.projectId() <= 0
                || command.actorId() == null || command.actorId() <= 0 || command.changes() == null)
            throw new IllegalArgumentException("交付件改版命令不完整");
        // Validate before any write; locking in id order agrees with Owner record operations.
        var ids = new HashSet<Long>(); var codes = new HashSet<String>();
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
        var updates = new ArrayList<DeliverablePlanChange>();
        var renamed = new ArrayList<Long>();
        var added = new ArrayList<DeliverablePlanChange>();
        for (var change : ordered) {
            if (change.id() == null) { added.add(change); continue; }
            if (change.definition() == null) {
                // 改版退役：平台护栏拒绝任何有材料/提交历史的实例；沿用旧契约在应用期失败而非静默保留。
                if (platform.retireUnhandled(List.of(change.id())) != 1)
                    throw new IllegalStateException("DELIVERABLE_PLAN_HANDLING_HISTORY_PROTECTED");
                continue;
            }
            updates.add(change);
            renamed.add(change.id());
        }
        // 编码改名暂存先腾空目标编码（平台内支持同批互换），再按乐观版本落地最终定义，最后新增。
        if (!renamed.isEmpty()) platform.stageTemplateFrozenCodes(renamed);
        for (var change : updates) {
            var d = change.definition();
            platform.updateTemplateFrozen(change.id(),
                    change.expectedVersion() == null ? null : change.expectedVersion().intValue(),
                    new PlatformDeliveryRequirementApi.TemplateFrozenDefinition(d.deliverableCode(), d.name(),
                            d.stageCode(), d.taskCode(), d.required(), 0, d.sourceDefinitionId(), null));
        }
        if (!added.isEmpty()) {
            platform.instantiateTemplateFrozen(command.projectId(), null, added.stream()
                    .map(change -> definition(change.definition())).toList());
        }
    }

    private DeliverableView view(PlatformDeliveryRequirementApi.TemplateFrozenView row) {
        // 旧实例视图契约（pms_project_deliverable_status 字典：PENDING/SUBMITTED/ACCEPTED）；
        // 统一要求状态 OPEN/SATISFIED/CONFIRMED 在此适配为旧字典，实例视图与任务工作台共用本口径。
        String legacyStatus = PlatformDeliveryRequirementApi.STATUS_OPEN.equals(row.status())
                ? "PENDING" : "ACCEPTED";
        return new DeliverableView(row.id(), row.projectId(), row.deliverableCode(), row.name(),
                row.stageCode(), row.taskCode(), row.required(), row.sourceDefinitionId(), legacyStatus,
                row.version() == null ? null : row.version().longValue());
    }

    private PlatformDeliveryRequirementApi.TemplateFrozenDefinition definition(DeliverableDefinition definition) {
        if (definition == null || definition.deliverableCode() == null || definition.name() == null
                || definition.stageCode() == null) {
            throw new IllegalArgumentException("交付件定义不完整");
        }
        return new PlatformDeliveryRequirementApi.TemplateFrozenDefinition(definition.deliverableCode(),
                definition.name(), definition.stageCode(), definition.taskCode(), definition.required(), 0,
                definition.sourceDefinitionId(), null);
    }
}
