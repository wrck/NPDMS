package cn.iocoder.yudao.module.pms.asset.controller.admin.device;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.configurationlog.DeviceLogParsedFactDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.configurationlog.DeviceLogParsedFactMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.configurationlog.query.DeviceParsedLogHistoryQuery;
import cn.iocoder.yudao.module.pms.asset.service.device.DeviceQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequiredArgsConstructor
@RequestMapping("/pms/asset/devices")
public class DeviceParsedLogController {
    private final DeviceQueryService devices;
    private final DeviceLogParsedFactMapper facts;
    @GetMapping("/{id}/log-parses") @PreAuthorize("@ss.hasPermission('pms:device:query')")
    public CommonResult<List<DeviceLogParsedFactDO>> history(@PathVariable Long id) {
        if (devices.getDevice(id)==null) throw new IllegalArgumentException("设备不存在");
        return CommonResult.success(facts.history(new DeviceParsedLogHistoryQuery(TenantContextHolder.getRequiredTenantId(),id)));
    }
}
