package cn.iocoder.yudao.module.pms.commerce.controller.admin.order;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.pms.commerce.controller.admin.order.vo.SalesOrderLineRespVO;
import cn.iocoder.yudao.module.pms.commerce.controller.admin.order.vo.SalesOrderRespVO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderLineDO;
import cn.iocoder.yudao.module.pms.commerce.service.contract.ContractAccessService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/api/v1/pms")
@Validated
@RequiredArgsConstructor
public class OrderController {

    private final ContractAccessService accessService;
    private final Environment environment;

    @GetMapping("/sales-orders")
    @PreAuthorize("@ss.hasPermission('pms:commerce:contract:query')")
    public CommonResult<PageResult<SalesOrderRespVO>> pageOrders(
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") @Min(1) int pageNo,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int pageSize) {
        return withTenant(() -> {
            PageResult<SalesOrderDO> page = accessService.pageSalesOrders(currentTenantId(), currentUserId(),
                    UUID.randomUUID().toString(), orderNo, status, (pageNo - 1) * pageSize, pageSize);
            return success(new PageResult<>(page.getList().stream().map(this::toOrder).toList(), page.getTotal()));
        });
    }

    @GetMapping("/order-lines")
    @PreAuthorize("@ss.hasPermission('pms:commerce:contract:query')")
    public CommonResult<PageResult<SalesOrderLineRespVO>> pageLines(
            @RequestParam(required = false) Long orderId,
            @RequestParam(required = false) String lineNo,
            @RequestParam(defaultValue = "1") @Min(1) int pageNo,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int pageSize) {
        return withTenant(() -> {
            PageResult<SalesOrderLineDO> page = accessService.pageSalesOrderLines(currentTenantId(), currentUserId(),
                    UUID.randomUUID().toString(), orderId, lineNo, (pageNo - 1) * pageSize, pageSize);
            return success(new PageResult<>(page.getList().stream().map(this::toLine).toList(), page.getTotal()));
        });
    }

    private SalesOrderRespVO toOrder(SalesOrderDO value) {
        return new SalesOrderRespVO(value.getId(), value.getSourceSystem(), value.getSourceVersion(),
                value.getCompanyCode(), value.getCompanyName(), value.getOrderType(), value.getOrderNo(),
                value.getCustomerCode(), value.getCustomerName(), value.getStatus(), value.getVersion());
    }

    private SalesOrderLineRespVO toLine(SalesOrderLineDO value) {
        return new SalesOrderLineRespVO(value.getId(), value.getOrderId(), value.getSourceSystem(),
                value.getSourceVersion(), value.getCompanyCode(), value.getOrderType(), value.getOrderNo(),
                value.getLineNo(), value.getItemCode(), value.getItemDesc(), value.getProductCode(),
                value.getOrderQty(), value.getOpenQty(), value.getDeliveredQty(), value.getUnitCode(),
                value.getUnitScale(), value.getQuantityStatus(), value.getStatus(), value.getVersion());
    }

    private Long currentTenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        return tenantId == null ? 0L : tenantId;
    }

    private Long currentUserId() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId == null) {
            throw exception(FORBIDDEN);
        }
        return userId;
    }

    private <T> T withTenant(Supplier<T> action) {
        if (TenantContextHolder.getTenantId() != null) {
            return action.get();
        }
        if (environment.getProperty("yudao.tenant.enable", Boolean.class, true)) {
            throw exception(FORBIDDEN);
        }
        AtomicReference<T> result = new AtomicReference<>();
        TenantUtils.execute(0L, () -> result.set(action.get()));
        return result.get();
    }
}
