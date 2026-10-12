package cn.iocoder.yudao.module.pms.asset.controller.admin.device;

import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.controller.admin.device.vo.SoftwareVersionRegistrationReqVO;
import cn.iocoder.yudao.module.pms.asset.service.version.DeviceSoftwareVersionMaintenanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/api/v1/pms/devices/{deviceId}/software-version")
@RequiredArgsConstructor
public class DeviceSoftwareVersionController {
    private final DeviceSoftwareVersionMaintenanceService service;
    @GetMapping
    @PreAuthorize("@ss.hasPermission('pms:device:query')")
    public CommonResult<DeviceSoftwareVersionMaintenanceService.Current> current(@PathVariable Long deviceId) {
        return success(service.current(TenantContextHolder.getRequiredTenantId(), SecurityFrameworkUtils.getLoginUserId(), deviceId));
    }
    @PostMapping("/registrations")
    @PreAuthorize("@ss.hasPermission('pms:device:update')")
    public CommonResult<DeviceSoftwareVersionMaintenanceService.Registration> register(@PathVariable Long deviceId,
            @Valid @RequestBody SoftwareVersionRegistrationReqVO request) {
        return success(service.register(TenantContextHolder.getRequiredTenantId(), SecurityFrameworkUtils.getLoginUserId(), deviceId,
                request.projectId(), request.expectedVersion(), request.requestKey(), request.values()));
    }
    @GetMapping("/history")
    @PreAuthorize("@ss.hasPermission('pms:device:query')")
    public CommonResult<PageResult<DeviceSoftwareVersionMaintenanceService.History>> history(@PathVariable Long deviceId,
            @RequestParam(defaultValue="1") int pageNo, @RequestParam(defaultValue="20") int pageSize) {
        return success(service.history(TenantContextHolder.getRequiredTenantId(), SecurityFrameworkUtils.getLoginUserId(), deviceId, pageNo, pageSize));
    }
}
