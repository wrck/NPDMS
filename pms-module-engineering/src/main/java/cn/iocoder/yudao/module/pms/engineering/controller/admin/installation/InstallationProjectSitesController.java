package cn.iocoder.yudao.module.pms.engineering.controller.admin.installation;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.engineering.service.installation.InstallationProjectSitesService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/pms/implementation/projects/{projectId}/installation-sites")
public class InstallationProjectSitesController {
    private final InstallationProjectSitesService service;

    @GetMapping
    @PreAuthorize("@ss.hasPermission('pms:imp-installation:create')")
    public CommonResult<InstallationProjectSitesService.Result> read(@PathVariable Long projectId,
                                                                      @RequestParam(required = false) Long siteId) {
        return CommonResult.success(service.read(projectId, siteId));
    }
}
