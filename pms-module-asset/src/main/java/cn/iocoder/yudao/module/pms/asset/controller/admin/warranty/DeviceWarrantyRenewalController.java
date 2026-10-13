package cn.iocoder.yudao.module.pms.asset.controller.admin.warranty;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.warranty.DeviceWarrantyRenewalRecordDO;
import cn.iocoder.yudao.module.pms.asset.service.warranty.DeviceWarrantyRenewalQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/api/v1/pms/asset/warranty-renewals")
@RequiredArgsConstructor
public class DeviceWarrantyRenewalController {
    private final DeviceWarrantyRenewalQueryService service;

    public record PageRequest(Long projectId, Long deviceId, String sn, Integer pageNo, Integer pageSize) {}

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('pms:device:query')")
    public CommonResult<PageResult<DeviceWarrantyRenewalRecordDO>> page(PageRequest request) {
        return success(service.page(new DeviceWarrantyRenewalQueryService.Query(
                TenantContextHolder.getRequiredTenantId(), SecurityFrameworkUtils.getLoginUserId(),
                request.projectId(), request.deviceId(), request.sn(),
                request.pageNo() == null ? 1 : request.pageNo(), request.pageSize() == null ? 20 : request.pageSize())));
    }
}
