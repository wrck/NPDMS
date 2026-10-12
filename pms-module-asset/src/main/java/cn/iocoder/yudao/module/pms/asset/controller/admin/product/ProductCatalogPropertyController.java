package cn.iocoder.yudao.module.pms.asset.controller.admin.product;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.migration.ProductCatalogMigrationApi;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/pms/product-catalog-properties")
public class ProductCatalogPropertyController {
    private final ProductCatalogMigrationApi products;
    public record DefinitionRequest(Long existingRevisionId) {}
    @PostMapping("/dppms-definition")
    @PreAuthorize("@ss.hasPermission('pms:integration:configure') && @ss.hasPermission('pms:dynamic-form-template:manage')")
    public CommonResult<Long> definition(@RequestBody DefinitionRequest request) {
        return success(products.configureDefinition(TenantContextHolder.getRequiredTenantId(),SecurityFrameworkUtils.getLoginUserId(),
                request.existingRevisionId(),"product-catalog-definition"));
    }
}
