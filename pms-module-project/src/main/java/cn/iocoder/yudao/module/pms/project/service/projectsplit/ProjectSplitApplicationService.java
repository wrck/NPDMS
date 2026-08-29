package cn.iocoder.yudao.module.pms.project.service.projectsplit;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.commerce.api.scope.DeliveryScopeApi;
import cn.iocoder.yudao.module.pms.commerce.api.scope.dto.SplitScopeApplyCommand;
import cn.iocoder.yudao.module.pms.commerce.api.scope.dto.SplitScopeApplyResult;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeQuery;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectsplit.ProjectSplitItemDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectsplit.ProjectSplitRequestDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectsplit.ProjectSplitScopeDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.ProjectTreeChangeDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectsplit.ProjectSplitItemMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectsplit.ProjectSplitRequestMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeChangeMapper;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectChildCreationService;
import cn.iocoder.yudao.module.pms.project.service.projecttree.ProjectTreeProjectionService;
import cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeScopeService;
import cn.iocoder.yudao.module.pms.project.service.projectsplit.command.ApplyProjectSplitCommand;
import cn.iocoder.yudao.module.pms.project.service.projectsplit.command.ApplyProjectSplitResult;
import cn.iocoder.yudao.module.pms.project.service.projectsplit.command.ApplyProjectSplitResult.CreatedProject;
import cn.iocoder.yudao.module.pms.project.service.projectsplit.command.ProjectSplitPreviewCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.*;
import static cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi.ACTION_MANAGE;

@Service
@RequiredArgsConstructor
public class ProjectSplitApplicationService {
    public static final String APPLY_SCOPE = "POST:/pms/project-split-requests/{id}/actions/apply";

    private final PlatformCommandExecutionApi commandExecutionService;
    private final ProjectSplitDraftService draftService;
    private final ProjectSplitPreviewService previewService;
    private final ProjectSplitRequestMapper requestMapper;
    private final ProjectSplitItemMapper itemMapper;
    private final ProjectMasterMapper projectMapper;
    private final ProjectChildCreationService childCreationService;
    private final DeliveryScopeApi deliveryScopeApi;
    private final ProjectTreeProjectionService treeProjectionService;
    private final ProjectTreeChangeMapper treeChangeMapper;
    private final ProjectSplitMetrics metrics;
    private final ProjectTreeScopeService treeScopeService;

    public ApplyProjectSplitResult apply(ApplyProjectSplitCommand command, ProjectSplitDraftService.Actor actor) {
        validate(command, actor);
        draftService.getDraft(command.requestId(), actor);
        long started = System.nanoTime();
        try {
            var execution = commandExecutionService.execute(
                    new PlatformCommandExecutionApi.IdempotencyScope(
                            actor.tenantId(), APPLY_SCOPE, actor.actorId(), command.idempotencyKey()),
                    command.requestDigest(), ApplyProjectSplitResult.class,
                    () -> applyOnce(command, actor),
                    result -> successFacts(actor, result));
            if (execution.decision() == PlatformCommandExecutionApi.Decision.CONFLICT) {
                throw exception(PMS_IDEMPOTENCY_KEY_CONFLICT);
            }
            if (execution.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS) {
                throw exception(PMS_IDEMPOTENCY_IN_PROGRESS);
            }
            ApplyProjectSplitResult result = execution.response();
            if (execution.decision() == PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED) {
                result = new ApplyProjectSplitResult(result.requestId(), result.projects(), result.scopeVersion(),
                        result.changeBatchId(), result.treeVersion(), true);
            }
            metrics.apply(true, "none", System.nanoTime() - started);
            return result;
        } catch (RuntimeException failure) {
            metrics.apply(false, failureStage(failure), System.nanoTime() - started);
            throw failure;
        }
    }

    private ApplyProjectSplitResult applyOnce(ApplyProjectSplitCommand command,
                                               ProjectSplitDraftService.Actor actor) {
        ProjectSplitRequestDO request = requestMapper.selectByIdForUpdate(command.requestId());
        if (request == null || !Objects.equals(request.getTenantId(), actor.tenantId())
                || !"DRAFT".equals(request.getStatus())) {
            throw exception(PROJECT_SPLIT_REQUEST_NOT_EXISTS);
        }
        requireVersions(request, command);
        ProjectMasterDO parent = projectMapper.selectByIdForUpdate(request.getParentProjectId());
        if (parent == null || !Objects.equals(parent.getTenantId(), actor.tenantId())
                || !Objects.equals(parent.getVersion(), command.expectedParentVersion())) {
            throw exception(PROJECT_SPLIT_APPLY_VERSION_CONFLICT);
        }
        treeScopeService.assertFullAccess(new ProjectScopeQuery(
                actor.tenantId(), actor.actorId(), parent.getId(), ACTION_MANAGE, command.expectedTreeVersion()));
        ProjectSplitPreviewService.PreviewResult preview = previewService.preview(
                new ProjectSplitPreviewCommand(request.getId(), command.expectedDraftVersion()), actor);
        if (!preview.valid()) {
            throw exception(PROJECT_SPLIT_APPLY_INVALID, String.join(",", preview.errors()));
        }
        ProjectSplitDraftService.DraftResult draft = draftService.getDraft(request.getId(), actor);
        List<CreatedProject> created = new ArrayList<>();
        Map<String, Long> projectIds = new LinkedHashMap<>();
        Map<String, Integer> projectVersions = new LinkedHashMap<>();
        for (ProjectSplitItemDO item : draft.items()) {
            ProjectMasterDO child = childCreationService.create(parent, item, actor.tenantId(), request.getId());
            created.add(new CreatedProject(item.getClientItemKey(), child.getId(), child.getProjectCode()));
            projectIds.put(item.getClientItemKey(), child.getId());
            projectVersions.put(item.getClientItemKey(), child.getVersion());
        }
        SplitScopeApplyResult scopeResult = deliveryScopeApi.applySplit(new SplitScopeApplyCommand(
                actor.tenantId(), parent.getId(), parent.getVersion(), command.expectedScopeVersion(),
                command.idempotencyKey(), Map.copyOf(projectIds), Map.copyOf(projectVersions), allocations(draft)));
        if (!scopeResult.valid()) {
            throw exception(PROJECT_SPLIT_APPLY_INVALID, String.join(",", scopeResult.errors()));
        }
        if (scopeResult.scopeVersion() == null) {
            throw exception(PROJECT_SPLIT_APPLY_INVALID, "COMMERCE_SCOPE_VERSION_MISSING");
        }
        for (ProjectSplitItemDO item : draft.items()) {
            if (itemMapper.markApplied(actor.tenantId(), request.getId(), item.getId(),
                    projectIds.get(item.getClientItemKey())) != 1) {
                throw exception(PROJECT_SPLIT_APPLY_VERSION_CONFLICT);
            }
        }
        String changeBatchId = UUID.randomUUID().toString();
        long newTreeVersion = command.expectedTreeVersion() + 1;
        publishTreeVersion(parent, created, actor, changeBatchId, command.expectedTreeVersion(), newTreeVersion);
        if (requestMapper.markAppliedIfMatch(actor.tenantId(), request.getId(), command.expectedDraftVersion(),
                changeBatchId) != 1) {
            throw exception(PROJECT_SPLIT_APPLY_VERSION_CONFLICT);
        }
        return new ApplyProjectSplitResult(request.getId(), created, scopeResult.scopeVersion(),
                changeBatchId, newTreeVersion, false);
    }

    private List<SplitScopeApplyCommand.Allocation> allocations(ProjectSplitDraftService.DraftResult draft) {
        Map<Long, ProjectSplitItemDO> items = new HashMap<>();
        draft.items().forEach(item -> items.put(item.getId(), item));
        Map<String, AllocationAccumulator> grouped = new LinkedHashMap<>();
        for (ProjectSplitScopeDO scope : draft.scopes()) {
            ProjectSplitItemDO item = items.get(scope.getSplitItemId());
            String key = item.getClientItemKey() + "|" + scope.getOrderLineId() + "|"
                    + scope.getOfficeDepartmentCode();
            AllocationAccumulator value = grouped.computeIfAbsent(key, ignored -> new AllocationAccumulator(
                    item.getClientItemKey(), scope.getOrderLineId(), scope.getOfficeDepartmentCode()));
            value.quantity = value.quantity.add(scope.getAllocatedQty());
            if (scope.getSerialNo() != null) value.serials.add(scope.getSerialNo());
        }
        return grouped.values().stream().map(value -> new SplitScopeApplyCommand.Allocation(
                value.clientItemKey, value.orderLineId, value.quantity, value.officeCode,
                List.copyOf(value.serials))).toList();
    }

    private void publishTreeVersion(ProjectMasterDO parent, List<CreatedProject> created,
                                    ProjectSplitDraftService.Actor actor, String changeBatchId,
                                    long baseVersion, long newVersion) {
        Long rootId = parent.getRootId() == null ? parent.getId() : parent.getRootId();
        treeProjectionService.publish(rootId, newVersion, changeBatchId);
        LocalDateTime now = LocalDateTime.now();
        for (CreatedProject child : created) {
            ProjectTreeChangeDO change = new ProjectTreeChangeDO();
            change.setChangeBatchId(changeBatchId); change.setOperationType("SPLIT_CREATE");
            change.setProjectId(child.projectId()); change.setParentIdAfter(parent.getId());
            change.setBaseTreeVersion(baseVersion); change.setNewTreeVersion(newVersion);
            change.setActorId(actor.actorId()); change.setReason("PROJECT_SPLIT_APPLY");
            change.setOccurredAt(now); change.setVersion(0);
            treeChangeMapper.insert(change);
        }
    }

    private void requireVersions(ProjectSplitRequestDO request, ApplyProjectSplitCommand command) {
        if (!Objects.equals(request.getDraftVersion(), command.expectedDraftVersion())
                || !Objects.equals(request.getParentVersion(), command.expectedParentVersion())
                || !Objects.equals(request.getScopeVersion(), command.expectedScopeVersion())
                || !Objects.equals(request.getTreeVersion(), command.expectedTreeVersion())) {
            throw exception(PROJECT_SPLIT_APPLY_VERSION_CONFLICT);
        }
    }

    private PlatformCommandExecutionApi.SuccessFacts successFacts(ProjectSplitDraftService.Actor actor,
                                                                      ApplyProjectSplitResult result) {
        Map<String, Object> detail = Map.of("requestId", result.requestId(),
                "projectCount", result.projects().size(), "scopeVersion", result.scopeVersion(),
                "treeVersion", result.treeVersion(), "changeBatchId", result.changeBatchId());
        return new PlatformCommandExecutionApi.SuccessFacts("PROJECT_SPLIT_APPLY", "ProjectSplitRequest",
                String.valueOf(result.requestId()), actor.correlationId(), JsonUtils.toJsonString(detail),
                "ProjectTreeChanged", JsonUtils.toJsonString(detail));
    }

    private void validate(ApplyProjectSplitCommand command, ProjectSplitDraftService.Actor actor) {
        if (command == null || command.requestId() == null || command.expectedDraftVersion() == null
                || command.expectedParentVersion() == null || command.expectedScopeVersion() == null
                || command.expectedTreeVersion() == null || command.idempotencyKey() == null
                || command.idempotencyKey().isBlank() || command.requestDigest() == null
                || !command.requestDigest().matches("[0-9a-f]{64}") || actor == null
                || actor.tenantId() == null || actor.actorId() == null || actor.correlationId() == null
                || actor.correlationId().isBlank()) {
            throw new IllegalArgumentException("项目拆分应用命令不完整");
        }
    }

    private String failureStage(RuntimeException failure) {
        if (failure instanceof ServiceException serviceException && serviceException.getCode() != null) {
            return "business_" + serviceException.getCode();
        }
        String name = failure.getClass().getSimpleName();
        return name.length() > 48 ? name.substring(0, 48) : name;
    }

    private static final class AllocationAccumulator {
        private final String clientItemKey;
        private final Long orderLineId;
        private final String officeCode;
        private BigDecimal quantity = BigDecimal.ZERO;
        private final List<String> serials = new ArrayList<>();
        private AllocationAccumulator(String clientItemKey, Long orderLineId, String officeCode) {
            this.clientItemKey = clientItemKey; this.orderLineId = orderLineId; this.officeCode = officeCode;
        }
    }
}
