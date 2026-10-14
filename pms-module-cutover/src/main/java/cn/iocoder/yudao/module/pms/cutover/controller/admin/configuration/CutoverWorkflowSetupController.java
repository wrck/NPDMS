package cn.iocoder.yudao.module.pms.cutover.controller.admin.configuration;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.cutover.CutoverWorkflowApi;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/pms/cutover-config/approval-workflow")
public class CutoverWorkflowSetupController {
    private final CutoverWorkflowApi workflow;
    @GetMapping
    @PreAuthorize("@ss.hasPermission('pms:cutover-config:query')")
    public CommonResult<String> inspect() { return CommonResult.success(workflow.definition(TenantContextHolder.getRequiredTenantId())); }
    @PostMapping("/actions/initialize")
    @PreAuthorize("@ss.hasPermission('pms:cutover-config:publish') && @ss.hasPermission('bpm:model:deploy')")
    public CommonResult<String> initialize() {
        return CommonResult.success(workflow.provision(TenantContextHolder.getRequiredTenantId(), SecurityFrameworkUtils.getLoginUserId()));
    }
}
