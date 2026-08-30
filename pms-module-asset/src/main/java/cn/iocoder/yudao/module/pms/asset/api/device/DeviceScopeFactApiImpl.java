package cn.iocoder.yudao.module.pms.asset.api.device;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceScopeResolutionResult;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceScopeResolveQuery;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceScopeRevalidationQuery;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceScopeRevalidationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;

import java.util.Objects;

/** 将完整Spring事务边界转换为AST公共失败语义。 */
@Service
@RequiredArgsConstructor
public class DeviceScopeFactApiImpl implements DeviceScopeFactApi {

    private final DeviceScopeFactTransactionExecutor transactions;

    @Override
    public DeviceScopeResolutionResult resolveBySerials(DeviceScopeResolveQuery query) {
        if (query == null) {
            throw failure(DeviceScopeFactException.Code.INVALID_REQUEST, "query must not be null");
        }
        requireTrustedTenant(query.tenantId());
        try {
            return transactions.resolveBySerials(query);
        } catch (DataAccessException | TransactionException exception) {
            throw unavailable("failed to resolve device scope", exception);
        }
    }

    @Override
    public DeviceScopeRevalidationResult lockAndRevalidate(DeviceScopeRevalidationQuery query) {
        if (query == null) {
            throw failure(DeviceScopeFactException.Code.INVALID_REQUEST, "query must not be null");
        }
        requireTrustedTenant(query.tenantId());
        try {
            return transactions.lockAndRevalidate(query);
        } catch (DataAccessException | TransactionException exception) {
            throw unavailable("failed to lock device scope", exception);
        }
    }

    private static void requireTrustedTenant(Long tenantId) {
        final Long currentTenantId;
        try {
            currentTenantId = TenantContextHolder.getRequiredTenantId();
        } catch (RuntimeException exception) {
            throw new DeviceScopeFactException(DeviceScopeFactException.Code.TENANT_CONTEXT_MISMATCH,
                    "trusted tenant context is missing", exception);
        }
        if (!Objects.equals(currentTenantId, tenantId)) {
            throw failure(DeviceScopeFactException.Code.TENANT_CONTEXT_MISMATCH,
                    "trusted tenant context does not match query tenant");
        }
    }

    private static DeviceScopeFactException unavailable(String message, RuntimeException cause) {
        return new DeviceScopeFactException(DeviceScopeFactException.Code.PROVIDER_UNAVAILABLE, message, cause);
    }

    private static DeviceScopeFactException failure(DeviceScopeFactException.Code code, String message) {
        return new DeviceScopeFactException(code, message);
    }
}
