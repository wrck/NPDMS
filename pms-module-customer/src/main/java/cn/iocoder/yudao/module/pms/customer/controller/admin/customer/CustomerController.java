package cn.iocoder.yudao.module.pms.customer.controller.admin.customer;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.customer.controller.admin.customer.vo.CustomerCreateReqVO;
import cn.iocoder.yudao.module.pms.customer.controller.admin.customer.vo.CustomerDetailRespVO;
import cn.iocoder.yudao.module.pms.customer.controller.admin.customer.vo.CustomerLifecycleReqVO;
import cn.iocoder.yudao.module.pms.customer.controller.admin.customer.vo.CustomerPageReqVO;
import cn.iocoder.yudao.module.pms.customer.controller.admin.customer.vo.CustomerRespVO;
import cn.iocoder.yudao.module.pms.customer.controller.admin.customer.vo.CustomerUpdateReqVO;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.customer.CustomerMasterDO;
import cn.iocoder.yudao.module.pms.customer.service.customer.CustomerApplicationService;
import cn.iocoder.yudao.module.pms.customer.service.customer.command.CreateCustomerCommand;
import cn.iocoder.yudao.module.pms.customer.service.customer.command.CustomerCommandResult;
import cn.iocoder.yudao.module.pms.customer.service.customer.command.CustomerLifecycleCommand;
import cn.iocoder.yudao.module.pms.customer.service.customer.command.UpdateCustomerCommand;
import cn.iocoder.yudao.module.pms.customer.service.query.CustomerDetailService;
import cn.iocoder.yudao.module.pms.customer.service.query.CustomerPageCriteria;
import cn.iocoder.yudao.module.pms.customer.service.query.CustomerQueryService;
import cn.iocoder.yudao.module.pms.customer.service.query.CustomerResponseService;
import cn.iocoder.yudao.module.pms.customer.service.security.CustomerContactAccessService;
import cn.iocoder.yudao.module.pms.customer.service.security.CustomerScopeContextService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.module.pms.customer.enums.ErrorCodeConstants.CUSTOMER_NOT_EXISTS;

@RestController("pmsCustomerController")
@RequestMapping("/pms/customers")
@Validated
public class CustomerController {

    @Resource
    private CustomerApplicationService customerApplicationService;
    @Resource
    private CustomerQueryService customerQueryService;
    @Resource
    private CustomerResponseService customerResponseService;
    @Resource
    private CustomerDetailService customerDetailService;
    @Resource
    private CustomerScopeContextService scopeContextService;
    @Resource
    private CustomerContactAccessService contactAccessService;

    @GetMapping
    @PreAuthorize("@ss.hasPermission('pms:customer:query')")
    public CommonResult<PageResult<CustomerRespVO>> page(@Validated CustomerPageReqVO request) {
        Long tenantId = tenantId();
        var scope = scopeContextService.resolve(tenantId, userId());
        var criteria = new CustomerPageCriteria(tenantId, request.getCode(), request.getName(),
                request.getDepartmentCode(), request.getMarketCode(), request.getSystemCode(),
                request.getExpendCode(), request.getIndustryCode(), request.getLifecycleStatus(),
                request.getSourceType(), request);
        var contactAccess = contactAccessService.resolve(userId(), true);
        return success(customerResponseService.page(
                customerQueryService.page(criteria, scope), contactAccess));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('pms:customer:query')")
    public CommonResult<CustomerDetailRespVO> get(@PathVariable("id") Long id) {
        Long tenantId = tenantId();
        CustomerMasterDO customer = customerQueryService.get(
                tenantId, id, scopeContextService.resolve(tenantId, userId()));
        if (customer == null) {
            throw exception(CUSTOMER_NOT_EXISTS);
        }
        var contactAccess = contactAccessService.resolve(userId(), true);
        return success(customerDetailService.get(customer, contactAccess));
    }

    @PostMapping
    @PreAuthorize("@ss.hasPermission('pms:customer:create')")
    public CommonResult<CustomerCommandResult> create(@Valid @RequestBody CustomerCreateReqVO request,
                                                       @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return success(customerApplicationService.create(new CreateCustomerCommand(tenantId(), request.getCode(),
                request.getName(), request.getShortName(), request.getRemark(), request.getSourceType(),
                request.getSourceKey(), request.getSourceVersion(), request.getTemporaryReason(),
                request.isReconciliationPending(), request.getDepartmentCode(), request.getMarketCode(),
                request.getSystemCode(), request.getExpendCode(), request.getIndustryCode(), idempotencyKey)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('pms:customer:update')")
    public CommonResult<CustomerCommandResult> update(@PathVariable("id") Long id,
                                                       @Valid @RequestBody CustomerUpdateReqVO request,
                                                       @RequestHeader("If-Match") Long expectedVersion,
                                                       @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return success(customerApplicationService.update(new UpdateCustomerCommand(tenantId(), id,
                request.getName(), request.getShortName(), request.getRemark(), request.getDepartmentCode(),
                request.getMarketCode(), request.getSystemCode(), request.getExpendCode(), request.getIndustryCode(),
                request.getChangedFields(), expectedVersion, idempotencyKey)));
    }

    @PostMapping("/{id}/actions/disable")
    @PreAuthorize("@ss.hasPermission('pms:customer:disable')")
    public CommonResult<CustomerCommandResult> disable(@PathVariable("id") Long id,
                                                        @Valid @RequestBody CustomerLifecycleReqVO request,
                                                        @RequestHeader("If-Match") Long expectedVersion,
                                                        @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return success(customerApplicationService.disable(lifecycle(id, request, expectedVersion, idempotencyKey)));
    }

    @PostMapping("/{id}/actions/delete")
    @PreAuthorize("@ss.hasPermission('pms:customer:delete')")
    public CommonResult<CustomerCommandResult> delete(@PathVariable("id") Long id,
                                                       @Valid @RequestBody CustomerLifecycleReqVO request,
                                                       @RequestHeader("If-Match") Long expectedVersion,
                                                       @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return success(customerApplicationService.delete(lifecycle(id, request, expectedVersion, idempotencyKey)));
    }

    @PostMapping("/{id}/actions/restore")
    @PreAuthorize("@ss.hasPermission('pms:customer:restore')")
    public CommonResult<CustomerCommandResult> restore(@PathVariable("id") Long id,
                                                        @Valid @RequestBody CustomerLifecycleReqVO request,
                                                        @RequestHeader("If-Match") Long expectedVersion,
                                                        @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return success(customerApplicationService.restore(lifecycle(id, request, expectedVersion, idempotencyKey)));
    }

    private CustomerLifecycleCommand lifecycle(Long id, CustomerLifecycleReqVO request,
                                               Long expectedVersion, String idempotencyKey) {
        return new CustomerLifecycleCommand(tenantId(), id, request.getReason(), expectedVersion, idempotencyKey);
    }

    private Long tenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId != null) {
            return tenantId;
        }
        var loginUser = SecurityFrameworkUtils.getLoginUser();
        if (loginUser == null || loginUser.getTenantId() == null) {
            return TenantContextHolder.getRequiredTenantId();
        }
        return loginUser.getTenantId();
    }

    private Long userId() {
        return SecurityFrameworkUtils.getLoginUserId();
    }
}
