package cn.iocoder.yudao.module.pms.project.controller.admin.acceptancereport;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.pms.project.controller.admin.acceptancereport.vo.AcceptanceReportDraftReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.acceptancereport.vo.AcceptanceReportPublishReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.acceptancereport.vo.AcceptanceReportRevokeReqVO;
import cn.iocoder.yudao.module.pms.project.service.acceptancereport.AcceptanceReportCommandService;
import cn.iocoder.yudao.module.pms.project.service.acceptancereport.AcceptanceReportCommands;
import cn.iocoder.yudao.module.pms.project.service.acceptancereport.AcceptanceReportQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.BAD_REQUEST;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - PMS 初验终验报告")
@RestController
@RequestMapping("/api/v1/pms/acceptances")
@Validated
@RequiredArgsConstructor
public class AcceptanceReportController {

    private final AcceptanceReportQueryService queryService;
    private final AcceptanceReportCommandService commandService;
    private final Environment environment;

    @GetMapping
    @Operation(summary = "查询有权项目的初验终验活动")
    @PreAuthorize("@ss.hasPermission('pms:acceptance:report:query')")
    public CommonResult<List<AcceptanceReportQueryService.ActivityView>> list(
            @RequestParam(required = false) Long projectId) {
        return withTenant(() -> success(queryService.list(projectId, queryActor())));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询验收活动")
    @PreAuthorize("@ss.hasPermission('pms:acceptance:report:query')")
    public CommonResult<AcceptanceReportQueryService.ActivityView> get(@PathVariable("id") Long acceptanceId) {
        return withTenant(() -> success(queryService.get(acceptanceId, queryActor())));
    }

    @GetMapping("/{id}/report-versions")
    @Operation(summary = "查询验收报告当前及历史版本")
    @PreAuthorize("@ss.hasPermission('pms:acceptance:report:query')")
    public CommonResult<List<AcceptanceReportQueryService.ReportVersionView>> listVersions(
            @PathVariable("id") Long acceptanceId) {
        return withTenant(() -> success(queryService.listVersions(acceptanceId, queryActor())));
    }

    @PostMapping("/{id}/report-versions")
    @Operation(summary = "创建验收报告草稿")
    @PreAuthorize("@ss.hasPermission('pms:acceptance:report:write')")
    public CommonResult<AcceptanceReportCommands.ReportResult> createDraft(
            @PathVariable("id") Long acceptanceId,
            @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody AcceptanceReportDraftReqVO request) {
        int expectedActivityVersion = parseIfMatch(ifMatch);
        return withTenant(() -> success(commandService.createDraft(new AcceptanceReportCommands.CreateDraftCommand(
                acceptanceId, expectedActivityVersion, content(request)), commandActor())));
    }

    @PatchMapping("/{id}/report-versions/{versionId}")
    @Operation(summary = "修改验收报告草稿")
    @PreAuthorize("@ss.hasPermission('pms:acceptance:report:write')")
    public CommonResult<AcceptanceReportCommands.ReportResult> updateDraft(
            @PathVariable("id") Long acceptanceId,
            @PathVariable Long versionId,
            @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody AcceptanceReportDraftReqVO request) {
        if (request.getExpectedReportVersionNo() == null) throw exception(BAD_REQUEST);
        int expectedActivityVersion = parseIfMatch(ifMatch);
        return withTenant(() -> success(commandService.updateDraft(new AcceptanceReportCommands.UpdateDraftCommand(
                acceptanceId, versionId, expectedActivityVersion, request.getExpectedReportVersionNo(),
                content(request)), commandActor())));
    }

    @PostMapping("/{id}/report-versions/{versionId}/actions/publish")
    @Operation(summary = "发布或替换当前验收报告版本")
    @PreAuthorize("@ss.hasPermission('pms:acceptance:report:write')")
    public CommonResult<AcceptanceReportCommands.ReportResult> publish(
            @PathVariable("id") Long acceptanceId,
            @PathVariable Long versionId,
            @RequestHeader("If-Match") String ifMatch,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
            @Valid @RequestBody AcceptanceReportPublishReqVO request) {
        int expectedActivityVersion = parseIfMatch(ifMatch);
        String digest = digest(acceptanceId + ":" + versionId + ":" + expectedActivityVersion + ":"
                + JsonUtils.toJsonString(request));
        return withTenant(() -> success(commandService.publish(new AcceptanceReportCommands.PublishCommand(
                acceptanceId, versionId, expectedActivityVersion, request.getExpectedReportVersionNo(),
                request.getExpectedCurrentReportVersionId(), idempotencyKey, digest), commandActor())));
    }

    @PostMapping("/{id}/actions/revoke-current-version")
    @Operation(summary = "撤销当前验收报告版本")
    @PreAuthorize("@ss.hasPermission('pms:acceptance:report:write')")
    public CommonResult<AcceptanceReportCommands.ReportResult> revoke(
            @PathVariable("id") Long acceptanceId,
            @RequestHeader("If-Match") String ifMatch,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
            @Valid @RequestBody AcceptanceReportRevokeReqVO request) {
        int expectedActivityVersion = parseIfMatch(ifMatch);
        String digest = digest(acceptanceId + ":" + expectedActivityVersion + ":" + JsonUtils.toJsonString(request));
        return withTenant(() -> success(commandService.revoke(new AcceptanceReportCommands.RevokeCommand(
                acceptanceId, expectedActivityVersion, request.getExpectedCurrentReportVersionId(),
                request.getExpectedCurrentReportVersionNo(), idempotencyKey, digest), commandActor())));
    }

    @GetMapping("/{id}/report-versions/{versionId}/attachments/{sequence}/download")
    @Operation(summary = "取得已重验的验收报告附件下载事实")
    @PreAuthorize("@ss.hasPermission('pms:acceptance:report:download')")
    public CommonResult<AcceptanceReportQueryService.AttachmentView> download(
            @PathVariable("id") Long acceptanceId,
            @PathVariable Long versionId,
            @PathVariable @Positive Integer sequence) {
        return withTenant(() -> success(queryService.getDownloadFact(
                acceptanceId, versionId, sequence, queryActor())));
    }

    private AcceptanceReportCommands.DraftContent content(AcceptanceReportDraftReqVO request) {
        return new AcceptanceReportCommands.DraftContent(request.getAcceptanceTime(), request.getConclusionCode(),
                request.getConclusionText(), request.getAcceptorName());
    }

    private AcceptanceReportQueryService.Actor queryActor() {
        return new AcceptanceReportQueryService.Actor(TenantContextHolder.getRequiredTenantId(), currentUserId());
    }

    private AcceptanceReportCommands.Actor commandActor() {
        return new AcceptanceReportCommands.Actor(TenantContextHolder.getRequiredTenantId(), currentUserId(),
                UUID.randomUUID().toString());
    }

    private Long currentUserId() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId == null) throw exception(FORBIDDEN);
        return userId;
    }

    private int parseIfMatch(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.startsWith("W/")) normalized = normalized.substring(2).trim();
        if (normalized.length() >= 2 && normalized.startsWith("\"") && normalized.endsWith("\"")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        try {
            int version = Integer.parseInt(normalized);
            if (version < 0) throw new NumberFormatException();
            return version;
        } catch (NumberFormatException invalid) {
            throw exception(BAD_REQUEST, "If-Match必须是非负验收活动版本");
        }
    }

    private String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException(unavailable);
        }
    }

    private <T> T withTenant(Supplier<T> action) {
        if (TenantContextHolder.getTenantId() != null) return action.get();
        if (environment.getProperty("yudao.tenant.enable", Boolean.class, true)) throw exception(FORBIDDEN);
        AtomicReference<T> result = new AtomicReference<>();
        TenantUtils.execute(0L, () -> result.set(action.get()));
        return result.get();
    }
}
