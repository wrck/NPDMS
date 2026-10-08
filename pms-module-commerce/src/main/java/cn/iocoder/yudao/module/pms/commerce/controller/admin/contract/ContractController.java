package cn.iocoder.yudao.module.pms.commerce.controller.admin.contract;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.pms.commerce.controller.admin.contract.vo.ContractRelationReqVO;
import cn.iocoder.yudao.module.pms.commerce.controller.admin.contract.vo.ContractDetailRespVO;
import cn.iocoder.yudao.module.pms.commerce.controller.admin.contract.vo.ContractRespVO;
import cn.iocoder.yudao.module.pms.commerce.controller.admin.order.vo.SalesOrderRespVO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.contract.ContractDO;
import cn.iocoder.yudao.module.pms.commerce.service.contract.ContractAccessService;
import cn.iocoder.yudao.module.pms.commerce.service.contract.ContractRelationCommand;
import cn.iocoder.yudao.module.pms.commerce.service.contract.ContractRelationCommandService;
import cn.iocoder.yudao.module.pms.commerce.service.contract.ContractRelationResult;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
@RequestMapping("/api/v1/pms/contracts")
@Validated
@RequiredArgsConstructor
public class ContractController {

    private final ContractAccessService accessService;
    private final ContractRelationCommandService relationService;
    private final PermissionApi permissionApi;
    private final Environment environment;

    @GetMapping
    @PreAuthorize("@ss.hasPermission('pms:commerce:contract:query')")
    public CommonResult<PageResult<ContractRespVO>> page(
            @RequestParam(required = false) String companyCode,
            @RequestParam(required = false) String contractNo,
            @RequestParam(required = false) String contractType,
            @RequestParam(required = false) String customer,
            @RequestParam(required = false) String sourceSystem,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") @Min(1) int pageNo,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int pageSize) {
        return withTenant(() -> {
            Long userId = currentUserId();
            boolean sensitiveReadable = canReadSensitive(userId);
            return success(toPage(accessService.pageContracts(currentTenantId(), userId,
                    UUID.randomUUID().toString(), new ContractAccessService.ContractSearch(
                            companyCode, contractNo, contractType, customer, sourceSystem, status,
                            (pageNo - 1) * pageSize, pageSize)),
                    sensitiveReadable));
        });
    }

    @GetMapping("/project/{projectId}/overview")
    @PreAuthorize("@ss.hasPermission('pms:commerce:contract:query')")
    public CommonResult<cn.iocoder.yudao.module.pms.commerce.controller.admin.contract.vo.ProjectCommerceRespVO> projectOverview(@PathVariable Long projectId) {
        return withTenant(() -> {
            Long userId = currentUserId();
            boolean sensitive = canReadSensitive(userId);
            var detail = accessService.getProjectCommerceDetail(currentTenantId(), userId, projectId);
            return success(new cn.iocoder.yudao.module.pms.commerce.controller.admin.contract.vo.ProjectCommerceRespVO(
                detail.contracts().stream().map(c -> toResponse(c, sensitive)).toList(),
                detail.orders().stream().map(o -> new cn.iocoder.yudao.module.pms.commerce.controller.admin.contract.vo.ProjectCommerceRespVO.Order(
                    o.getId(), o.getOrderNo(), o.getCompanyName(), o.getOrderCreateTime(),
                    sensitive ? o.getOrderAmount() : null, sensitive ? o.getCurrencyCode() : null,
                    o.getSalesType(), o.getCustomerRequiredTime(), o.getExecutionNo(),
                    sensitive ? o.getCustomerCode() : null, sensitive ? o.getCustomerName() : null)).toList(),
                detail.executionOrders().stream().map(e -> new cn.iocoder.yudao.module.pms.commerce.controller.admin.contract.vo.ProjectCommerceRespVO.ExecutionOrder(
                    e.getId(), e.getExecutionNo(), e.getSalesRepCode(), e.getSalesRepName(),
                    e.getMarketName(), e.getSystemName(), e.getExpendName(), e.getIndustryName(),
                    e.getDepartmentName(), e.getCompanyName(), e.getCustomerProjectName(), sensitive ? e.getFinalCustomerName() : null,
                    sensitive ? e.getAgentName() : null, e.getProjectManagerName(), e.getServiceTypeName(), e.getChannelName(),
                    e.getSubmitTime(), e.getSourceSystem(), e.getSourceSyncTime(),
                    e.getProjectCode(), e.getProjectName(), sensitive ? e.getProjectAmount() : null,
                    e.getMajorProjectLevel(), e.getProjectType(), e.getMarketCode(), e.getSystemCode(),
                    e.getExpendCode(), e.getIndustryCode())).toList()));
        });
    }

    @GetMapping("/{id}/creation-source")
    @PreAuthorize("@ss.hasPermission('pms:commerce:contract:query')")
    public CommonResult<cn.iocoder.yudao.module.pms.commerce.controller.admin.contract.vo.ContractCreationSourceRespVO> creationSource(
            @PathVariable Long id, @RequestParam(required = false) Long salesOrderId) {
        return withTenant(() -> {
            Long userId = currentUserId();
            boolean sensitiveReadable = canReadSensitive(userId);
            var detail = accessService.getCreationSource(currentTenantId(), userId,
                    UUID.randomUUID().toString(), id);
            return success(cn.iocoder.yudao.module.pms.commerce.controller.admin.contract.vo.ContractCreationSourceRespVO.from(
                    detail, sensitiveReadable, salesOrderId));
        });
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('pms:commerce:contract:query')")
    public CommonResult<ContractDetailRespVO> get(@PathVariable Long id) {
        return withTenant(() -> {
            Long userId = currentUserId();
            ContractAccessService.ContractDetail detail = accessService.getContractDetail(
                    currentTenantId(), userId, UUID.randomUUID().toString(), id);
            boolean sensitiveReadable = canReadSensitive(userId);
            return success(new ContractDetailRespVO(toResponse(detail.contract(), sensitiveReadable),
                    detail.relatedOrders().stream().map(value -> toOrder(value, sensitiveReadable)).toList(),
                    detail.projectRelations().stream().map(value ->
                            new ContractDetailRespVO.ProjectRelationRespVO(value.getId(), value.getProjectId(),
                                    value.getRelationRole(), value.getStatus(), value.getVersion())).toList(),
                    detail.contract().getMasterSourceSystem(), detail.contract().getSourceVersion(),
                    detail.contract().getSourceSyncTime(), detail.contract().getSourceUpdatedAt()));
        });
    }

    @PostMapping("/{id}/project-relations")
    @PreAuthorize("@ss.hasPermission('pms:commerce:contract:relate')")
    public CommonResult<ContractRelationResult> relate(
            @PathVariable Long id,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
            @Valid @RequestBody ContractRelationReqVO request) {
        return withTenant(() -> success(relationService.relate(new ContractRelationCommand(
                currentTenantId(), currentUserId(), id, request.projectId(), request.relationRole(),
                idempotencyKey, request.reason()))));
    }

    PageResult<ContractRespVO> toPage(PageResult<ContractDO> page, boolean sensitiveReadable) {
        return new PageResult<>(page.getList().stream()
                .map(value -> toResponse(value, sensitiveReadable)).toList(), page.getTotal());
    }

    ContractRespVO toResponse(ContractDO value, boolean sensitiveReadable) {
        return new ContractRespVO(value.getId(), value.getCompanyCode(), value.getCompanyName(),
                value.getContractNo(), sensitiveReadable ? value.getContractType() : null,
                sensitiveReadable ? value.getCustomerCode() : null,
                sensitiveReadable ? value.getCustomerName() : null,
                value.getContractName(), sensitiveReadable ? value.getContractAmount() : null,
                sensitiveReadable ? value.getCurrencyCode() : null, value.getSourceVersion(),
                value.getSourceUpdatedAt(), value.getStatus(), value.getVersion());
    }

    private SalesOrderRespVO toOrder(
            cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderDO value,
            boolean sensitiveReadable) {
        return new SalesOrderRespVO(value.getId(), value.getSourceSystem(), value.getSourceVersion(),
                value.getCompanyCode(), value.getCompanyName(), value.getOrderType(), value.getOrderNo(),
                sensitiveReadable ? value.getCustomerCode() : null,
                sensitiveReadable ? value.getCustomerName() : null, value.getStatus(), value.getVersion());
    }

    private boolean canReadSensitive(Long userId) {
        return permissionApi.hasAnyPermissions(userId, "pms:commerce:contract:sensitive-read");
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
