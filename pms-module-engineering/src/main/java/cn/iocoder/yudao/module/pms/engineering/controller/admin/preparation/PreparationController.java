package cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo.PreparationCursorPageRespVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo.PreparationCandidatePageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo.PreparationItemPatchReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo.PreparationItemPatchRespVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo.PreparationItemRespVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo.PreparationPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo.PreparationRespVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo.PreparationReviewReqVO;
import cn.iocoder.yudao.module.pms.engineering.service.preparation.PreparationQueryService;
import cn.iocoder.yudao.module.pms.engineering.service.preparation.PreparationItemApplicationService;
import cn.iocoder.yudao.module.pms.engineering.service.preparation.PreparationReviewService;
import cn.iocoder.yudao.module.pms.engineering.service.preparation.command.PatchPreparationItemCommand;
import cn.iocoder.yudao.module.pms.engineering.service.preparation.command.PreparationReviewCommand;
import cn.iocoder.yudao.module.pms.engineering.service.preparation.command.PreparationReviewResult;
import cn.iocoder.yudao.module.system.api.permission.dto.OrganizationUserCandidateRespDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.UUID;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.PREPARATION_PROJECT_FACT_INVALID;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.PREPARATION_COMMAND_INVALID;

@Tag(name = "管理后台 - PMS 工勘准备")
@RestController
@RequestMapping("/api/v1/pms/preparations")
@Validated
@RequiredArgsConstructor
public class PreparationController {

    private final PreparationQueryService queryService;
    private final PreparationItemApplicationService itemApplicationService;
    private final PreparationReviewService reviewService;
    private final Environment environment;

    @GetMapping
    @Operation(summary = "按项目查询当前工勘准备")
    @PreAuthorize("@ss.hasAnyPermissions('pms:preparation-survey:query','pms:preparation-survey:manage')")
    public CommonResult<PreparationRespVO> getCurrent(
            @RequestParam("projectId") @Positive Long projectId,
            @RequestParam(value = "type", defaultValue = "PRE_02") String type) {
        return withTrustedTenant(() -> success(queryService.getCurrent(projectId, type, actor())));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询工勘准备详情")
    @PreAuthorize("@ss.hasAnyPermissions('pms:preparation-survey:query','pms:preparation-survey:manage')")
    public CommonResult<PreparationRespVO> getDetail(@PathVariable("id") @Positive Long id) {
        return withTrustedTenant(() -> success(queryService.getDetail(id, actor())));
    }

    @GetMapping("/{id}/items")
    @Operation(summary = "稳定游标查询工勘项与固定表单")
    @PreAuthorize("@ss.hasAnyPermissions('pms:preparation-survey:query','pms:preparation-survey:manage')")
    public CommonResult<PreparationCursorPageRespVO<PreparationItemRespVO>> getItems(
            @PathVariable("id") @Positive Long id,
            @Valid @ModelAttribute PreparationPageReqVO request) {
        return withTrustedTenant(() -> success(queryService.getItems(id, request, actor())));
    }

    @GetMapping("/{id}/assignment-candidates")
    @Operation(summary = "按项目所属组织分页查询工勘负责人候选")
    @PreAuthorize("@ss.hasPermission('pms:preparation-survey:manage')")
    public CommonResult<PageResult<OrganizationUserCandidateRespDTO>> getAssignmentCandidates(
            @PathVariable("id") @Positive Long id,
            @Valid @ModelAttribute PreparationCandidatePageReqVO request) {
        return withTrustedTenant(() -> success(itemApplicationService.getCandidates(id, request, commandActor())));
    }

    @PatchMapping("/{id}/items/{itemId}")
    @Operation(summary = "按字段存在性修改工勘项及固定表单")
    @PreAuthorize("@ss.hasAnyPermissions('pms:preparation-survey:manage','pms:preparation-survey:fill')")
    public CommonResult<PreparationItemPatchRespVO> patchItem(
            @PathVariable("id") @Positive Long id,
            @PathVariable("itemId") @Positive Long itemId,
            @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody PreparationItemPatchReqVO request) {
        return withTrustedTenant(() -> {
            var evidence = request.getEvidenceReferences() == null ? null
                    : request.getEvidenceReferences().stream().map(row ->
                    new PatchPreparationItemCommand.EvidenceReference(row.getArtifactId(), row.getVersionNo(),
                            row.getReferenceKey(), row.getFileFactVersion(), row.getScopeVersion())).toList();
            var command = new PatchPreparationItemCommand(id, itemId, parseVersion(ifMatch),
                    request.getExpectedPreparationVersion(), request.getExpectedInputVersion(),
                    request.getExpectedReadinessVersion(), request.getExpectedFormVersion(),
                    request.getExpectedProjectVersion(), request.getSubmittedFields(),
                    request.getApplicabilityCode(), request.getOutsourced(), request.getAssigneeUserId(),
                    request.getNotApplicableReason(), request.getSiteResultCode(), request.getSiteResultDetail(),
                    request.getFormValueSnapshot(), evidence);
            return success(itemApplicationService.patch(command, commandActor()));
        });
    }

    @PostMapping("/{id}/actions/submit")
    @Operation(summary = "提交并冻结当前工勘准备版本")
    @PreAuthorize("@ss.hasPermission('pms:preparation-survey:manage')")
    public CommonResult<PreparationReviewResult> submit(@PathVariable("id") @Positive Long id,
            @RequestHeader("If-Match") String ifMatch,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PreparationReviewReqVO request) {
        return review(id, null, PreparationReviewCommand.SUBMIT, ifMatch, idempotencyKey, request);
    }

    @PostMapping("/{id}/items/{itemId}/actions/{action}")
    @Operation(summary = "确认、确认不适用或退回工勘项")
    @PreAuthorize("@ss.hasPermission('pms:preparation-survey:manage')")
    public CommonResult<PreparationReviewResult> reviewItem(@PathVariable("id") @Positive Long id,
            @PathVariable("itemId") @Positive Long itemId, @PathVariable("action") String action,
            @RequestHeader("If-Match") String ifMatch,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PreparationReviewReqVO request) {
        String commandAction = switch (action) {
            case "confirm" -> PreparationReviewCommand.CONFIRM;
            case "confirm-not-applicable" -> PreparationReviewCommand.CONFIRM_NOT_APPLICABLE;
            case "return" -> PreparationReviewCommand.RETURN;
            default -> throw exception(PREPARATION_COMMAND_INVALID);
        };
        return review(id, itemId, commandAction, ifMatch, idempotencyKey, request);
    }

    private CommonResult<PreparationReviewResult> review(Long preparationId, Long itemId, String action,
            String ifMatch, String idempotencyKey, PreparationReviewReqVO request) {
        return withTrustedTenant(() -> success(reviewService.execute(new PreparationReviewCommand(action,
                preparationId, itemId, itemId == null ? parseVersion(ifMatch) : request.getExpectedPreparationVersion(),
                itemId == null ? null : parseVersion(ifMatch), request.getExpectedProjectVersion(),
                request.getReason(), idempotencyKey), commandActor())));
    }

    @GetMapping("/history")
    @Operation(summary = "稳定游标查询项目工勘准备版本历史")
    @PreAuthorize("@ss.hasAnyPermissions('pms:preparation-survey:query','pms:preparation-survey:manage')")
    public CommonResult<PreparationCursorPageRespVO<PreparationRespVO>> getHistory(
            @RequestParam("projectId") @Positive Long projectId,
            @Valid @ModelAttribute PreparationPageReqVO request) {
        return withTrustedTenant(() -> success(queryService.getHistory(projectId, request, actor())));
    }

    private PreparationQueryService.Actor actor() {
        Long actorId = SecurityFrameworkUtils.getLoginUserId();
        if (actorId == null || actorId <= 0) throw exception(PREPARATION_PROJECT_FACT_INVALID);
        return new PreparationQueryService.Actor(TenantContextHolder.getRequiredTenantId(), actorId);
    }

    private PreparationItemApplicationService.Actor commandActor() {
        Long actorId = SecurityFrameworkUtils.getLoginUserId();
        if (actorId == null || actorId <= 0) throw exception(PREPARATION_PROJECT_FACT_INVALID);
        return new PreparationItemApplicationService.Actor(TenantContextHolder.getRequiredTenantId(), actorId,
                UUID.randomUUID().toString());
    }

    private Integer parseVersion(String value) {
        try {
            int version = Integer.parseInt(value);
            if (version < 0) throw new NumberFormatException();
            return version;
        } catch (NumberFormatException failure) {
            throw exception(PREPARATION_PROJECT_FACT_INVALID);
        }
    }

    private <T> T withTrustedTenant(Supplier<T> action) {
        if (TenantContextHolder.getTenantId() != null) return action.get();
        if (environment.getProperty("yudao.tenant.enable", Boolean.class, true)) {
            throw exception(PREPARATION_PROJECT_FACT_INVALID);
        }
        AtomicReference<T> result = new AtomicReference<>();
        TenantUtils.execute(0L, () -> result.set(action.get()));
        return result.get();
    }
}
