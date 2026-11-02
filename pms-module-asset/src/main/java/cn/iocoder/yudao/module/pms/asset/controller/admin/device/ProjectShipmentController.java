package cn.iocoder.yudao.module.pms.asset.controller.admin.device;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.service.device.ProjectShipmentQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
@RestController
@RequestMapping("/api/v1/pms/asset/project-shipments")
@RequiredArgsConstructor
public class ProjectShipmentController {
    private final ProjectShipmentQueryService service;
    public record PageRequest(Long projectId, String sn, Integer pageNo, Integer pageSize) {}
    public record Shipment(Long id, String deviceSn, String productCode, String contractNo, String orderNo, String lineNo,
                           LocalDateTime shipmentTime, String carrierName, String expressNo, String receiverName,
                           LocalDate warrantyStartDate, Integer warrantyMonths) {}
    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('pms:device:query')")
    public CommonResult<PageResult<Shipment>> page(PageRequest request) {
        var page = service.page(new ProjectShipmentQueryService.Query(TenantContextHolder.getRequiredTenantId(),
                SecurityFrameworkUtils.getLoginUserId(), request.projectId(), request.sn(),
                request.pageNo() == null ? 1 : request.pageNo(), request.pageSize() == null ? 20 : request.pageSize()));
        return success(new PageResult<>(page.getList().stream().map(item -> {
            var row = item.shipment(); var logistics = item.logistics();
            return new Shipment(row.getId(), row.getDeviceSn(), row.getProductCode(), row.getContractNo(), row.getOrderNo(),
                    row.getLineNo(), row.getShipmentTime(), logistics == null ? null : logistics.carrierName(),
                    logistics == null ? null : logistics.expressNo(), logistics == null ? null : logistics.receiverName(),
                    row.getWarrantyStartDate(), row.getWarrantyMonths());
        }).toList(), page.getTotal()));
    }
}
