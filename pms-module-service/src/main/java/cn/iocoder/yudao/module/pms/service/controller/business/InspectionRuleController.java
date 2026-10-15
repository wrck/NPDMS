package cn.iocoder.yudao.module.pms.service.controller.business;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.service.service.inspection.*;
import cn.iocoder.yudao.module.pms.service.service.inspectionrule.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v1/pms/inspection-rules")
public class InspectionRuleController {
    private final InspectionRuleQueryService queries;
    private final InspectionRuleRevisionService revisions;
    private final InspectionRulePublicationService publications;
    private final InspectionCommandExecutor executor;

    public record Create(@NotBlank @Size(max = 64) String detectionId,
                         @NotBlank @Size(max = 128) String ruleName) { }
    public record Draft(String inspectionItem, String description, String categoryCode,
                        String categoryNameSnapshot, String severityCode, String severityNameSnapshot,
                        Integer sortOrder, String expectedResultRegex, String thresholdDataType,
                        String thresholdOperator, BigDecimal thresholdValue, String thresholdUnit,
                        List<InspectionRuleRevisionService.CommandDraft> commands,
                        List<InspectionRuleRevisionService.ProductTypeDraft> productTypes) { }
    public record Review(@NotBlank String conclusionCode) { }
    private record VersionedInput(Long revisionId, Long version, Object input) { }

    @GetMapping
    @PreAuthorize("@ss.hasPermission('pms:inspection-rule:query')")
    public CommonResult<PageResult<InspectionRuleQueryService.RuleView>> page(
            @Valid InspectionRuleQueryService.PageQuery query) { return success(queries.page(query)); }

    @GetMapping("/revisions/{revisionId}")
    @PreAuthorize("@ss.hasPermission('pms:inspection-rule:query')")
    public CommonResult<InspectionRuleQueryService.RuleView> detail(@PathVariable Long revisionId) {
        return success(queries.detail(revisionId));
    }

    @GetMapping("/selectable")
    @PreAuthorize("@ss.hasPermission('pms:inspection-rule:select')")
    public CommonResult<List<InspectionRuleQueryService.SelectableRule>> selectable(@RequestParam List<Long> deviceIds) {
        return success(queries.selectable(new InspectionRuleQueryService.DeviceSelection(
                InspectionRequestContext.current(), deviceIds)));
    }

    @PostMapping
    @PreAuthorize("@ss.hasPermission('pms:inspection-rule:manage')")
    public CommonResult<InspectionRuleRevisionService.DraftResult> create(
            @RequestHeader HttpHeaders headers, @Valid @RequestBody Create request) {
        var context = InspectionRequestContext.write(headers);
        return success(executor.execute(InspectionCommandExecutor.Write.<InspectionRuleRevisionService.DraftResult>builder()
                .context(context).operation("RULE_CREATE").scope("rules").input(request)
                .responseType(InspectionRuleRevisionService.DraftResult.class)
                .action(() -> revisions.createDraft(new InspectionRuleRevisionService.CreateDraftCommand(
                        request.detectionId(), request.ruleName())))
                .resourceKey(result -> String.valueOf(result.ruleId())).build()));
    }

    @PutMapping("/revisions/{revisionId}")
    @PreAuthorize("@ss.hasPermission('pms:inspection-rule:manage')")
    public CommonResult<InspectionRuleRevisionService.DraftResult> save(@PathVariable Long revisionId,
            @RequestHeader HttpHeaders headers, @RequestBody Draft request) {
        var context = InspectionRequestContext.write(headers);
        int version = ruleVersion(context);
        var command = new InspectionRuleRevisionService.SaveDraftCommand(revisionId, version,
                request.inspectionItem(), request.description(), request.categoryCode(), request.categoryNameSnapshot(),
                request.severityCode(), request.severityNameSnapshot(), request.sortOrder(), request.expectedResultRegex(),
                request.thresholdDataType(), request.thresholdOperator(), request.thresholdValue(), request.thresholdUnit(),
                request.commands(), request.productTypes());
        return success(executor.execute(InspectionCommandExecutor.Write.<InspectionRuleRevisionService.DraftResult>builder()
                .context(context).operation("RULE_SAVE").scope(String.valueOf(revisionId)).input(command)
                .responseType(InspectionRuleRevisionService.DraftResult.class).action(() -> revisions.saveDraft(command))
                .resourceKey(result -> String.valueOf(result.ruleId())).build()));
    }

    @PostMapping("/revisions/{revisionId}/actions/copy")
    @PreAuthorize("@ss.hasPermission('pms:inspection-rule:manage')")
    public CommonResult<InspectionRuleRevisionService.DraftResult> copy(@PathVariable Long revisionId,
            @RequestHeader HttpHeaders headers) {
        var context = InspectionRequestContext.write(headers);
        return success(executor.execute(InspectionCommandExecutor.Write.<InspectionRuleRevisionService.DraftResult>builder()
                .context(context).operation("RULE_COPY").scope(String.valueOf(revisionId))
                .input(new VersionedInput(revisionId, context.requireVersion(), null))
                .responseType(InspectionRuleRevisionService.DraftResult.class).action(() -> {
                    var source = queries.detail(revisionId);
                    if (source.getVersion().longValue() != context.requireVersion()) {
                        throw new InspectionException(InspectionException.Reason.VERSION_CONFLICT,
                                "规则版本已变化，请刷新后重试", source.getVersion().longValue());
                    }
                    return revisions.copyRevision(revisionId);
                }).resourceKey(result -> String.valueOf(result.ruleId())).build()));
    }

    @PostMapping("/revisions/{revisionId}/actions/validate")
    @PreAuthorize("@ss.hasPermission('pms:inspection-rule:manage')")
    public CommonResult<InspectionRuleRevisionService.ValidationResult> validate(@PathVariable Long revisionId) {
        return success(revisions.validateRevision(revisionId));
    }

    @PostMapping("/revisions/{revisionId}/actions/record-security-review")
    @PreAuthorize("@ss.hasPermission('pms:inspection-rule:security-review')")
    public CommonResult<InspectionRulePublicationService.ReviewResult> review(@PathVariable Long revisionId,
            @RequestHeader HttpHeaders headers, @Valid @RequestBody Review request) {
        var context = InspectionRequestContext.write(headers);
        return success(publications.recordSecurityReview(new InspectionRulePublicationService.ReviewCommand(
                revisionId, ruleVersion(context), request.conclusionCode(),
                context.idempotencyKey(), context.correlationId())));
    }

    @PostMapping("/revisions/{revisionId}/actions/publish")
    @PreAuthorize("@ss.hasPermission('pms:inspection-rule:publish')")
    public CommonResult<InspectionRulePublicationService.PublishResult> publish(@PathVariable Long revisionId,
            @RequestHeader HttpHeaders headers) {
        var context = InspectionRequestContext.write(headers);
        return success(publications.publish(new InspectionRulePublicationService.PublishCommand(revisionId,
                ruleVersion(context), context.idempotencyKey(), context.correlationId())));
    }

    @PostMapping("/revisions/{revisionId}/actions/disable")
    @PreAuthorize("@ss.hasPermission('pms:inspection-rule:disable')")
    public CommonResult<InspectionRulePublicationService.DisableResult> disable(@PathVariable Long revisionId,
            @RequestHeader HttpHeaders headers) {
        var context = InspectionRequestContext.write(headers);
        return success(publications.disable(new InspectionRulePublicationService.DisableCommand(revisionId,
                ruleVersion(context), context.idempotencyKey(), context.correlationId())));
    }

    private static int ruleVersion(InspectionRequestContext context) {
        long version = context.requireVersion();
        InspectionException.require(version >= 0 && version <= Integer.MAX_VALUE, "规则 If-Match 版本号超出范围");
        return (int) version;
    }
}
