package cn.iocoder.yudao.module.pms.integration.controller.admin.deviceops;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.pms.integration.deviceops.DacDesktopConnectService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/pms/integration/device-ops/desktop/connections")
@ConditionalOnProperty(prefix="pms.integration.device-ops", name="enabled", havingValue="true")
public class DacDesktopConnectController {
    private final DacDesktopConnectService connections;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @ApiAccessLog(requestEnable=false, responseEnable=false)
    public CommonResult<DacDesktopConnectService.Started> begin(@RequestBody DacDesktopConnectService.Begin input) {
        return CommonResult.success(connections.begin(input, SecurityFrameworkUtils.getLoginUserId()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @ApiAccessLog(requestEnable=false, responseEnable=false)
    public CommonResult<DacDesktopConnectService.Result> poll(@PathVariable("id") String id) {
        return CommonResult.success(connections.poll(id, SecurityFrameworkUtils.getLoginUserId()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @ApiAccessLog(requestEnable=false, responseEnable=false)
    public CommonResult<Boolean> cancel(@PathVariable("id") String id) {
        connections.cancel(id, SecurityFrameworkUtils.getLoginUserId());
        return CommonResult.success(true);
    }

}
