package cn.iocoder.yudao.module.pms.engineering.controller.admin.installation;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.engineering.service.installation.InstallationPlannedAddressService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/v1/pms/implementation/projects/{projectId}/planned-installation-addresses")
public class InstallationPlannedAddressController {
    private final InstallationPlannedAddressService service;
    @GetMapping
    @PreAuthorize("@ss.hasPermission('pms:imp-installation:query') && @ss.hasPermission('pms:sol-solution:query')")
    public CommonResult<InstallationPlannedAddressService.Plan> read(@PathVariable Long projectId) {
        return CommonResult.success(service.read(projectId));
    }
}
