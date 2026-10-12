package cn.iocoder.yudao.module.pms.asset.controller.admin.device;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.service.version.SoftwareVersionLegacyExtensionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/pms/software-history-properties")
public class SoftwareHistoryPropertyController {
    private final SoftwareVersionLegacyExtensionService extensions;
    public record DefinitionRequest(Long existingRevisionId) {}
    @PostMapping("/dppms-definition")
    @PreAuthorize("@ss.hasPermission('pms:integration:configure') && @ss.hasPermission('pms:dynamic-form-template:manage')")
    public CommonResult<Long> definition(@RequestBody DefinitionRequest request) {
        return CommonResult.success(extensions.configureDefinition(TenantContextHolder.getRequiredTenantId(),
                SecurityFrameworkUtils.getLoginUserId(),request.existingRevisionId()));
    }
}
