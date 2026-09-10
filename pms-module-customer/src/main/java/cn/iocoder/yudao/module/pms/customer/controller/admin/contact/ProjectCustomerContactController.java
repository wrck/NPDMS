package cn.iocoder.yudao.module.pms.customer.controller.admin.contact;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.customer.api.query.CustomerQueryApi;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.contact.CustomerContactMasterDO;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.contact.ProjectCustomerContactDO;
import cn.iocoder.yudao.module.pms.customer.service.contact.CustomerContactMasterService;
import cn.iocoder.yudao.module.pms.customer.service.contact.ProjectContactWrite;
import cn.iocoder.yudao.module.pms.customer.service.contact.ProjectCustomerContactService;
import cn.iocoder.yudao.module.pms.customer.service.security.CustomerContactAccessService;
import cn.iocoder.yudao.module.pms.customer.service.security.CustomerFieldMaskingService;
import cn.iocoder.yudao.module.pms.project.api.contact.ProjectContactContextApi;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/api/v1/pms/projects/{projectId}/customer-contacts")
@Validated
@RequiredArgsConstructor
public class ProjectCustomerContactController {
    private final ProjectCustomerContactService service;
    private final CustomerContactAccessService contactAccess;
    private final CustomerFieldMaskingService masking;
    private final CustomerQueryApi customers;

    public record ContextResponse(ProjectContactContextApi.Context project, String customerName, String customerStatus, boolean sensitiveRead) {}
    public record ImportRequest(@NotNull @Min(0) Integer expectedProjectVersion) {}
    public record RestoreRequest(@NotNull @Min(0) Integer expectedProjectVersion, @Min(0) @Max(1) Integer status) {}
    public record AssociateRequest(@NotNull @Min(0) Integer expectedProjectVersion, @NotNull @Positive Long customerId) {}

    @PostMapping("/actions/associate-customer")
    @PreAuthorize("@ss.hasPermission('pms:project:update')")
    public CommonResult<ProjectContactContextApi.Context> associateCustomer(@PathVariable Long projectId, @Valid @RequestBody AssociateRequest request) {
        return success(service.associateCustomer(actor(), projectId, request.expectedProjectVersion(), request.customerId()));
    }

    @GetMapping("/context")
    @PreAuthorize("@ss.hasPermission('pms:customer-contact:query')")
    public CommonResult<ContextResponse> context(@PathVariable Long projectId) {
        var context = service.context(actor(), projectId);
        var customer = context.customerId() == null ? null : customers.getCustomer(context.customerId());
        return success(new ContextResponse(context, customer == null ? null : customer.name(), customer == null ? null : customer.lifecycleStatus(), sensitive()));
    }

    @GetMapping
    @PreAuthorize("@ss.hasPermission('pms:customer-contact:query')")
    public CommonResult<PageResult<ProjectCustomerContactDO>> page(@PathVariable Long projectId, @Valid ProjectContactPageReqVO query) {
        var result = service.page(actor(), projectId, query.getStatus(), query.getName(), query);
        if (!sensitive()) result.getList().forEach(row -> {
            row.setMobile(masking.maskPhone(row.getMobile())); row.setPhone(masking.maskPhone(row.getPhone())); row.setEmail(masking.maskEmail(row.getEmail()));
        });
        return success(result);
    }

    @GetMapping("/sources")
    @PreAuthorize("@ss.hasPermission('pms:customer-contact:query')")
    public CommonResult<PageResult<CustomerContactMasterDO>> sources(@PathVariable Long projectId, @Valid ProjectContactPageReqVO page) {
        var result = service.sources(actor(), projectId, page.getName(), page);
        if (!sensitive()) result.getList().forEach(row -> {
            row.setMobile(masking.maskPhone(row.getMobile())); row.setPhone(masking.maskPhone(row.getPhone())); row.setEmail(masking.maskEmail(row.getEmail()));
        });
        return success(result);
    }

    @GetMapping("/history")
    @PreAuthorize("@ss.hasPermission('pms:customer-contact:query')")
    public CommonResult<PageResult<cn.iocoder.yudao.module.pms.customer.dal.dataobject.contact.ContactHistoryDO>> history(
            @PathVariable Long projectId, @Valid PageParam page) {
        var result = service.history(actor(), projectId, page);
        if (!sensitive()) result.getList().forEach(row -> {
            row.setBeforeValues(maskHistory(row.getBeforeValues())); row.setAfterValues(maskHistory(row.getAfterValues()));
        });
        return success(result);
    }

    private String maskHistory(String value) {
        if (value == null) return null;
        var data = cn.hutool.json.JSONUtil.parseObj(value);
        data.set("mobile", masking.maskPhone(data.getStr("mobile")));
        data.set("phone", masking.maskPhone(data.getStr("phone")));
        data.set("email", masking.maskEmail(data.getStr("email")));
        return data.toString();
    }

    @PostMapping("/actions/import-defaults")
    @PreAuthorize("@ss.hasPermission('pms:customer-contact:create')")
    public CommonResult<Integer> importDefaults(@PathVariable Long projectId, @Valid @RequestBody ImportRequest request) {
        return success(service.importDefaults(actor(), projectId, request.expectedProjectVersion()));
    }

    @PostMapping
    @PreAuthorize("@ss.hasPermission('pms:customer-contact:create')")
    public CommonResult<Long> create(@PathVariable Long projectId, @Valid @RequestBody ProjectContactSaveReqVO request,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max=128) String key) {
        return success(service.create(actor(), request.command(projectId, null), key).getId());
    }

    @PutMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('pms:customer-contact:update')")
    public CommonResult<Boolean> update(@PathVariable Long projectId, @PathVariable Long id,
            @Valid @RequestBody ProjectContactSaveReqVO request) {
        service.update(actor(), request.command(projectId, id)); return success(true);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('pms:customer-contact:delete')")
    public CommonResult<Boolean> delete(@PathVariable Long projectId, @PathVariable Long id,
            @RequestParam Integer expectedProjectVersion, @RequestHeader("If-Match") Integer version,
            @RequestParam(defaultValue="false") boolean confirmNoPrimary) {
        service.delete(actor(), new ProjectContactWrite(projectId, id, null, expectedProjectVersion, version,
                null, false, 0, confirmNoPrimary));
        return success(true);
    }

    @PostMapping({"/{id}/actions/restore-enabled", "/{id}/actions/restore"})
    @PreAuthorize("@ss.hasPermission('pms:customer-contact:update')")
    public CommonResult<Boolean> restoreEnabled(@PathVariable Long projectId, @PathVariable Long id,
            @Valid @RequestBody RestoreRequest request, @RequestHeader("If-Match") Integer version) {
        service.restore(actor(), projectId, id, request.expectedProjectVersion(), version, request.status() == null ? 0 : request.status());
        return success(true);
    }

    private boolean sensitive() { return contactAccess.resolve(SecurityFrameworkUtils.getLoginUserId(), true) == CustomerFieldMaskingService.ContactAccess.RAW; }
    private CustomerContactMasterService.Actor actor() {
        return new CustomerContactMasterService.Actor(TenantContextHolder.getRequiredTenantId(), SecurityFrameworkUtils.getLoginUserId());
    }
}
