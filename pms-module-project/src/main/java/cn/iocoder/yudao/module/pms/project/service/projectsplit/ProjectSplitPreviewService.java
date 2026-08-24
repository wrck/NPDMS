package cn.iocoder.yudao.module.pms.project.service.projectsplit;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.asset.api.device.AssetDeviceScopeApi;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.SerialScopeValidationResult;
import cn.iocoder.yudao.module.pms.commerce.api.scope.DeliveryScopeApi;
import cn.iocoder.yudao.module.pms.commerce.api.scope.dto.SplitScopeApplyResult;
import cn.iocoder.yudao.module.pms.commerce.api.scope.dto.SplitScopePreviewCommand;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectsplit.ProjectSplitItemDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectsplit.ProjectSplitRequestDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectsplit.ProjectSplitScopeDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.ProjectTreeVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectsplit.ProjectSplitItemMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectsplit.ProjectSplitRequestMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeVersionMapper;
import cn.iocoder.yudao.module.pms.project.service.platform.ProjectOperationAuditService;
import cn.iocoder.yudao.module.pms.project.service.projectsplit.command.ProjectSplitPreviewCommand;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_SPLIT_DRAFT_VERSION_CONFLICT;

@Service
@RequiredArgsConstructor
public class ProjectSplitPreviewService {
    private final ProjectSplitDraftService draftService;
    private final ProjectSplitRequestMapper requestMapper;
    private final ProjectSplitItemMapper itemMapper;
    private final ProjectMasterMapper projectMapper;
    private final ProjectTreeVersionMapper treeVersionMapper;
    private final DeliveryScopeApi deliveryScopeApi;
    private final AssetDeviceScopeApi assetDeviceScopeApi;
    private final DeptApi deptApi;
    private final ProjectOperationAuditService auditService;
    private final ProjectSplitMetrics metrics;

    @Transactional(rollbackFor = Exception.class)
    public PreviewResult preview(ProjectSplitPreviewCommand command, ProjectSplitDraftService.Actor actor) {
        long started = System.nanoTime();
        ProjectSplitDraftService.DraftResult draft = draftService.getDraft(command.requestId(), actor);
        ProjectSplitRequestDO request = draft.request();
        if (!Objects.equals(request.getDraftVersion(), command.expectedDraftVersion())) {
            throw exception(PROJECT_SPLIT_DRAFT_VERSION_CONFLICT);
        }
        List<String> errors = new ArrayList<>();
        validateProjectWatermarks(request, errors);
        validateDepartments(draft, errors);

        List<SplitScopePreviewCommand.Allocation> allocations = allocations(draft);
        try {
            SplitScopeApplyResult commerce = deliveryScopeApi.previewSplit(new SplitScopePreviewCommand(
                    actor.tenantId(), request.getParentProjectId(), request.getScopeVersion(), allocations));
            errors.addAll(commerce.errors());
        } catch (RuntimeException ignored) {
            errors.add("COMMERCE_SCOPE_UNAVAILABLE");
        }
        List<String> serials = draft.scopes().stream().map(ProjectSplitScopeDO::getSerialNo)
                .filter(Objects::nonNull).toList();
        if (!serials.isEmpty()) {
            try {
                SerialScopeValidationResult asset = assetDeviceScopeApi.validateAssignableSerials(
                        actor.tenantId(), request.getParentProjectId(), serials);
                asset.missingSerialNumbers().forEach(serial -> errors.add("SERIAL_MISSING:" + serial));
                asset.unavailableSerialNumbers().forEach(serial -> errors.add("SERIAL_UNAVAILABLE:" + serial));
                asset.duplicateSerialNumbers().forEach(serial -> errors.add("DUPLICATE_SERIAL:" + serial));
            } catch (RuntimeException ignored) {
                errors.add("ASSET_SCOPE_UNAVAILABLE");
            }
        }
        List<String> distinctErrors = errors.stream().distinct().toList();
        List<ItemResult> itemResults = itemResults(draft, distinctErrors);
        boolean valid = distinctErrors.isEmpty();
        LocalDateTime validatedAt = LocalDateTime.now();
        String previewHash = sha256(JsonUtils.toJsonString(Map.of(
                "requestId", request.getId(), "draftVersion", request.getDraftVersion(),
                "parentVersion", request.getParentVersion(), "scopeVersion", request.getScopeVersion(),
                "treeVersion", request.getTreeVersion(), "allocations", allocations)));
        persistResult(request, draft.items(), valid, distinctErrors, itemResults, previewHash, validatedAt);
        auditService.record(actor.tenantId(), actor.actorId(), actor.correlationId(), "PROJECT_SPLIT_PREVIEW",
                request.getId(), valid ? "SUCCESS" : "VALIDATION_FAILED",
                Map.of("valid", valid, "errorCount", distinctErrors.size(), "draftVersion", request.getDraftVersion()));
        metrics.preview(valid, valid ? "none" : errorType(distinctErrors.getFirst()), System.nanoTime() - started);
        return new PreviewResult(request.getId(), request.getDraftVersion(), valid, previewHash, validatedAt,
                request.getParentVersion(), request.getScopeVersion(), request.getTreeVersion(), distinctErrors,
                itemResults);
    }

    public PreviewResult validateAgain(ProjectSplitPreviewCommand command, ProjectSplitDraftService.Actor actor) {
        return preview(command, actor);
    }

    private void validateProjectWatermarks(ProjectSplitRequestDO request, List<String> errors) {
        ProjectMasterDO parent = projectMapper.selectById(request.getParentProjectId());
        if (parent == null) {
            errors.add("PARENT_PROJECT_NOT_FOUND");
            return;
        }
        if (!Objects.equals(parent.getVersion(), request.getParentVersion())) {
            errors.add("PARENT_VERSION_CONFLICT");
        }
        Long rootId = parent.getRootId() == null ? parent.getId() : parent.getRootId();
        ProjectTreeVersionDO tree = treeVersionMapper.selectLatestActive(rootId);
        long treeVersion = tree == null ? 0L : tree.getTreeVersion();
        if (!Objects.equals(treeVersion, request.getTreeVersion())) {
            errors.add("TREE_VERSION_CONFLICT");
        }
    }

    private void validateDepartments(ProjectSplitDraftService.DraftResult draft, List<String> errors) {
        Set<String> codes = new LinkedHashSet<>();
        draft.items().stream().map(ProjectSplitItemDO::getOfficeDepartmentCode).filter(Objects::nonNull).forEach(codes::add);
        draft.scopes().stream().map(ProjectSplitScopeDO::getOfficeDepartmentCode).filter(Objects::nonNull).forEach(codes::add);
        for (String code : codes) {
            try {
                DeptRespDTO department = deptApi.getDeptByCode(code);
                if (department == null || !CommonStatusEnum.ENABLE.getStatus().equals(department.getStatus())) {
                    errors.add("DEPARTMENT_CODE_INVALID:" + code);
                }
            } catch (RuntimeException ignored) {
                errors.add("DEPARTMENT_AUTHORITY_UNAVAILABLE:" + code);
            }
        }
    }

    private List<SplitScopePreviewCommand.Allocation> allocations(ProjectSplitDraftService.DraftResult draft) {
        Map<Long, ProjectSplitItemDO> itemById = new HashMap<>();
        draft.items().forEach(item -> itemById.put(item.getId(), item));
        Map<String, AllocationAccumulator> grouped = new LinkedHashMap<>();
        for (ProjectSplitScopeDO scope : draft.scopes()) {
            ProjectSplitItemDO item = itemById.get(scope.getSplitItemId());
            String key = item.getClientItemKey() + "|" + scope.getOrderLineId() + "|" + scope.getOfficeDepartmentCode();
            AllocationAccumulator value = grouped.computeIfAbsent(key, ignored -> new AllocationAccumulator(
                    item.getClientItemKey(), scope.getOrderLineId(), scope.getOfficeDepartmentCode()));
            value.quantity = value.quantity.add(scope.getAllocatedQty());
            if (scope.getSerialNo() != null) {
                value.serials.add(scope.getSerialNo());
            }
        }
        return grouped.values().stream().map(value -> new SplitScopePreviewCommand.Allocation(
                value.clientItemKey, value.orderLineId, value.quantity, value.officeCode, List.copyOf(value.serials))).toList();
    }

    private void persistResult(ProjectSplitRequestDO request, List<ProjectSplitItemDO> items, boolean valid,
                               List<String> errors, List<ItemResult> itemResults, String previewHash,
                               LocalDateTime validatedAt) {
        ProjectSplitRequestDO update = new ProjectSplitRequestDO();
        update.setId(request.getId());
        update.setValidationStatus(valid ? "VALID" : "INVALID");
        update.setValidationSummary(JsonUtils.toJsonString(errors));
        update.setPreviewHash(previewHash);
        update.setValidatedAt(validatedAt);
        requestMapper.updateById(update);
        Map<String, ItemResult> resultByKey = itemResults.stream()
                .collect(java.util.stream.Collectors.toMap(ItemResult::clientItemKey, result -> result));
        for (ProjectSplitItemDO item : items) {
            ItemResult result = resultByKey.get(item.getClientItemKey());
            ProjectSplitItemDO itemUpdate = new ProjectSplitItemDO();
            itemUpdate.setId(item.getId());
            itemUpdate.setItemStatus(result.valid() ? "VALID" : "INVALID");
            itemUpdate.setValidationResult(JsonUtils.toJsonString(result.errors()));
            itemMapper.updateById(itemUpdate);
        }
    }

    private List<ItemResult> itemResults(ProjectSplitDraftService.DraftResult draft, List<String> errors) {
        Map<Long, List<ProjectSplitScopeDO>> scopesByItem = draft.scopes().stream()
                .collect(java.util.stream.Collectors.groupingBy(ProjectSplitScopeDO::getSplitItemId));
        return draft.items().stream().map(item -> {
            List<ProjectSplitScopeDO> scopes = scopesByItem.getOrDefault(item.getId(), List.of());
            Set<String> references = new LinkedHashSet<>();
            references.add(item.getClientItemKey());
            if (item.getOfficeDepartmentCode() != null) {
                references.add(item.getOfficeDepartmentCode());
            }
            scopes.forEach(scope -> {
                references.add(String.valueOf(scope.getOrderLineId()));
                if (scope.getOfficeDepartmentCode() != null) {
                    references.add(scope.getOfficeDepartmentCode());
                }
                if (scope.getSerialNo() != null) {
                    references.add(scope.getSerialNo());
                }
            });
            List<String> itemErrors = errors.stream().filter(error -> {
                int separator = error.indexOf(':');
                return separator < 0 || references.contains(error.substring(separator + 1));
            }).toList();
            return new ItemResult(item.getClientItemKey(), itemErrors.isEmpty(), itemErrors);
        }).toList();
    }

    private String errorType(String error) {
        int separator = error.indexOf(':');
        return separator < 0 ? error : error.substring(0, separator);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static final class AllocationAccumulator {
        private final String clientItemKey;
        private final Long orderLineId;
        private final String officeCode;
        private BigDecimal quantity = BigDecimal.ZERO;
        private final List<String> serials = new ArrayList<>();
        private AllocationAccumulator(String clientItemKey, Long orderLineId, String officeCode) {
            this.clientItemKey = clientItemKey;
            this.orderLineId = orderLineId;
            this.officeCode = officeCode;
        }
    }

    public record PreviewResult(Long requestId, Integer draftVersion, boolean valid, String previewHash,
                                LocalDateTime validatedAt, Integer parentVersion, Long scopeVersion,
                                Long treeVersion, List<String> errors, List<ItemResult> items) {}
    public record ItemResult(String clientItemKey, boolean valid, List<String> errors) {}
}
