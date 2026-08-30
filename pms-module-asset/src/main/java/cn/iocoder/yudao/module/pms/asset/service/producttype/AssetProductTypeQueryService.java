package cn.iocoder.yudao.module.pms.asset.service.producttype;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.producttype.dto.AuthorizedDeviceProductTypeQuery;
import cn.iocoder.yudao.module.pms.asset.api.producttype.dto.AuthorizedDeviceProductTypeResult;
import cn.iocoder.yudao.module.pms.asset.api.producttype.dto.ProductTypeCodeResult;
import cn.iocoder.yudao.module.pms.asset.api.producttype.dto.ProductTypeCodesQuery;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.producttype.AssetProductTypeDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.producttype.AssetProductTypeMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.producttype.DeviceCurrentProductTypeMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.producttype.projection.AuthorizedDeviceProductTypeProjection;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.producttype.query.AuthorizedDeviceProductTypesQuery;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.producttype.query.ProductTypesByCodesQuery;
import cn.iocoder.yudao.module.pms.asset.service.producttype.security.AssetProductTypeRequestGuard;
import cn.iocoder.yudao.module.pms.asset.service.security.DeviceAccessScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.asset.enums.ErrorCodeConstants.AST_PRODUCT_TYPE_INVALID_REQUEST;
import static cn.iocoder.yudao.module.pms.asset.service.producttype.security.AssetProductTypeActionCodes.DEVICE_PRODUCT_TYPE_READ;
import static cn.iocoder.yudao.module.pms.asset.service.producttype.security.AssetProductTypeActionCodes.PRODUCT_TYPE_READ_CODES;

@Service
@RequiredArgsConstructor
public class AssetProductTypeQueryService {

    private static final String FRESH = "FRESH";
    private static final String NOT_AVAILABLE = "NOT_AVAILABLE";

    private final AssetProductTypeRequestGuard requestGuard;
    private final DeviceAccessScopeService accessScopeService;
    private final AssetProductTypeMapper productTypeMapper;
    private final DeviceCurrentProductTypeMapper currentProductTypeMapper;

    public List<ProductTypeCodeResult> getByCodes(ProductTypeCodesQuery query) {
        requestGuard.requireTrustedPrincipal(PRODUCT_TYPE_READ_CODES);
        if (query == null) {
            throw exception(AST_PRODUCT_TYPE_INVALID_REQUEST);
        }
        List<String> requestedCodes = query.productTypeCodes();
        if (requestedCodes.isEmpty()) {
            return List.of();
        }
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        Map<String, AssetProductTypeDO> productTypesByCode = productTypeMapper.selectByCodes(
                        new ProductTypesByCodesQuery(tenantId, Set.copyOf(requestedCodes)))
                .stream()
                .collect(Collectors.toMap(AssetProductTypeDO::getTypeCode, Function.identity()));
        return requestedCodes.stream()
                .map(code -> toCodeResult(code, productTypesByCode.get(code)))
                .toList();
    }

    public List<AuthorizedDeviceProductTypeResult> getAuthorizedDeviceProductType(
            AuthorizedDeviceProductTypeQuery query) {
        requestGuard.requireTrustedPrincipal(DEVICE_PRODUCT_TYPE_READ);
        if (query == null) {
            throw exception(AST_PRODUCT_TYPE_INVALID_REQUEST);
        }
        Long subjectUserId = requestGuard.requireSubjectUser(query.subjectUserId());
        if (query.deviceIds().isEmpty()) {
            return List.of();
        }
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        Set<Long> visibleProjectIds = accessScopeService.visibleProjectIds(tenantId, subjectUserId);
        if (visibleProjectIds.isEmpty()) {
            return List.of();
        }
        LocalDateTime effectiveAt = LocalDateTime.now();
        return currentProductTypeMapper.selectAuthorizedCurrent(
                        new AuthorizedDeviceProductTypesQuery(
                                tenantId,
                                Set.copyOf(query.deviceIds()),
                                visibleProjectIds,
                                effectiveAt))
                .stream()
                .map(this::toAuthorizedDeviceResult)
                .toList();
    }

    private ProductTypeCodeResult toCodeResult(String code, AssetProductTypeDO productType) {
        if (productType == null) {
            return new ProductTypeCodeResult(
                    code, false, false, null, null, null,
                    NOT_AVAILABLE, null, false);
        }
        return new ProductTypeCodeResult(
                code,
                true,
                Boolean.TRUE.equals(productType.getEnabled()),
                productType.getDisplayName(),
                productType.getSourceSystem(),
                productType.getSourceVersion(),
                productType.getSyncStatus(),
                productType.getSyncedAt(),
                isFromLastSuccessfulCopy(productType.getSyncStatus(), productType.getSyncedAt()));
    }

    private AuthorizedDeviceProductTypeResult toAuthorizedDeviceResult(
            AuthorizedDeviceProductTypeProjection projection) {
        String syncStatus = projection.syncStatus() == null
                ? NOT_AVAILABLE : projection.syncStatus();
        return new AuthorizedDeviceProductTypeResult(
                projection.deviceId(),
                projection.productTypeCode(),
                projection.displayName(),
                Boolean.TRUE.equals(projection.enabled()),
                projection.sourceVersion(),
                projection.resolutionStatus(),
                syncStatus,
                projection.lastSuccessfulSyncTime(),
                isFromLastSuccessfulCopy(syncStatus, projection.lastSuccessfulSyncTime()));
    }

    private boolean isFromLastSuccessfulCopy(String syncStatus, LocalDateTime syncedAt) {
        return syncedAt != null && !FRESH.equals(syncStatus);
    }
}
