package cn.iocoder.yudao.module.pms.commerce.controller.admin.contract;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.pms.commerce.controller.admin.contract.vo.ContractRelationReqVO;
import cn.iocoder.yudao.module.pms.commerce.controller.admin.contract.vo.ContractRespVO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.contract.ContractDO;
import cn.iocoder.yudao.module.pms.commerce.service.contract.ContractAccessService;
import cn.iocoder.yudao.module.pms.commerce.service.contract.ContractRelationCommand;
import cn.iocoder.yudao.module.pms.commerce.service.contract.ContractRelationCommandService;
import cn.iocoder.yudao.module.pms.commerce.service.contract.ContractRelationResult;
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
    private final Environment environment;

    @GetMapping
    @PreAuthorize("@ss.hasPermission('pms:commerce:contract:query')")
    public CommonResult<PageResult<ContractRespVO>> page(
            @RequestParam(required = false) String contractNo,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") @Min(1) int pageNo,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int pageSize) {
        return withTenant(() -> success(toPage(accessService.pageContracts(currentTenantId(), currentUserId(),
                UUID.randomUUID().toString(), contractNo, status, (pageNo - 1) * pageSize, pageSize))));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('pms:commerce:contract:query')")
    public CommonResult<ContractRespVO> get(@PathVariable Long id) {
        return withTenant(() -> success(toResponse(accessService.getContract(currentTenantId(), currentUserId(),
                UUID.randomUUID().toString(), id))));
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

    private PageResult<ContractRespVO> toPage(PageResult<ContractDO> page) {
        return new PageResult<>(page.getList().stream().map(this::toResponse).toList(), page.getTotal());
    }

    private ContractRespVO toResponse(ContractDO value) {
        return new ContractRespVO(value.getId(), value.getCompanyCode(), value.getCompanyName(),
                value.getContractNo(), value.getContractType(), value.getCustomerCode(), value.getCustomerName(),
                value.getContractName(), value.getCurrencyCode(), value.getSourceVersion(),
                value.getSourceUpdatedAt(), value.getStatus(), value.getVersion());
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
