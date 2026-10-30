package cn.iocoder.yudao.module.pms.asset.controller.admin.device;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.service.device.DeviceOrganizationProjectionService;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/pms/devices/actions/rebuild-organizations")
@RequiredArgsConstructor
public class DeviceOrganizationProjectionController {
    private final DeviceOrganizationProjectionService service;
    private final OperationAuditApi audit;

    @PostMapping
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    @PreAuthorize("@ss.hasPermission('pms:integration:configure')")
    public CommonResult<DeviceOrganizationProjectionService.Result> rebuild(@RequestParam(defaultValue="0") long afterId) {
        Long tenant = TenantContextHolder.getRequiredTenantId();
        var result = service.rebuildPage(tenant, afterId);
        audit.record(tenant, SecurityFrameworkUtils.getLoginUserId(),UUID.randomUUID().toString(),
                "DEVICE_ORGANIZATION_REBUILD","DeviceOrganization",String.valueOf(afterId),"SUCCESS",
                Map.of("processed",result.processed(),"updated",result.updated(),"afterId",result.afterId()));
        return CommonResult.success(result);
    }
}
