package cn.iocoder.yudao.module.pms.platform.controller.admin.dynamicform;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.pms.platform.controller.admin.dynamicform.vo.DynamicFormInstanceCreateReqVO;
import cn.iocoder.yudao.module.pms.platform.controller.admin.dynamicform.vo.DynamicFormInstanceCreatedRespVO;
import cn.iocoder.yudao.module.pms.platform.controller.admin.dynamicform.vo.DynamicFormInstancePageReqVO;
import cn.iocoder.yudao.module.pms.platform.controller.admin.dynamicform.vo.DynamicFormInstancePatchReqVO;
import cn.iocoder.yudao.module.pms.platform.controller.admin.dynamicform.vo.DynamicFormInstancePatchRespVO;
import cn.iocoder.yudao.module.pms.platform.controller.admin.dynamicform.vo.DynamicFormInstanceRespVO;
import cn.iocoder.yudao.module.pms.platform.controller.admin.dynamicform.vo.DynamicFormInstanceSummaryRespVO;
import cn.iocoder.yudao.module.pms.platform.service.dynamicform.DynamicFormCommandService;
import cn.iocoder.yudao.module.pms.platform.service.dynamicform.DynamicFormCommands;
import cn.iocoder.yudao.module.pms.platform.service.dynamicform.DynamicFormQueryService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_SCOPE_FORBIDDEN;

@Tag(name = "管理后台 - PMS 共享动态表单实例")
@RestController
@RequestMapping("/api/v1/pms")
@Validated
@RequiredArgsConstructor
public class DynamicFormInstanceController {

    private final DynamicFormCommandService commandService;
    private final DynamicFormQueryService queryService;
    private final Environment environment;

    @GetMapping("/dynamic-form-instances")
    @Operation(summary = "分页查询手工动态表单实例")
    @PreAuthorize("@ss.hasPermission('pms:dynamic-form-instance:query')")
    public CommonResult<PageResult<DynamicFormInstanceSummaryRespVO>> page(@Valid DynamicFormInstancePageReqVO request) {
        return trusted(() -> {
            var result = queryService.pageInstances(actor(), request.getPageNo(), request.getPageSize());
            return success(new PageResult<>(result.list().stream().map(DynamicFormInstanceSummaryRespVO::of).toList(),
                    result.total()));
        });
    }

    @PostMapping("/dynamic-form-instances")
    @Operation(summary = "按明确发布修订创建手工动态表单实例")
    @PreAuthorize("@ss.hasPermission('pms:dynamic-form-instance:create')")
    public CommonResult<DynamicFormInstanceCreatedRespVO> create(
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String key,
            @Valid @RequestBody DynamicFormInstanceCreateReqVO request) {
        return trusted(() -> success(DynamicFormInstanceCreatedRespVO.of(commandService.createInstance(
                new DynamicFormCommands.CreateInstance(actor(), request.getTemplateRevisionId(),
                        request.getExpectedTemplateVersion(), request.getInstanceName(), key)))));
    }

    @GetMapping("/dynamic-form-instances/{instanceId}")
    @Operation(summary = "查询冻结动态表单实例")
    @PreAuthorize("@ss.hasPermission('pms:dynamic-form-instance:query')")
    public CommonResult<DynamicFormInstanceRespVO> get(@PathVariable Long instanceId) {
        return trusted(() -> success(DynamicFormInstanceRespVO.of(queryService.getInstance(actor(), instanceId))));
    }

    @PatchMapping("/dynamic-form-instances/{instanceId}")
    @Operation(summary = "部分保存动态表单普通字段")
    @PreAuthorize("@ss.hasPermission('pms:dynamic-form-instance:update')")
    public CommonResult<DynamicFormInstancePatchRespVO> patch(
            @PathVariable Long instanceId, @RequestHeader("If-Match") Integer version,
            @Valid @RequestBody DynamicFormInstancePatchReqVO request) {
        return trusted(() -> success(DynamicFormInstancePatchRespVO.of(commandService.patchInstance(
                new DynamicFormCommands.PatchInstance(actor(), instanceId, version,
                        request.getValues(), java.util.UUID.randomUUID().toString())))));
    }

    private DynamicFormCommands.Actor actor() {
        return new DynamicFormCommands.Actor(TenantContextHolder.getRequiredTenantId(),
                SecurityFrameworkUtils.getLoginUserId());
    }

    private <T> T trusted(Supplier<T> action) {
        if (TenantContextHolder.getTenantId() != null) return action.get();
        if (environment.getProperty("yudao.tenant.enable", Boolean.class, true)) {
            throw exception(FILE_SCOPE_FORBIDDEN);
        }
        AtomicReference<T> result = new AtomicReference<>();
        TenantUtils.execute(0L, () -> result.set(action.get()));
        return result.get();
    }
}
