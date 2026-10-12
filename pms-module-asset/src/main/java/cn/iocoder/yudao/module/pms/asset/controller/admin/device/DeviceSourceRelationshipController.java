package cn.iocoder.yudao.module.pms.asset.controller.admin.device;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsDeviceSourceRelationshipApi;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/pms/device-source-relationships")
public class DeviceSourceRelationshipController {
    private final DppmsDeviceSourceRelationshipApi relationships;
    @GetMapping
    @PreAuthorize("@ss.hasPermission(#deviceId != null ? 'pms:device:query' : 'pms:project:query')")
    public CommonResult<PageResult<DppmsDeviceSourceRelationshipApi.RelationshipRecord>> page(
            @RequestParam(required=false) Long projectId,@RequestParam(required=false) Long deviceId,
            @RequestParam(required=false) String deviceSn,
            @RequestParam(defaultValue="1") int pageNo,@RequestParam(defaultValue="20") int pageSize) {
        return success(relationships.pageRelationships(new DppmsDeviceSourceRelationshipApi.Query(
                TenantContextHolder.getRequiredTenantId(),SecurityFrameworkUtils.getLoginUserId(),projectId,deviceId,deviceSn,pageNo,pageSize)));
    }
}
