package cn.iocoder.yudao.module.pms.platform.service.businessmodel.result;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.result.BusinessResultFormationPort;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.result.BusinessResultPort;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.result.BusinessResultQuery;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.result.BusinessResultRecord;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.result.ResultSemantics;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.event.BusinessEventKind;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.event.BusinessEventPort;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.event.BusinessEventRecord;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityRef;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.result.PlatformBusinessResultDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.result.PlatformBusinessResultMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 统一业务结果登记默认实现：形成依据必须真实（事件身份/执行引用），
 * 同一 (结果类型, 对象, 形成依据) 重复形成幂等返回原结果；
 * 固定结果查不到时不回退最新。
 */
@Service
public class PlatformBusinessResultService implements BusinessResultPort, BusinessResultFormationPort {

    private final PlatformBusinessResultMapper resultMapper;
    private final ResultSubscriptionService resultSubscriptionService;
    private final BusinessEventPort eventPort;

    public PlatformBusinessResultService(PlatformBusinessResultMapper resultMapper,
                                         ResultSubscriptionService resultSubscriptionService,
                                         BusinessEventPort eventPort) {
        this.resultMapper = resultMapper;
        this.resultSubscriptionService = resultSubscriptionService;
        this.eventPort = eventPort;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public BusinessResultRecord form(String resultType, EntityRef objectRef, ResultSemantics semantics,
                                     String formationBasis, String formedByBackend) {
        if (resultType == null || resultType.isBlank() || formationBasis == null || formationBasis.isBlank()) {
            throw new BusinessContractException("CONTRACT_REJECTED", "结果类型与形成依据不能为空");
        }
        Optional<PlatformBusinessResultDO> existing = resultMapper
                .selectByBasis(resultType, objectRef.ownerModule(), objectRef.entityType(),
                        objectRef.entityId(), formationBasis);
        if (existing.isPresent()) {
            return toRecord(existing.get());
        }
        PlatformBusinessResultDO row = new PlatformBusinessResultDO();
        row.setResultType(resultType);
        row.setOwnerModule(objectRef.ownerModule());
        row.setEntityType(objectRef.entityType());
        row.setEntityId(objectRef.entityId());
        row.setResultId(UUID.randomUUID().toString());
        row.setSemantics(semantics == null ? ResultSemantics.NEW_RESULT.name() : semantics.name());
        row.setFormationBasis(formationBasis);
        row.setFormedByBackend(formedByBackend);
        row.setFormedAt(LocalDateTime.now());
        row.setValid(true);
        row.setTenantId(objectRef.tenantId());
        try {
            resultMapper.insert(row);
        } catch (DuplicateKeyException ex) {
            // 并发重复形成：同一依据只保留一个结果，幂等返回已登记记录。
            return toRecord(resultMapper
                    .selectByBasis(resultType, objectRef.ownerModule(), objectRef.entityType(),
                            objectRef.entityId(), formationBasis)
                    .orElseThrow(() -> ex));
        }
        appendResultEvent(row, BusinessEventKind.FORMED);
        return toRecord(row);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void invalidate(String resultType, EntityRef objectRef, String basis) {
        PlatformBusinessResultDO row = resultMapper
                .selectByBasis(resultType, objectRef.ownerModule(), objectRef.entityType(),
                        objectRef.entityId(), basis)
                .orElseThrow(() -> new BusinessContractException("RESULT_NOT_FOUND",
                        "未找到可失效的结果: " + resultType + " " + basis));
        row.setValid(false);
        resultMapper.updateById(row);
        // 结果失效不回滚历史判断：按采纳引用记录证据历史影响，不覆盖原结论、不自动重开。
        resultSubscriptionService.recordInvalidationImpact(row.getResultId(),
                "结果失效: " + resultType + " " + basis);
        appendResultEvent(row, BusinessEventKind.INVALIDATED);
    }

    /** 结果形成/失效事件与登记同事务追加；载荷携带结果身份与形成序号（订阅补扫的轮次上界）。 */
    private void appendResultEvent(PlatformBusinessResultDO row, BusinessEventKind kind) {
        if (eventPort == null) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("resultType", row.getResultType());
        payload.put("resultId", row.getResultId());
        payload.put("resultSequence", row.getId());
        eventPort.append(new BusinessEventRecord("result-" + kind.name().toLowerCase(java.util.Locale.ROOT)
                        + "-" + row.getResultId(),
                new EntityRef(row.getTenantId(), row.getOwnerModule(), row.getEntityType(), row.getEntityId()),
                kind, String.valueOf(row.getId()), row.getFormationBasis(), payload, 0L));
    }

    @Override
    public BusinessResultRecord current(BusinessResultQuery query) {
        if (query.resultId() != null && !query.resultId().isBlank()) {
            return resultMapper.selectInventory(query.resultType(), ref(query).ownerModule(), ref(query).entityType(),
                            null, query.resultId(), true).stream()
                    .findFirst().map(PlatformBusinessResultService::toRecord).orElse(null);
        }
        if (query.objectRef() == null) {
            throw new BusinessContractException("CONTRACT_REJECTED", "当前结果查询必须指定对象或结果身份");
        }
        // 仅返回有效结果；固定结果查不到时不回退最新。
        return resultMapper.selectCurrentValid(query.resultType(), query.objectRef().ownerModule(),
                        query.objectRef().entityType(), query.objectRef().entityId())
                .map(PlatformBusinessResultService::toRecord).orElse(null);
    }

    @Override
    public List<BusinessResultRecord> inventory(BusinessResultQuery query) {
        EntityRef objectRef = query.objectRef();
        return resultMapper.selectInventory(query.resultType(),
                        objectRef == null ? null : objectRef.ownerModule(),
                        objectRef == null ? null : objectRef.entityType(),
                        objectRef == null ? null : objectRef.entityId(),
                        query.resultId(), query.onlyValid())
                .stream().map(PlatformBusinessResultService::toRecord).toList();
    }

    private static EntityRef ref(BusinessResultQuery query) {
        if (query.objectRef() == null) {
            throw new BusinessContractException("CONTRACT_REJECTED", "按结果身份查询仍需对象引用以确定租户");
        }
        return query.objectRef();
    }

    static BusinessResultRecord toRecord(PlatformBusinessResultDO row) {
        return new BusinessResultRecord(row.getResultType(),
                new EntityRef(row.getTenantId(), row.getOwnerModule(), row.getEntityType(), row.getEntityId()),
                row.getResultId(), ResultSemantics.valueOf(row.getSemantics()), row.getFormationBasis(),
                row.getFormedAt(), Boolean.TRUE.equals(row.getValid()), null, row.getId());
    }
}
