package cn.iocoder.yudao.module.pms.asset.controller.admin.device;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.device.DeviceQueryApi;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsAssetSourceHistoryApi;
import cn.iocoder.yudao.module.pms.asset.service.security.DeviceAccessScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
@RestController
@RequestMapping("/api/v1/pms/devices")
@RequiredArgsConstructor
public class DeviceSourceHistoryController {
    private final DppmsAssetSourceHistoryApi histories;
    private final DeviceQueryApi devices;
    private final DeviceAccessScopeService scope;
    @GetMapping("/{id}/source-software-observations")
    @PreAuthorize("@ss.hasPermission('pms:device:query')")
    public CommonResult<PageResult<DppmsAssetSourceHistoryApi.ObservationRecord>> observations(
            @PathVariable Long id,@RequestParam(defaultValue="1") int pageNo,@RequestParam(defaultValue="20") int pageSize) {
        Long tenant=TenantContextHolder.getRequiredTenantId(), actor=SecurityFrameworkUtils.getLoginUserId();
        scope.assertVisible(tenant,actor,id);
        if (pageNo<1||pageSize<1||pageSize>100) throw new IllegalArgumentException("来源版本分页参数无效");
        var device=devices.getDevice(id);
        if (device==null||device.projectId()==null) return success(new PageResult<>(List.of(),0L));
        return success(histories.pageObservations(new DppmsAssetSourceHistoryApi.Query(tenant,actor,
                device.projectId(),device.sn(),pageNo,pageSize)));
    }
    @GetMapping("/{id}/source-factory-versions")
    @PreAuthorize("@ss.hasPermission('pms:device:query')")
    public CommonResult<PageResult<DppmsAssetSourceHistoryApi.FactoryRecord>> factories(
            @PathVariable Long id,@RequestParam(defaultValue="1") int pageNo,@RequestParam(defaultValue="20") int pageSize) {
        Long tenant=TenantContextHolder.getRequiredTenantId(), actor=SecurityFrameworkUtils.getLoginUserId();
        scope.assertVisible(tenant,actor,id);
        if (pageNo<1||pageSize<1||pageSize>100) throw new IllegalArgumentException("来源版本分页参数无效");
        var device=devices.getDevice(id);
        if (device==null||device.projectId()==null) return success(new PageResult<>(List.of(),0L));
        return success(histories.pageFactories(new DppmsAssetSourceHistoryApi.Query(tenant,actor,
                device.projectId(),device.sn(),pageNo,pageSize)));
    }

}
