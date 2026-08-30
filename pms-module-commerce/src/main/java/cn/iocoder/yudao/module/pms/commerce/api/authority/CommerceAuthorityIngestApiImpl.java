package cn.iocoder.yudao.module.pms.commerce.api.authority;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.api.authority.dto.CommerceAuthorityBatchCommand;
import cn.iocoder.yudao.module.pms.commerce.api.authority.dto.CommerceAuthorityBatchResult;
import cn.iocoder.yudao.module.pms.commerce.service.authority.CommerceAuthorityIngestService;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;

import java.util.Objects;

/** COM权威副本接收公共边界；事务异常在代理外转换为稳定公共失败。 */
@Service
public class CommerceAuthorityIngestApiImpl implements CommerceAuthorityIngestApi {

    private final CommerceAuthorityIngestService ingestService;

    public CommerceAuthorityIngestApiImpl(CommerceAuthorityIngestService ingestService) {
        this.ingestService = ingestService;
    }

    @Override
    public CommerceAuthorityBatchResult ingestBatch(CommerceAuthorityBatchCommand command) {
        if (command == null) {
            throw new CommerceAuthorityIngestException(
                    CommerceAuthorityIngestException.Code.INVALID_REQUEST, "command不能为空");
        }
        Long trustedTenantId;
        try {
            trustedTenantId = TenantContextHolder.getRequiredTenantId();
        } catch (RuntimeException ex) {
            throw new CommerceAuthorityIngestException(
                    CommerceAuthorityIngestException.Code.TENANT_CONTEXT_MISMATCH,
                    "缺少受信租户上下文", ex);
        }
        if (!Objects.equals(trustedTenantId, command.tenantId())) {
            throw new CommerceAuthorityIngestException(
                    CommerceAuthorityIngestException.Code.TENANT_CONTEXT_MISMATCH,
                    "受信租户与批次tenantId不一致");
        }
        try {
            return ingestService.ingest(command);
        } catch (CommerceAuthorityIngestException ex) {
            throw ex;
        } catch (DataAccessException | TransactionException ex) {
            throw new CommerceAuthorityIngestException(
                    CommerceAuthorityIngestException.Code.PROVIDER_UNAVAILABLE,
                    "COM权威副本Provider暂不可用", ex);
        }
    }
}
