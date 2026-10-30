package cn.iocoder.yudao.module.system.controller.admin.permission;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.system.controller.admin.permission.vo.scope.*;
import cn.iocoder.yudao.module.system.service.permission.UserOrganizationScopeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 用户公司部门范围")
@RestController
@RequestMapping("/api/v1/pms/user-organization-scopes")
@Validated
@RequiredArgsConstructor
public class UserOrganizationScopeController {
    private final UserOrganizationScopeService scopeService;

    @GetMapping
    @Operation(summary = "分页查询用户公司部门范围，含停用历史")
    @PreAuthorize("@ss.hasAnyPermissions('system:user-scope:query', 'system:user-scope:manage')")
    public CommonResult<PageResult<UserOrganizationScopeRespVO>> getPage(@Valid UserOrganizationScopePageReqVO request) {
        return success(BeanUtils.toBean(scopeService.getPage(request), UserOrganizationScopeRespVO.class));
    }

    @PostMapping
    @Operation(summary = "新增用户公司部门范围")
    @PreAuthorize("@ss.hasPermission('system:user-scope:manage')")
    public CommonResult<Long> create(@Valid @RequestBody UserOrganizationScopeSaveReqVO request) {
        return success(scopeService.create(request));
    }

    @PostMapping("/{id}/actions/revise")
    @Operation(summary = "变更用户公司部门范围，保留原行")
    @PreAuthorize("@ss.hasPermission('system:user-scope:manage')")
    public CommonResult<Long> revise(@Positive @PathVariable("id") Long id,
                                    @Valid @RequestBody UserOrganizationScopeSaveReqVO request) {
        return success(scopeService.revise(id, request));
    }

    @PostMapping("/{id}/actions/disable")
    @Operation(summary = "停用用户公司部门范围，保留历史")
    @PreAuthorize("@ss.hasPermission('system:user-scope:manage')")
    public CommonResult<Boolean> disable(@Positive @PathVariable("id") Long id,
                                        @Valid @RequestBody UserOrganizationScopeDisableReqVO request) {
        scopeService.disable(id, request);
        return success(true);
    }
}
