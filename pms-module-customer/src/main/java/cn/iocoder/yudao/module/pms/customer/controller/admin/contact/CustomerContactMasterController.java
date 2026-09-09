package cn.iocoder.yudao.module.pms.customer.controller.admin.contact;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.contact.CustomerContactMasterDO;
import cn.iocoder.yudao.module.pms.customer.service.contact.CustomerContactMasterService;
import cn.iocoder.yudao.module.pms.customer.service.security.CustomerContactAccessService;
import cn.iocoder.yudao.module.pms.customer.service.security.CustomerFieldMaskingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/** Customer-owned contact master; no project-local update is routed to these commands. */
@RestController
@RequestMapping("/api/v1/pms/customer-contacts")
@Validated
@RequiredArgsConstructor
public class CustomerContactMasterController {
    private final CustomerContactMasterService service;
    private final CustomerContactAccessService contactAccess;
    private final CustomerFieldMaskingService masking;
    private final cn.iocoder.yudao.module.pms.customer.api.contact.CustomerContactMasterApi contacts;

    @GetMapping
    @PreAuthorize("@ss.hasPermission('pms:customer-contact:query')")
    public CommonResult<PageResult<cn.iocoder.yudao.module.pms.customer.api.contact.CustomerContactMasterApi.Contact>> page(@Valid ContactMasterPageReqVO request) {
        var result = contacts.page(new cn.iocoder.yudao.module.pms.customer.api.contact.CustomerContactMasterApi.PageQuery(
                request.getCustomerId(), request.getName(), null, request.getStatus(), request.getPageNo(), request.getPageSize()));
        return success(new PageResult<>(result.list(), result.total()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('pms:customer-contact:query')")
    public CommonResult<CustomerContactMasterDO> get(@PathVariable Long id, @RequestParam Long customerId) {
        return success(mask(service.get(actor(), customerId, id)));
    }

    @PostMapping
    @PreAuthorize("@ss.hasPermission('pms:customer-contact:create')")
    public CommonResult<Long> create(@Valid @RequestBody ContactMasterSaveReqVO request,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max=128) String idempotencyKey) {
        return success(service.create(actor(), request.command(null), idempotencyKey).getId());
    }

    @PutMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('pms:customer-contact:update')")
    public CommonResult<Boolean> update(@PathVariable Long id, @Valid @RequestBody ContactMasterSaveReqVO request) {
        service.update(actor(), request.command(id));
        return success(true);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('pms:customer-contact:delete')")
    public CommonResult<Boolean> delete(@PathVariable Long id, @RequestParam Long customerId,
            @RequestHeader("If-Match") Integer version) {
        service.delete(actor(), customerId, id, version);
        return success(true);
    }

    private CustomerContactMasterDO mask(CustomerContactMasterDO row) {
        if (contactAccess.resolve(SecurityFrameworkUtils.getLoginUserId(), true) != CustomerFieldMaskingService.ContactAccess.RAW) {
            row.setMobile(masking.maskPhone(row.getMobile())); row.setPhone(masking.maskPhone(row.getPhone()));
            row.setEmail(masking.maskEmail(row.getEmail()));
        }
        return row;
    }

    private CustomerContactMasterService.Actor actor() {
        return new CustomerContactMasterService.Actor(TenantContextHolder.getRequiredTenantId(), SecurityFrameworkUtils.getLoginUserId());
    }
}
