package cn.iocoder.yudao.module.pms.integration.deviceops;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import jakarta.annotation.security.PermitAll;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

/** Native bridge deliberately has no admin-api prefix or browser bearer token. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/pms/integration/device-ops/desktop/connections")
@ConditionalOnProperty(prefix="pms.integration.device-ops", name="enabled", havingValue="true")
public class DacDesktopConnectProofController {
    private final DacDesktopConnectService connections;

    @PostMapping("/{id}/proof")
    @PermitAll // Nonce possession + signature + tenant/environment binding are enforced by the service.
    @ApiAccessLog(requestEnable=false, responseEnable=false)
    public CommonResult<Boolean> proof(@PathVariable("id") String id, @RequestBody DacDesktopConnectService.Proof proof) {
        connections.accept(id, proof);
        return CommonResult.success(true);
    }
}
