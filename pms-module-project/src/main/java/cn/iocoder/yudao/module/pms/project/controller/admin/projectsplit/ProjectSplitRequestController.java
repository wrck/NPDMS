package cn.iocoder.yudao.module.pms.project.controller.admin.projectsplit;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.controller.admin.projectsplit.vo.ProjectSplitDraftSaveReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projectsplit.vo.ApplyProjectSplitReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projectsplit.vo.ProjectSplitPreviewRespVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projectsplit.vo.ProjectSplitRequestRespVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectsplit.ProjectSplitItemDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectsplit.ProjectSplitScopeDO;
import cn.iocoder.yudao.module.pms.project.service.projectsplit.ProjectSplitDraftService;
import cn.iocoder.yudao.module.pms.project.service.projectsplit.ProjectSplitApplicationService;
import cn.iocoder.yudao.module.pms.project.service.projectsplit.ProjectSplitPreviewService;
import cn.iocoder.yudao.module.pms.project.service.projectsplit.command.ProjectSplitDraftCommand;
import cn.iocoder.yudao.module.pms.project.service.projectsplit.command.ApplyProjectSplitCommand;
import cn.iocoder.yudao.module.pms.project.service.projectsplit.command.ApplyProjectSplitResult;
import cn.iocoder.yudao.module.pms.project.service.projectsplit.command.ProjectSplitPreviewCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 项目拆分草稿")
@RestController
@RequestMapping("/pms/project-split-requests")
@Validated
@RequiredArgsConstructor
public class ProjectSplitRequestController {
    private final ProjectSplitDraftService draftService;
    private final ProjectSplitPreviewService previewService;
    private final ProjectSplitApplicationService applicationService;

    @PostMapping
    @Operation(summary = "创建项目拆分草稿")
    @PreAuthorize("@ss.hasPermission('pms:project:create')")
    public CommonResult<ProjectSplitRequestRespVO> create(@Valid @RequestBody ProjectSplitDraftSaveReqVO request) {
        return success(toResponse(draftService.saveDraft(toCommand(null, request), actor())));
    }

    @GetMapping("/{id}")
    @Operation(summary = "读取项目拆分草稿")
    @PreAuthorize("@ss.hasPermission('pms:project:query')")
    public CommonResult<ProjectSplitRequestRespVO> get(@PathVariable("id") Long id) {
        return success(toResponse(draftService.getDraft(id, actor())));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新项目拆分草稿")
    @PreAuthorize("@ss.hasPermission('pms:project:update')")
    public CommonResult<ProjectSplitRequestRespVO> update(@PathVariable("id") Long id,
                                                           @Valid @RequestBody ProjectSplitDraftSaveReqVO request) {
        return success(toResponse(draftService.saveDraft(toCommand(id, request), actor())));
    }

    @PostMapping("/{id}/actions/preview")
    @Operation(summary = "服务端预览项目拆分")
    @PreAuthorize("@ss.hasPermission('pms:project:create')")
    public CommonResult<ProjectSplitPreviewRespVO> preview(@PathVariable("id") Long id,
                                                            @RequestParam @NotNull Integer expectedDraftVersion) {
        return success(toResponse(previewService.preview(
                new ProjectSplitPreviewCommand(id, expectedDraftVersion), actor())));
    }

    @PostMapping("/{id}/actions/validate")
    @Operation(summary = "重新校验项目拆分草稿")
    @PreAuthorize("@ss.hasPermission('pms:project:create')")
    public CommonResult<ProjectSplitPreviewRespVO> validate(@PathVariable("id") Long id,
                                                             @RequestParam @NotNull Integer expectedDraftVersion) {
        return success(toResponse(previewService.validateAgain(
                new ProjectSplitPreviewCommand(id, expectedDraftVersion), actor())));
    }

    @PostMapping("/{id}/actions/apply")
    @Operation(summary = "原子应用项目拆分方案")
    @PreAuthorize("@ss.hasPermission('pms:project:create')")
    public CommonResult<ApplyProjectSplitResult> apply(
            @PathVariable("id") Long id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody ApplyProjectSplitReqVO request) {
        Integer expectedDraftVersion = Integer.valueOf(ifMatch.replace("\"", "").trim());
        String digest = sha256(id + "|" + expectedDraftVersion + "|" + request.getExpectedParentVersion()
                + "|" + request.getExpectedScopeVersion() + "|" + request.getExpectedTreeVersion());
        return success(applicationService.apply(new ApplyProjectSplitCommand(id, expectedDraftVersion,
                request.getExpectedParentVersion(), request.getExpectedScopeVersion(),
                request.getExpectedTreeVersion(), idempotencyKey, digest), actor()));
    }

    private ProjectSplitDraftCommand toCommand(Long requestId, ProjectSplitDraftSaveReqVO request) {
        return new ProjectSplitDraftCommand(requestId, request.getExpectedDraftVersion(), request.getParentProjectId(),
                request.getTemplateRevisionId(), request.getItems().stream().map(item ->
                new ProjectSplitDraftCommand.Item(item.getClientItemKey(), item.getProjectName(),
                        item.getBusinessLevelCode(), item.getTreeSort(), item.getOfficeDepartmentCode(),
                        item.getScopes().stream().map(scope -> new ProjectSplitDraftCommand.Scope(
                                scope.getOrderLineId(), scope.getQuantity(), scope.getOfficeDepartmentCode(),
                                scope.getSerialNumbers())).toList())).toList());
    }

    private ProjectSplitDraftService.Actor actor() {
        return new ProjectSplitDraftService.Actor(TenantContextHolder.getTenantId(),
                SecurityFrameworkUtils.getLoginUserId(), UUID.randomUUID().toString());
    }

    private ProjectSplitRequestRespVO toResponse(ProjectSplitDraftService.DraftResult draft) {
        ProjectSplitRequestRespVO response = new ProjectSplitRequestRespVO();
        response.setId(draft.request().getId());
        response.setParentProjectId(draft.request().getParentProjectId());
        response.setStatus(draft.request().getStatus());
        response.setDraftVersion(draft.request().getDraftVersion());
        response.setParentVersion(draft.request().getParentVersion());
        response.setScopeVersion(draft.request().getScopeVersion());
        response.setTreeVersion(draft.request().getTreeVersion());
        response.setTemplateRevisionId(draft.request().getTemplateRevisionId());
        response.setPreviewHash(draft.request().getPreviewHash());
        response.setValidationStatus(draft.request().getValidationStatus());
        response.setValidatedAt(draft.request().getValidatedAt());
        Map<Long, List<ProjectSplitScopeDO>> scopes = draft.scopes().stream()
                .collect(Collectors.groupingBy(ProjectSplitScopeDO::getSplitItemId));
        response.setItems(draft.items().stream().map(item -> toItem(item, scopes.getOrDefault(item.getId(), List.of()))).toList());
        return response;
    }

    private ProjectSplitRequestRespVO.Item toItem(ProjectSplitItemDO item, List<ProjectSplitScopeDO> scopes) {
        ProjectSplitRequestRespVO.Item response = new ProjectSplitRequestRespVO.Item();
        response.setId(item.getId());
        response.setClientItemKey(item.getClientItemKey());
        response.setProjectName(item.getProjectName());
        response.setBusinessLevelCode(item.getBusinessLevelCode());
        response.setTreeSort(item.getTreeSort());
        response.setOfficeDepartmentCode(item.getOfficeDepartmentCode());
        response.setItemStatus(item.getItemStatus());
        response.setScopes(scopes.stream().map(scope -> {
            ProjectSplitRequestRespVO.Scope value = new ProjectSplitRequestRespVO.Scope();
            value.setId(scope.getId()); value.setOrderLineId(scope.getOrderLineId());
            value.setAllocatedQty(scope.getAllocatedQty()); value.setOfficeDepartmentCode(scope.getOfficeDepartmentCode());
            value.setSerialNo(scope.getSerialNo()); value.setSourceScopeVersion(scope.getSourceScopeVersion());
            return value;
        }).toList());
        return response;
    }

    private ProjectSplitPreviewRespVO toResponse(ProjectSplitPreviewService.PreviewResult result) {
        ProjectSplitPreviewRespVO response = new ProjectSplitPreviewRespVO();
        response.setRequestId(result.requestId()); response.setDraftVersion(result.draftVersion());
        response.setValid(result.valid()); response.setPreviewHash(result.previewHash());
        response.setValidatedAt(result.validatedAt()); response.setParentVersion(result.parentVersion());
        response.setScopeVersion(result.scopeVersion()); response.setTreeVersion(result.treeVersion());
        response.setErrors(result.errors());
        response.setItems(result.items().stream().map(item -> {
            ProjectSplitPreviewRespVO.Item value = new ProjectSplitPreviewRespVO.Item();
            value.setClientItemKey(item.clientItemKey());
            value.setValid(item.valid());
            value.setErrors(item.errors());
            return value;
        }).toList());
        return response;
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
