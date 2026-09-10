package cn.iocoder.yudao.module.pms.customer.controller.admin.customer;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.customer.controller.admin.customer.vo.CustomerDetailRespVO;
import cn.iocoder.yudao.module.pms.customer.service.query.CustomerDetailService;
import cn.iocoder.yudao.module.pms.customer.service.query.CustomerQueryService;
import cn.iocoder.yudao.module.pms.customer.service.security.CustomerContactAccessService;
import cn.iocoder.yudao.module.pms.customer.service.security.CustomerScopeContextService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.module.pms.customer.enums.ErrorCodeConstants.CUSTOMER_NOT_EXISTS;

@RestController
@RequestMapping("/api/v1/pms/customers")
@Validated
@RequiredArgsConstructor
public class CustomerCodeQueryController {
    private final CustomerQueryService queryService;
    private final CustomerScopeContextService scopeContextService;
    private final CustomerContactAccessService contactAccessService;
    private final CustomerDetailService detailService;

    @GetMapping("/by-code")
    @PreAuthorize("@ss.hasPermission('pms:customer:query')")
    public CommonResult<CustomerDetailRespVO> getByCode(@RequestParam("code") @NotBlank @Size(max = 64) String code) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        var customer = queryService.getByCode(tenantId, code, scopeContextService.resolve(tenantId, userId));
        if (customer == null) throw exception(CUSTOMER_NOT_EXISTS);
        return success(detailService.get(customer, contactAccessService.resolve(userId, true), userId));
    }
}
