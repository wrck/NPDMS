package cn.iocoder.yudao.module.pms.engineering.controller.admin.constructionplan;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.constructionplan.vo.ConstructionPlanChangePageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.constructionplan.vo.ConstructionPlanChangeRespVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.constructionplan.vo.ConstructionPlanCreateReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.constructionplan.vo.ConstructionPlanCursorPageRespVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.constructionplan.vo.ConstructionPlanRespVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.constructionplan.vo.ConstructionPlanRevisionPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.constructionplan.vo.ConstructionPlanRevisionRespVO;
import cn.iocoder.yudao.module.pms.engineering.service.constructionplan.ConstructionPlanApplicationService;
import cn.iocoder.yudao.module.pms.engineering.service.constructionplan.ConstructionPlanQueryService;
import cn.iocoder.yudao.module.pms.engineering.service.constructionplan.command.CreateInitialDurationCommand;
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
import org.springframework.web.bind.annotation.ModelAttribute;
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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.CONSTRUCTION_PLAN_PROJECT_FACT_INVALID;

@Tag(name = "管理后台 - PMS 项目工期")
@RestController
@RequestMapping("/api/v1/pms/construction-plans")
@Validated
@RequiredArgsConstructor
public class ConstructionPlanController {

    private final ConstructionPlanApplicationService applicationService;
    private final ConstructionPlanQueryService queryService;
    private final Environment environment;

    @PostMapping
    @Operation(summary = "首次创建并生效项目工期")
    @PreAuthorize("@ss.hasPermission('pms:construction-plan:duration-manage')")
    public CommonResult<ConstructionPlanRespVO> createInitial(
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
            @Valid @RequestBody ConstructionPlanCreateReqVO request) {
        return withTrustedTenant(() -> {
            Long actorId = requiredActorId();
            String requestDigest = digest(JsonUtils.toJsonString(request));
            var command = new CreateInitialDurationCommand(request.getProjectId(),
                    request.getCalculationBasis(), request.getStartDate(), request.getEndDate(),
                    request.getDurationDays(), request.getExpectedProjectVersion(), idempotencyKey,
                    requestDigest);
            return success(applicationService.createInitial(command,
                    new ConstructionPlanApplicationService.Actor(TenantContextHolder.getRequiredTenantId(),
                            actorId, UUID.randomUUID().toString())));
        });
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询项目工期详情")
    @PreAuthorize("@ss.hasAnyPermissions('pms:construction-plan:query','pms:construction-plan:duration-manage')")
    public CommonResult<ConstructionPlanRespVO> getById(@PathVariable("id") @Positive Long planId) {
        return withTrustedTenant(() -> success(queryService.getById(planId, queryActor())));
    }

    @GetMapping
    @Operation(summary = "按项目查询唯一工期；未录入时返回空业务结果")
    @PreAuthorize("@ss.hasAnyPermissions('pms:construction-plan:query','pms:construction-plan:duration-manage')")
    public CommonResult<ConstructionPlanRespVO> getByProjectId(
            @RequestParam("projectId") @Positive Long projectId) {
        return withTrustedTenant(() -> success(queryService.getByProjectId(projectId, queryActor())));
    }

    @GetMapping("/{id}/revisions")
    @Operation(summary = "稳定游标查询项目工期版本")
    @PreAuthorize("@ss.hasAnyPermissions('pms:construction-plan:query','pms:construction-plan:duration-manage')")
    public CommonResult<ConstructionPlanCursorPageRespVO<ConstructionPlanRevisionRespVO>> getRevisions(
            @PathVariable("id") @Positive Long planId,
            @Valid @ModelAttribute ConstructionPlanRevisionPageReqVO request) {
        return withTrustedTenant(() -> success(queryService.getRevisions(planId, request, queryActor())));
    }

    @GetMapping("/{id}/changes")
    @Operation(summary = "稳定游标查询项目工期变更")
    @PreAuthorize("@ss.hasAnyPermissions('pms:construction-plan:query','pms:construction-plan:duration-manage')")
    public CommonResult<ConstructionPlanCursorPageRespVO<ConstructionPlanChangeRespVO>> getChanges(
            @PathVariable("id") @Positive Long planId,
            @Valid @ModelAttribute ConstructionPlanChangePageReqVO request) {
        return withTrustedTenant(() -> success(queryService.getChanges(planId, request, queryActor())));
    }

    private ConstructionPlanQueryService.Actor queryActor() {
        return new ConstructionPlanQueryService.Actor(
                TenantContextHolder.getRequiredTenantId(), requiredActorId());
    }

    private Long requiredActorId() {
        Long actorId = SecurityFrameworkUtils.getLoginUserId();
        if (actorId == null || actorId <= 0) {
            throw exception(CONSTRUCTION_PLAN_PROJECT_FACT_INVALID);
        }
        return actorId;
    }

    private <T> T withTrustedTenant(Supplier<T> action) {
        if (TenantContextHolder.getTenantId() != null) return action.get();
        if (environment.getProperty("yudao.tenant.enable", Boolean.class, true)) {
            throw exception(CONSTRUCTION_PLAN_PROJECT_FACT_INVALID);
        }
        AtomicReference<T> result = new AtomicReference<>();
        TenantUtils.execute(0L, () -> result.set(action.get()));
        return result.get();
    }

    private String digest(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256摘要算法不可用", ex);
        }
    }
}
