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
import cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo.PreparationReadinessEvaluateReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo.PreparationReadinessSnapshotRespVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo.PreparationSourceRefreshReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo.PreparationWaiverReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo.RequirementAnalysisActionReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo.RequirementAnalysisCreateReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo.RequirementAnalysisFormPatchReqVO;
import cn.iocoder.yudao.module.pms.engineering.service.preparation.PreparationQueryService;
import cn.iocoder.yudao.module.pms.engineering.service.preparation.PreparationItemApplicationService;
import cn.iocoder.yudao.module.pms.engineering.service.preparation.PreparationReviewService;
import cn.iocoder.yudao.module.pms.engineering.service.preparation.PreparationReadinessService;
import cn.iocoder.yudao.module.pms.engineering.service.preparation.PreparationSourceService;
import cn.iocoder.yudao.module.pms.engineering.service.preparation.PreparationWaiverService;
import cn.iocoder.yudao.module.pms.engineering.service.preparation.command.PatchPreparationItemCommand;
import cn.iocoder.yudao.module.pms.engineering.service.preparation.command.PreparationReviewCommand;
import cn.iocoder.yudao.module.pms.engineering.service.preparation.command.PreparationReviewResult;
import cn.iocoder.yudao.module.pms.engineering.service.preparation.command.PreparationReadinessCommand;
import cn.iocoder.yudao.module.pms.engineering.service.preparation.command.PreparationReadinessResult;
import cn.iocoder.yudao.module.pms.engineering.service.requirement.RequirementAnalysisDynamicFormCommandService;
import cn.iocoder.yudao.module.pms.engineering.service.requirement.RequirementAnalysisDynamicFormQueryService;
import cn.iocoder.yudao.module.pms.engineering.service.requirement.RequirementAnalysisQueryService;
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
    private final PreparationReadinessService readinessService;
    private final PreparationSourceService sourceService;
    private final PreparationWaiverService waiverService;
    private final RequirementAnalysisQueryService requirementQueryService;
    private final RequirementAnalysisDynamicFormQueryService dynamicRequirementQueryService;
    private final RequirementAnalysisDynamicFormCommandService dynamicRequirementCommandService;
    private final Environment environment;

    @GetMapping
    @Operation(summary = "按项目查询当前工勘准备")
    @PreAuthorize("@ss.hasAnyPermissions('pms:preparation-survey:query','pms:preparation-survey:manage',"
            + "'pms:requirement-analysis:query')")
    public CommonResult<?> getCurrent(
            @RequestParam("projectId") @Positive Long projectId,
            @RequestParam(value = "type", defaultValue = "PRE_02") String type,
            @RequestParam(value = "history", defaultValue = "false") boolean history,
            @Valid @ModelAttribute PreparationPageReqVO pageRequest) {
        if (RequirementAnalysisQueryService.TYPE_ALIAS.equals(type)
                || RequirementAnalysisQueryService.TYPE.equals(type)) {
            return withTrustedTenant(() -> success(history
                    ? dynamicRequirementQueryService.getHistory(projectId, pageRequest, dynamicRequirementActor())
                    : dynamicRequirementQueryService.getWorkspace(projectId, dynamicRequirementActor())));
        }
        return withTrustedTenant(() -> success(queryService.getCurrent(projectId, type, actor())));
    }

    @PostMapping
    @Operation(summary = "创建首个需求分析草稿")
    @PreAuthorize("@ss.hasPermission('pms:requirement-analysis:manage')")
    public CommonResult<RequirementAnalysisDynamicFormCommandService.CommandResult> createRequirementAnalysis(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody RequirementAnalysisCreateReqVO request) {
        if (!RequirementAnalysisQueryService.TYPE_ALIAS.equals(request.getType())
                && !RequirementAnalysisQueryService.TYPE.equals(request.getType())) {
            throw exception(PREPARATION_COMMAND_INVALID);
        }
        return withTrustedTenant(() -> success(dynamicRequirementCommandService.createInitial(
                new RequirementAnalysisDynamicFormCommandService.CreateCommand(
                        request.getProjectId(), idempotencyKey), dynamicRequirementCommandActor())));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询工勘准备详情")
    @PreAuthorize("@ss.hasAnyPermissions('pms:preparation-survey:query','pms:preparation-survey:manage','pms:requirement-analysis:query')")
    public CommonResult<?> getDetail(@PathVariable("id") @Positive Long id) {
        return withTrustedTenant(() -> dynamicRequirementQueryService.owns(id,
                TenantContextHolder.getRequiredTenantId())
                ? success(dynamicRequirementQueryService.getDetail(id, dynamicRequirementActor()))
                : success(queryService.getDetail(id, actor())));
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

    @PatchMapping(value = "/{id}/items/{itemId}", params = "!type")
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

    @PatchMapping("/{id}/form")
    @Operation(summary = "保存需求分析动态表单普通值")
    @PreAuthorize("@ss.hasPermission('pms:requirement-analysis:manage')")
    public CommonResult<RequirementAnalysisDynamicFormCommandService.CommandResult> patchRequirementForm(
            @PathVariable("id") @Positive Long id,
            @RequestHeader("If-Match") String ifMatch,
            @RequestHeader("X-SOL-If-Match") String solIfMatch,
            @Valid @RequestBody RequirementAnalysisFormPatchReqVO request) {
        return withTrustedTenant(() -> success(dynamicRequirementCommandService.patch(
                new RequirementAnalysisDynamicFormCommandService.PatchCommand(id, parseVersion(solIfMatch),
                        parseVersion(ifMatch), request.getValues(), UUID.randomUUID().toString()),
                dynamicRequirementCommandActor())));
    }

    @PostMapping(value = "/{id}/actions/submit", params = "!type")
    @Operation(summary = "提交并冻结当前工勘准备版本")
    @PreAuthorize("@ss.hasPermission('pms:preparation-survey:manage')")
    public CommonResult<PreparationReviewResult> submit(@PathVariable("id") @Positive Long id,
            @RequestHeader("If-Match") String ifMatch,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PreparationReviewReqVO request) {
        return review(id, null, PreparationReviewCommand.SUBMIT, ifMatch, idempotencyKey, request);
    }

    @PostMapping(value = "/{id}/actions/submit", params = "type=PRE_04")
    @Operation(summary = "完成并冻结需求分析版本")
    @PreAuthorize("@ss.hasPermission('pms:requirement-analysis:manage')")
    public CommonResult<RequirementAnalysisDynamicFormCommandService.CommandResult> completeRequirementAnalysis(
            @PathVariable("id") @Positive Long id,
            @RequestHeader("If-Match") String ifMatch,
            @RequestHeader("X-SOL-If-Match") String solIfMatch,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody(required = false) RequirementAnalysisActionReqVO ignored) {
        return withTrustedTenant(() -> success(dynamicRequirementCommandService.complete(
                new RequirementAnalysisDynamicFormCommandService.CompleteCommand(id, parseVersion(solIfMatch),
                        parseVersion(ifMatch), idempotencyKey), dynamicRequirementCommandActor())));
    }

    @PostMapping("/{id}/actions/create-draft")
    @Operation(summary = "从当前有效完成版创建需求分析修订草稿")
    @PreAuthorize("@ss.hasPermission('pms:requirement-analysis:manage')")
    public CommonResult<RequirementAnalysisDynamicFormCommandService.CommandResult> createRequirementAnalysisRevision(
            @PathVariable("id") @Positive Long id,
            @RequestHeader("If-Match") String ifMatch,
            @RequestHeader("X-SOL-If-Match") String solIfMatch,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody(required = false) RequirementAnalysisActionReqVO ignored) {
        return withTrustedTenant(() -> {
            var source = dynamicRequirementQueryService.getDetail(id, dynamicRequirementActor());
            return success(dynamicRequirementCommandService.createRevision(
                    new RequirementAnalysisDynamicFormCommandService.CreateRevisionCommand(id,
                            source.getDynamicFormInstanceId(), parseVersion(solIfMatch), parseVersion(ifMatch),
                            idempotencyKey), dynamicRequirementCommandActor()));
        });
    }

    @GetMapping("/{id}/compare")
    @Operation(summary = "对比同项目需求分析版本")
    @PreAuthorize("@ss.hasPermission('pms:requirement-analysis:query')")
    public CommonResult<?> compareRequirementAnalysis(
            @PathVariable("id") @Positive Long id,
            @RequestParam("targetPreparationId") @Positive Long targetPreparationId) {
        return withTrustedTenant(() -> success(dynamicRequirementQueryService.compare(
                id, targetPreparationId, dynamicRequirementActor())));
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

    @PostMapping("/{id}/actions/evaluate-readiness")
    @Operation(summary = "显式评估工勘实施就绪")
    @PreAuthorize("@ss.hasPermission('pms:preparation-survey:manage')")
    public CommonResult<PreparationReadinessResult> evaluateReadiness(
            @PathVariable("id") @Positive Long id,
            @RequestHeader("If-Match") String ifMatch,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PreparationReadinessEvaluateReqVO request) {
        return withTrustedTenant(() -> success(readinessService.evaluate(new PreparationReadinessCommand(
                id, parseVersion(ifMatch), request.getExpectedProjectVersion(), idempotencyKey), commandActor())));
    }

    @PostMapping("/{id}/items/{itemId}/sources/actions/refresh")
    @Operation(summary = "刷新工勘项权威来源事实")
    @PreAuthorize("@ss.hasPermission('pms:preparation-survey:manage')")
    public CommonResult<PreparationSourceService.SourceRefreshResult> refreshSource(
            @PathVariable("id") @Positive Long id, @PathVariable("itemId") @Positive Long itemId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PreparationSourceRefreshReqVO request) {
        return withTrustedTenant(() -> success(sourceService.refresh(new PreparationSourceService.SourceRefreshCommand(
                id, itemId, request.getExpectedPreparationVersion(), request.getExpectedInputVersion(),
                request.getExpectedReadinessVersion(), request.getExpectedItemVersion(),
                request.getExpectedSourceVersion(), request.getExpectedProjectVersion(), request.getSourceTypeCode(),
                request.getSourceObjectType(), request.getSourceObjectId(), request.getSourceReferenceKey(),
                idempotencyKey), commandActor())));
    }

    @PostMapping("/{id}/items/{itemId}/waivers")
    @Operation(summary = "申请逐项就绪豁免")
    @PreAuthorize("@ss.hasPermission('pms:preparation-survey:manage')")
    public CommonResult<PreparationWaiverService.WaiverResult> createWaiver(
            @PathVariable("id") @Positive Long id, @PathVariable("itemId") @Positive Long itemId,
            @RequestHeader("If-Match") String ifMatch,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PreparationWaiverReqVO request) {
        return waiver(id, itemId, null, "CREATE", ifMatch, idempotencyKey, request);
    }

    @GetMapping("/{id}/items/{itemId}/waivers")
    @Operation(summary = "稳定游标查询逐项豁免历史")
    @PreAuthorize("@ss.hasAnyPermissions('pms:preparation-survey:query','pms:preparation-survey:manage','pms:preparation-survey:waiver-approve')")
    public CommonResult<PreparationWaiverService.WaiverPage> getWaivers(
            @PathVariable("id") @Positive Long id, @PathVariable("itemId") @Positive Long itemId,
            @Valid @ModelAttribute PreparationPageReqVO request) {
        return withTrustedTenant(() -> success(waiverService.page(
                id, itemId, request.getCursor(), request.getPageSize(), commandActor())));
    }

    @PostMapping("/{id}/items/{itemId}/waivers/{waiverId}/actions/{action}")
    @Operation(summary = "提交、批准、驳回或撤回逐项豁免")
    @PreAuthorize("@ss.hasAnyPermissions('pms:preparation-survey:manage','pms:preparation-survey:waiver-approve')")
    public CommonResult<PreparationWaiverService.WaiverResult> actWaiver(
            @PathVariable("id") @Positive Long id, @PathVariable("itemId") @Positive Long itemId,
            @PathVariable("waiverId") @Positive Long waiverId, @PathVariable("action") String action,
            @RequestHeader("If-Match") String ifMatch,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PreparationWaiverReqVO request) {
        String commandAction = switch (action) {
            case "submit" -> "SUBMIT";
            case "approve" -> "APPROVE";
            case "reject" -> "REJECT";
            case "withdraw" -> "WITHDRAW";
            default -> throw exception(PREPARATION_COMMAND_INVALID);
        };
        return waiver(id, itemId, waiverId, commandAction, ifMatch, idempotencyKey, request);
    }

    private CommonResult<PreparationWaiverService.WaiverResult> waiver(Long preparationId, Long itemId,
            Long waiverId, String action, String ifMatch, String idempotencyKey, PreparationWaiverReqVO request) {
        return withTrustedTenant(() -> success(waiverService.execute(new PreparationWaiverService.WaiverCommand(
                action, preparationId, itemId, waiverId, parseVersion(ifMatch), request.getExpectedInputVersion(),
                request.getExpectedReadinessVersion(), request.getExpectedItemVersion(),
                request.getExpectedWaiverVersion(), request.getExpectedProjectVersion(), request.getBlockerCodes(),
                request.getReason(), request.getRisk(), request.getCompensation(), request.getValidFrom(),
                request.getValidUntil(), request.getOpinion(), idempotencyKey), commandActor())));
    }

    @GetMapping("/{id}/readiness-snapshots")
    @Operation(summary = "稳定游标查询工勘就绪快照历史")
    @PreAuthorize("@ss.hasAnyPermissions('pms:preparation-survey:query','pms:preparation-survey:manage')")
    public CommonResult<PreparationCursorPageRespVO<PreparationReadinessSnapshotRespVO>> getReadinessSnapshots(
            @PathVariable("id") @Positive Long id,
            @Valid @ModelAttribute PreparationPageReqVO request) {
        return withTrustedTenant(() -> success(queryService.getReadinessSnapshots(id, request, actor())));
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

    private RequirementAnalysisQueryService.Actor requirementActor() {
        Long actorId = SecurityFrameworkUtils.getLoginUserId();
        if (actorId == null || actorId <= 0) throw exception(PREPARATION_PROJECT_FACT_INVALID);
        return new RequirementAnalysisQueryService.Actor(
                TenantContextHolder.getRequiredTenantId(), actorId, UUID.randomUUID().toString());
    }

    private RequirementAnalysisDynamicFormQueryService.Actor dynamicRequirementActor() {
        Long actorId = SecurityFrameworkUtils.getLoginUserId();
        if (actorId == null || actorId <= 0) throw exception(PREPARATION_PROJECT_FACT_INVALID);
        return new RequirementAnalysisDynamicFormQueryService.Actor(
                TenantContextHolder.getRequiredTenantId(), actorId);
    }

    private RequirementAnalysisDynamicFormCommandService.Actor dynamicRequirementCommandActor() {
        Long actorId = SecurityFrameworkUtils.getLoginUserId();
        if (actorId == null || actorId <= 0) throw exception(PREPARATION_PROJECT_FACT_INVALID);
        return new RequirementAnalysisDynamicFormCommandService.Actor(
                TenantContextHolder.getRequiredTenantId(), actorId, UUID.randomUUID().toString());
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
