package cn.iocoder.yudao.module.pms.project.controller.admin.projectauthorization;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.authorization.dto.AuthorizationGrantDTO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projectauthorization.vo.ProjectAuthorizationCreateReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projectauthorization.vo.ProjectAuthorizationPageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projectauthorization.vo.ProjectAuthorizationRespVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projectauthorization.vo.ProjectAuthorizationRevokeReqVO;
import cn.iocoder.yudao.module.pms.project.service.projectauthorization.ProjectAuthorizationApplicationService;
import cn.iocoder.yudao.module.pms.project.service.projectauthorization.ProjectAuthorizationApplicationService.CreateCommand;
import cn.iocoder.yudao.module.pms.project.service.projectauthorization.ProjectAuthorizationApplicationService.PageQuery;
import cn.iocoder.yudao.module.pms.project.service.projectauthorization.ProjectAuthorizationApplicationService.RevokeCommand;
import cn.iocoder.yudao.module.pms.project.service.projectauthorization.ProjectAuthorizationGuard.Actor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
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

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_AUTHORIZATION_INVALID;

@Tag(name = "管理后台 - 项目授权")
@RestController
@RequestMapping("/pms")
@Validated
@RequiredArgsConstructor
public class ProjectAuthorizationController {

    private final ProjectAuthorizationApplicationService applicationService;

    @PostMapping("/projects/{projectId}/authorization-grants")
    @Operation(summary = "创建项目授权")
    @PreAuthorize("@ss.hasPermission('pms:project:authorization:manage')")
    public CommonResult<ProjectAuthorizationRespVO> create(
            @PathVariable("projectId") Long projectId,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
            @Valid @RequestBody ProjectAuthorizationCreateReqVO request) {
        String digest = digest(projectId + ":" + JsonUtils.toJsonString(request));
        AuthorizationGrantDTO result = applicationService.create(new CreateCommand(
                projectId, request.getSubjectUserId(), request.getActionCode(), request.getScopeCode(),
                request.getEffectiveFrom(), request.getEffectiveTo(), request.getReason(),
                idempotencyKey, digest), actor());
        return success(toResponse(result));
    }

    @GetMapping("/projects/{projectId}/authorization-grants")
    @Operation(summary = "分页查询项目授权")
    @PreAuthorize("@ss.hasPermission('pms:project:authorization:query')")
    public CommonResult<PageResult<ProjectAuthorizationRespVO>> page(
            @PathVariable("projectId") Long projectId,
            @Valid ProjectAuthorizationPageReqVO request) {
        var result = applicationService.page(new PageQuery(
                projectId, request.getSubjectUserId(), request.getActionCode(), request.getScopeCode(),
                request.getStatusCode(), request.getEffectiveAt(), request.getPageNo(), request.getPageSize()),
                actor());
        return success(new PageResult<>(result.list().stream().map(this::toResponse).toList(), result.total()));
    }

    @GetMapping("/project-authorization-grants/{grantId}")
    @Operation(summary = "查询项目授权详情")
    @Parameter(name = "grantId", description = "授权ID", required = true)
    @PreAuthorize("@ss.hasPermission('pms:project:authorization:query')")
    public CommonResult<ProjectAuthorizationRespVO> get(@PathVariable("grantId") Long grantId) {
        return success(toResponse(applicationService.get(grantId, actor())));
    }

    @PostMapping("/project-authorization-grants/{grantId}/actions/revoke")
    @Operation(summary = "撤销项目授权")
    @PreAuthorize("@ss.hasPermission('pms:project:authorization:revoke')")
    public CommonResult<ProjectAuthorizationRespVO> revoke(
            @PathVariable("grantId") Long grantId,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
            @RequestHeader("If-Match") @NotBlank String ifMatch,
            @Valid @RequestBody ProjectAuthorizationRevokeReqVO request) {
        Integer expectedVersion = parseIfMatch(ifMatch);
        String digest = digest(grantId + ":" + expectedVersion + ":" + JsonUtils.toJsonString(request));
        return success(toResponse(applicationService.revoke(new RevokeCommand(
                grantId, expectedVersion, request.getReason(), idempotencyKey, digest), actor())));
    }

    private ProjectAuthorizationRespVO toResponse(AuthorizationGrantDTO grant) {
        ProjectAuthorizationRespVO response = new ProjectAuthorizationRespVO();
        response.setId(grant.id());
        response.setSubjectUserId(grant.subjectId());
        response.setProjectId(grant.resourceId());
        response.setActionCode(grant.actionCode());
        response.setScopeCode(grant.scopeCode());
        response.setEffectiveFrom(grant.effectiveFrom());
        response.setEffectiveTo(grant.effectiveTo());
        response.setStatusCode(grant.statusCode());
        response.setGrantedBy(grant.grantedBy());
        response.setGrantedAt(grant.grantedAt());
        response.setRevokedBy(grant.revokedBy());
        response.setRevokedAt(grant.revokedAt());
        response.setRevokeReason(grant.revokeReason());
        response.setVersion(grant.version());
        return response;
    }

    private Actor actor() {
        Long tenantId = TenantContextHolder.getTenantId();
        return new Actor(tenantId == null ? 0L : tenantId, SecurityFrameworkUtils.getLoginUserId());
    }

    private Integer parseIfMatch(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.startsWith("W/")) normalized = normalized.substring(2).trim();
        if (normalized.length() >= 2 && normalized.startsWith("\"") && normalized.endsWith("\"")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        try {
            int version = Integer.parseInt(normalized);
            if (version < 0) throw new NumberFormatException("negative version");
            return version;
        } catch (NumberFormatException ex) {
            throw exception(PROJECT_AUTHORIZATION_INVALID);
        }
    }

    private String digest(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 摘要算法不可用", ex);
        }
    }
}
