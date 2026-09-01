package cn.iocoder.yudao.module.pms.project.controller.admin.stagegate;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateProcessStartFact;
import cn.iocoder.yudao.module.pms.project.controller.admin.stagegate.vo.ProjectStageAdvanceReadinessRespVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.stagegate.vo.ProjectStageAdvanceReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.stagegate.vo.ProjectStageGateProcessDefinitionRespVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.stagegate.vo.ProjectStageGateProcessStartReqVO;
import cn.iocoder.yudao.module.pms.project.service.stagegate.ProjectStageAdvanceApplicationService;
import cn.iocoder.yudao.module.pms.project.service.stagegate.ProjectStageReadinessService;
import cn.iocoder.yudao.module.pms.project.service.stagegate.command.ProjectStageAdvanceCommand;
import cn.iocoder.yudao.module.pms.project.service.stagegate.command.ProjectStageAdvanceResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
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
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TREE_SCOPE_FORBIDDEN;

@Tag(name = "管理后台 - PMS 项目阶段准出与推进")
@RestController
@RequestMapping("/pms/projects")
@Validated
@RequiredArgsConstructor
public class ProjectStageAdvanceController {

    private final ProjectStageReadinessService readinessService;
    private final ProjectStageAdvanceApplicationService applicationService;
    private final Environment environment;

    @GetMapping("/{id}/stage-advance-readiness")
    @Operation(summary = "查询当前阶段准出门禁")
    @PreAuthorize("@ss.hasPermission('pms:project:query')")
    public CommonResult<ProjectStageAdvanceReadinessRespVO> readiness(@PathVariable("id") Long projectId) {
        return withTrustedTenant(() -> success(ProjectStageAdvanceReadinessRespVO.from(
                readinessService.evaluate(projectId, SecurityFrameworkUtils.getLoginUserId()))));
    }

    @GetMapping("/{id}/stage-gates/{gateReferenceId}/process-definitions")
    @Operation(summary = "查询当前Gate可启动的Flowable定义身份")
    @PreAuthorize("@ss.hasPermission('pms:project:update')")
    public CommonResult<List<ProjectStageGateProcessDefinitionRespVO>> processDefinitions(
            @PathVariable("id") Long projectId, @PathVariable("gateReferenceId") Long gateReferenceId) {
        return withTrustedTenant(() -> success(applicationService.listDefinitions(
                projectId, gateReferenceId, actor()).stream()
                .map(ProjectStageGateProcessDefinitionRespVO::from).toList()));
    }

    @PostMapping("/{id}/stage-gates/{gateReferenceId}/actions/start-process")
    @Operation(summary = "启动当前Gate审批流程")
    @PreAuthorize("@ss.hasPermission('pms:project:update')")
    public CommonResult<ProjectStageGateProcessStartFact> startProcess(
            @PathVariable("id") Long projectId, @PathVariable("gateReferenceId") Long gateReferenceId,
            @RequestHeader("If-Match") @NotBlank String ifMatch,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
            @Valid @RequestBody ProjectStageGateProcessStartReqVO request) {
        return withTrustedTenant(() -> success(applicationService.startProcess(projectId, gateReferenceId,
                parseIfMatch(ifMatch), blankToNull(request.getProcessDefinitionId()), idempotencyKey,
                digest(projectId, gateReferenceId, parseIfMatch(ifMatch), request), actor())));
    }

    @PostMapping("/{id}/actions/advance-stage")
    @Operation(summary = "按当前全部EXIT Gate结果推进相邻阶段")
    @PreAuthorize("@ss.hasPermission('pms:project:update')")
    public CommonResult<ProjectStageAdvanceResult> advance(
            @PathVariable("id") Long projectId,
            @RequestHeader("If-Match") @NotBlank String ifMatch,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
            @Valid @RequestBody ProjectStageAdvanceReqVO request) {
        return withTrustedTenant(() -> {
            Integer expectedVersion = parseIfMatch(ifMatch);
            return success(applicationService.advance(new ProjectStageAdvanceCommand(projectId, expectedVersion,
                    request.getExpectedCurrentStage(), request.getExpectedTreeVersion(), idempotencyKey,
                    digest(projectId, expectedVersion, request)), actor()));
        });
    }

    private ProjectStageAdvanceApplicationService.Actor actor() {
        return new ProjectStageAdvanceApplicationService.Actor(currentTenantId(),
                SecurityFrameworkUtils.getLoginUserId(), UUID.randomUUID().toString());
    }

    private Long currentTenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        return tenantId == null ? 0L : tenantId;
    }

    private <T> T withTrustedTenant(Supplier<T> action) {
        if (TenantContextHolder.getTenantId() != null) return action.get();
        if (environment.getProperty("yudao.tenant.enable", Boolean.class, true)) {
            throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        }
        AtomicReference<T> result = new AtomicReference<>();
        TenantUtils.execute(0L, () -> result.set(action.get()));
        return result.get();
    }

    private Integer parseIfMatch(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.startsWith("W/")) normalized = normalized.substring(2).trim();
        if (normalized.length() >= 2 && normalized.startsWith("\"") && normalized.endsWith("\"")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        try {
            int parsed = Integer.parseInt(normalized);
            if (parsed < 0) throw new NumberFormatException("negative version");
            return parsed;
        } catch (NumberFormatException ex) {
            throw exception(BAD_REQUEST, "If-Match必须是非负Project版本");
        }
    }

    private String digest(Object... values) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(JsonUtils.toJsonString(values).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
