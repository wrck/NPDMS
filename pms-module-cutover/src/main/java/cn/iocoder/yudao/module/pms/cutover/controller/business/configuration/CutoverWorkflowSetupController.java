package cn.iocoder.yudao.module.pms.cutover.controller.business.configuration;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.cutover.CutoverWorkflowApi;
import cn.iocoder.yudao.module.pms.cutover.service.configuration.CutoverWorkflowSetupService;
import cn.iocoder.yudao.module.pms.cutover.controller.business.support.CutoverRequestHeaders;
import org.springframework.http.HttpHeaders;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/pms/cutover-configurations/approval-workflow")
public class CutoverWorkflowSetupController {
    private final CutoverWorkflowApi workflow;
    private final CutoverWorkflowSetupService setup;
    @GetMapping
    @PreAuthorize("@ss.hasPermission('pms:cutover-config:query')")
    public CommonResult<String> inspect() { return CommonResult.success(workflow.definition(TenantContextHolder.getRequiredTenantId())); }
    @PostMapping("/actions/initialize")
    @PreAuthorize("@ss.hasPermission('pms:cutover-config:publish') && @ss.hasPermission('bpm:model:deploy')")
    public CommonResult<String> initialize(@RequestHeader HttpHeaders headers) {
        return CommonResult.success(setup.initialize(CutoverRequestHeaders.intent(headers)));
    }
}
