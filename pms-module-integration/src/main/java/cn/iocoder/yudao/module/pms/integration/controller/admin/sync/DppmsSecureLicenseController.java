package cn.iocoder.yudao.module.pms.integration.controller.admin.sync;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.integration.sync.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
@RestController @RequestMapping("/api/v1/pms/integration") @RequiredArgsConstructor
public class DppmsSecureLicenseController {
    private final SyncConnectionService connections;
    @PostMapping("/templates/dppms-secure-license")
    @PreAuthorize("@ss.hasPermission('pms:integration:configure')")
    @cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog(requestEnable=false)
    public CommonResult<SyncDefinition> template(@RequestBody DppmsSecureLicenseSyncTemplate.Request request) {
        connections.required(request.connectionId());return success(DppmsSecureLicenseSyncTemplate.create(request));
    }
}
