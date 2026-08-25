package cn.iocoder.yudao.module.pms.project.controller.admin.projectgovernance;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.pms.project.controller.admin.projectgovernance.vo.ProjectExceptionCloseReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projectgovernance.vo.ProjectGovernanceGuardRespVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projectgovernance.vo.ProjectGovernanceHistoryPageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projectgovernance.vo.ProjectGovernanceHistoryRespVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projectgovernance.vo.ProjectReopenReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projectgovernance.vo.ProjectRollbackReqVO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectgovernance.query.ProjectGovernanceHistoryPageQuery;
import cn.iocoder.yudao.module.pms.project.service.projectgovernance.ProjectGovernanceApplicationService;
import cn.iocoder.yudao.module.pms.project.service.projectgovernance.ProjectGovernanceGuardResult;
import cn.iocoder.yudao.module.pms.project.service.projectgovernance.ProjectGovernanceGuardService;
import cn.iocoder.yudao.module.pms.project.service.projectgovernance.ProjectGovernanceHistoryQueryService;
import cn.iocoder.yudao.module.pms.project.service.projectgovernance.command.ExceptionCloseProjectCommand;
import cn.iocoder.yudao.module.pms.project.service.projectgovernance.command.GovernanceActionResult;
import cn.iocoder.yudao.module.pms.project.service.projectgovernance.command.ReopenProjectCommand;
import cn.iocoder.yudao.module.pms.project.service.projectgovernance.command.RollbackProjectCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.BAD_REQUEST;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TREE_SCOPE_FORBIDDEN;

/** F-PROJ-006项目回退、异常关闭、受控重开及append-only历史HTTP边界。 */
@Tag(name = "管理后台 - PMS 项目异常治理")
@RestController
@RequestMapping("/pms/projects")
@Validated
@RequiredArgsConstructor
public class ProjectGovernanceCommandController {

    private final ProjectGovernanceGuardService guardService;
    private final ProjectGovernanceApplicationService applicationService;
    private final ProjectGovernanceHistoryQueryService historyQueryService;
    private final Environment environment;

    @GetMapping("/{id}/governance-guard")
    @Operation(summary = "查询项目治理完整守卫")
    @PreAuthorize("@ss.hasPermission('pms:project:governance:query')")
    public CommonResult<ProjectGovernanceGuardRespVO> getGuard(
            @PathVariable("id") Long projectId,
            @RequestParam("action") ProjectGovernanceGuardService.GovernanceAction action,
            @RequestParam(value = "pageNo", defaultValue = "1") @Min(1) Integer pageNo,
            @RequestParam(value = "pageSize", defaultValue = "20") @Min(1) @Max(200) Integer pageSize) {
        return withTrustedTenant(() -> {
            ProjectGovernanceGuardResult result = guardService.evaluate(projectId, action, actor());
            return success(toGuardResponse(result, pageNo, pageSize));
        });
    }

    @PostMapping("/{id}/actions/rollback")
    @Operation(summary = "回退项目至S0并重新进入待指派")
    @PreAuthorize("@ss.hasPermission('pms:project:rollback')")
    public CommonResult<GovernanceActionResult> rollback(
            @PathVariable("id") Long projectId,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
            @RequestHeader("If-Match") @NotBlank String ifMatch,
            @Valid @RequestBody ProjectRollbackReqVO request) {
        return withTrustedTenant(() -> {
            Integer expectedVersion = parseIfMatch(ifMatch);
            String digest = digest(projectId, expectedVersion, request);
            return success(applicationService.rollback(new RollbackProjectCommand(
                    projectId, expectedVersion, request.getGuardToken(), request.getReasonCode(),
                    request.getReasonDetail(), request.getReassignmentRequirement(), idempotencyKey, digest), actor()));
        });
    }

    @PostMapping("/{id}/actions/close")
    @Operation(summary = "异常关闭项目")
    @PreAuthorize("@ss.hasPermission('pms:project:close')")
    public CommonResult<GovernanceActionResult> close(
            @PathVariable("id") Long projectId,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
            @RequestHeader("If-Match") @NotBlank String ifMatch,
            @Valid @RequestBody ProjectExceptionCloseReqVO request) {
        return withTrustedTenant(() -> {
            Integer expectedVersion = parseIfMatch(ifMatch);
            String digest = digest(projectId, expectedVersion, request);
            List<ExceptionCloseProjectCommand.LegacyItem> legacyItems = request.getLegacyItems().stream()
                    .map(item -> new ExceptionCloseProjectCommand.LegacyItem(
                            item.getType(), item.getSummary(), item.getOwner(), item.getStatus()))
                    .toList();
            return success(applicationService.close(new ExceptionCloseProjectCommand(
                    projectId, expectedVersion, request.getGuardToken(), request.getReasonCode(),
                    request.getReasonDetail(), request.getBusinessBasis(), legacyItems,
                    idempotencyKey, digest), actor()));
        });
    }

    @PostMapping("/{id}/actions/reopen")
    @Operation(summary = "受控重开异常关闭项目")
    @PreAuthorize("@ss.hasPermission('pms:project:reopen')")
    public CommonResult<GovernanceActionResult> reopen(
            @PathVariable("id") Long projectId,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
            @RequestHeader("If-Match") @NotBlank String ifMatch,
            @Valid @RequestBody ProjectReopenReqVO request) {
        return withTrustedTenant(() -> {
            Integer expectedVersion = parseIfMatch(ifMatch);
            String digest = digest(projectId, expectedVersion, request);
            return success(applicationService.reopen(new ReopenProjectCommand(
                    projectId, expectedVersion, request.getReasonCode(), request.getReasonDetail(),
                    request.getExceptionCloseSnapshotId(), idempotencyKey, digest), actor()));
        });
    }

    @GetMapping("/{id}/governance-history")
    @Operation(summary = "分页查询项目append-only治理历史")
    @PreAuthorize("@ss.hasPermission('pms:project:governance:query')")
    public CommonResult<PageResult<ProjectGovernanceHistoryRespVO>> getHistory(
            @PathVariable("id") Long projectId,
            @Valid ProjectGovernanceHistoryPageReqVO request) {
        var page = historyQueryService.page(new ProjectGovernanceHistoryPageQuery(
                        currentTenantId(), projectId, request),
                new ProjectGovernanceHistoryQueryService.Actor(
                        currentTenantId(), SecurityFrameworkUtils.getLoginUserId()));
        return success(BeanUtils.toBean(page, ProjectGovernanceHistoryRespVO.class));
    }

    private ProjectGovernanceGuardRespVO toGuardResponse(
            ProjectGovernanceGuardResult result, int pageNo, int pageSize) {
        long offset = (long) (pageNo - 1) * pageSize;
        int fromIndex = (int) Math.min(offset, result.blockers().size());
        int toIndex = Math.min(fromIndex + pageSize, result.blockers().size());
        ProjectGovernanceGuardRespVO response = new ProjectGovernanceGuardRespVO();
        response.setProjectId(result.projectId());
        response.setProjectVersion(result.projectVersion());
        response.setLifecycleStatus(result.lifecycleStatus());
        response.setCurrentStage(result.currentStage());
        response.setAssignmentStatus(result.assignmentStatus());
        response.setTreeRootProjectId(result.treeRootProjectId());
        response.setTreeVersion(result.treeVersion());
        response.setAction(result.action());
        response.setAllowed(result.allowed());
        response.setGuardToken(result.guardToken());
        response.setProviderFacts(BeanUtils.toBean(
                result.providerFacts(), ProjectGovernanceGuardRespVO.ProviderFact.class));
        response.setBlockers(BeanUtils.toBean(
                result.blockers().subList(fromIndex, toIndex), ProjectGovernanceGuardRespVO.Blocker.class));
        response.setBlockerTotal((long) result.blockers().size());
        response.setBlockerPageNo(pageNo);
        response.setBlockerPageSize(pageSize);
        response.setCheckedAt(result.checkedAt());
        return response;
    }

    private ProjectGovernanceGuardService.Actor actor() {
        return new ProjectGovernanceGuardService.Actor(currentTenantId(),
                SecurityFrameworkUtils.getLoginUserId(), UUID.randomUUID().toString());
    }

    private Long currentTenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        return tenantId == null ? 0L : tenantId;
    }

    /**
     * 多租户启用时只接受 Web Filter 建立的上下文；单租户关闭租户模块时，按平台约定在本次调用内建立租户0上下文。
     */
    private <T> T withTrustedTenant(Supplier<T> action) {
        if (TenantContextHolder.getTenantId() != null) {
            return action.get();
        }
        if (environment.getProperty("yudao.tenant.enable", Boolean.class, true)) {
            throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        }
        AtomicReference<T> result = new AtomicReference<>();
        TenantUtils.execute(0L, () -> result.set(action.get()));
        return result.get();
    }

    private String digest(Long projectId, Integer expectedVersion, Object request) {
        return sha256(projectId + ":" + expectedVersion + ":" + JsonUtils.toJsonString(request));
    }

    private Integer parseIfMatch(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.startsWith("W/")) {
            normalized = normalized.substring(2).trim();
        }
        if (normalized.length() >= 2 && normalized.startsWith("\"") && normalized.endsWith("\"")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        try {
            int version = Integer.parseInt(normalized);
            if (version < 0) {
                throw new NumberFormatException("negative version");
            }
            return version;
        } catch (NumberFormatException ex) {
            throw exception(BAD_REQUEST, "If-Match必须是非负Project版本");
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
