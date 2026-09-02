package cn.iocoder.yudao.module.pms.commerce.service.scope;

import cn.iocoder.yudao.module.pms.commerce.api.scope.DeliveryScopeFactException;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeProjectVersionDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.DeliveryScopeProjectVersionMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query.DeliveryScopeProjectVersionAdvance;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query.DeliveryScopeProjectVersionQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query.DeliveryScopeProjectVersionSeed;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class DeliveryScopeProjectVersionService {
    private final DeliveryScopeProjectVersionMapper mapper;

    public long current(Long tenantId, Long projectId) {
        DeliveryScopeProjectVersionDO row = mapper.selectCurrent(
                new DeliveryScopeProjectVersionQuery(tenantId, projectId));
        return row == null ? 0L : row.getScopeVersion();
    }

    public DeliveryScopeProjectVersionDO lock(Long tenantId, Long projectId, String actor,
                                              LocalDateTime now) {
        mapper.insertIfAbsent(new DeliveryScopeProjectVersionSeed(
                IdWorker.getId(), tenantId, projectId, actor, now));
        DeliveryScopeProjectVersionDO row = mapper.selectForUpdate(
                new DeliveryScopeProjectVersionQuery(tenantId, projectId));
        if (row == null) {
            throw corrupted("project scope version row is unavailable");
        }
        return row;
    }

    public DeliveryScopeProjectVersionDO lockExisting(Long tenantId, Long projectId) {
        return mapper.selectForUpdate(new DeliveryScopeProjectVersionQuery(tenantId, projectId));
    }

    public long advance(DeliveryScopeProjectVersionDO row, String changeType,
                        String actor, LocalDateTime now) {
        long next = row.getScopeVersion() + 1;
        int nextPayload = row.getPayloadVersion() + 1;
        int affected = mapper.advance(new DeliveryScopeProjectVersionAdvance(
                row.getTenantId(), row.getProjectId(), row.getScopeVersion(), row.getVersion(),
                next, nextPayload, changeType, actor, now));
        if (affected != 1) {
            throw new DeliveryScopeFactException(DeliveryScopeFactException.Code.SCOPE_STALE,
                    "project scope version changed concurrently");
        }
        return next;
    }

    private DeliveryScopeFactException corrupted(String message) {
        return new DeliveryScopeFactException(DeliveryScopeFactException.Code.OWNER_DATA_CORRUPTED, message);
    }
}
