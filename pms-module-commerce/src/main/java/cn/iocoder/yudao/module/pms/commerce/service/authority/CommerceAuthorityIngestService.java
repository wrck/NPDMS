package cn.iocoder.yudao.module.pms.commerce.service.authority;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.commerce.api.authority.CommerceAuthorityIngestException;
import cn.iocoder.yudao.module.pms.commerce.api.authority.dto.*;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.authority.ContractDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.authority.SalesOrderContractRelationDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.authority.SalesOrderDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.OrderLineDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDetailDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeProjectVersionDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.authority.*;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.authority.query.AuthorityProjectVersionQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.authority.query.AuthorityRelationQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.authority.query.AuthorityScopeDetailsQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.authority.query.AuthorityScopeImpactQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.authority.query.AuthorityScopeReleaseUpdate;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.authority.query.AuthoritySourceQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.authority.query.ContractAuthorityUpdate;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.authority.query.OrderLineAuthorityUpdate;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.authority.query.SalesOrderAuthorityUpdate;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;

import static cn.iocoder.yudao.module.pms.commerce.api.authority.CommerceAuthorityIngestException.Code.*;

/** INT-01批次进入COM后的本地Owner副本事务。 */
@Service
public class CommerceAuthorityIngestService {

    private static final String SCOPE_CODE = "COM:AUTHORITY:INGEST";
    private static final String CONFIRMED = "CONFIRMED";
    private static final String PENDING_AUTHORITY = "PENDING_AUTHORITY";

    private final PlatformCommandExecutionApi commandExecutionApi;
    private final AuthorityPayloadCanonicalizer canonicalizer;
    private final ContractAuthorityMapper contractMapper;
    private final SalesOrderAuthorityMapper salesOrderMapper;
    private final OrderLineAuthorityMapper orderLineMapper;
    private final OrderContractRelationAuthorityMapper relationMapper;
    private final AuthorityScopeImpactMapper scopeImpactMapper;
    private final Clock clock;

    @Autowired
    public CommerceAuthorityIngestService(PlatformCommandExecutionApi commandExecutionApi,
                                          AuthorityPayloadCanonicalizer canonicalizer,
                                          ContractAuthorityMapper contractMapper,
                                          SalesOrderAuthorityMapper salesOrderMapper,
                                          OrderLineAuthorityMapper orderLineMapper,
                                          OrderContractRelationAuthorityMapper relationMapper,
                                          AuthorityScopeImpactMapper scopeImpactMapper) {
        this(commandExecutionApi, canonicalizer, contractMapper, salesOrderMapper, orderLineMapper,
                relationMapper, scopeImpactMapper, Clock.systemDefaultZone());
    }

    CommerceAuthorityIngestService(PlatformCommandExecutionApi commandExecutionApi,
                                   AuthorityPayloadCanonicalizer canonicalizer,
                                   ContractAuthorityMapper contractMapper,
                                   SalesOrderAuthorityMapper salesOrderMapper,
                                   OrderLineAuthorityMapper orderLineMapper,
                                   OrderContractRelationAuthorityMapper relationMapper,
                                   AuthorityScopeImpactMapper scopeImpactMapper,
                                   Clock clock) {
        this.commandExecutionApi = commandExecutionApi;
        this.canonicalizer = canonicalizer;
        this.contractMapper = contractMapper;
        this.salesOrderMapper = salesOrderMapper;
        this.orderLineMapper = orderLineMapper;
        this.relationMapper = relationMapper;
        this.scopeImpactMapper = scopeImpactMapper;
        this.clock = clock;
    }

    @Transactional(rollbackFor = Exception.class)
    public CommerceAuthorityBatchResult ingest(CommerceAuthorityBatchCommand command) {
        PlatformCommandExecutionApi.ExecutionResult<CommerceAuthorityBatchResult> execution =
                commandExecutionApi.execute(
                        new PlatformCommandExecutionApi.IdempotencyScope(
                                command.tenantId(), SCOPE_CODE, 0L, command.eventId()),
                        canonicalizer.batchDigest(command), CommerceAuthorityBatchResult.class,
                        () -> applyBatch(command),
                        result -> successFacts(command, result));
        return switch (execution.decision()) {
            case NEW -> execution.response();
            case REPLAY_COMPLETED -> new CommerceAuthorityBatchResult(
                    command.eventId(), command.batchId(), CommerceAuthorityBatchResult.Decision.EVENT_REPLAYED);
            case CONFLICT -> throw failure(EVENT_PAYLOAD_CONFLICT, "eventId已被不同批次载荷使用");
            case IN_PROGRESS -> throw failure(PROVIDER_UNAVAILABLE, "批次正在处理中，请使用同一eventId重试");
        };
    }

    private CommerceAuthorityBatchResult applyBatch(CommerceAuthorityBatchCommand command) {
        BatchChanges changes = new BatchChanges();
        for (CommerceContractFact fact : command.contracts()) {
            applyContract(command, fact, changes);
        }
        for (CommerceSalesOrderFact fact : command.salesOrders()) {
            applySalesOrder(command, fact, changes);
        }
        for (CommerceOrderLineFact fact : command.orderLines()) {
            applyOrderLine(command, fact, changes);
        }
        for (CommerceOrderContractRelationFact fact : command.orderContractRelations()) {
            applyRelation(command, fact, changes);
        }
        CommerceAuthorityBatchResult.Decision decision = changes.changed
                ? CommerceAuthorityBatchResult.Decision.ACCEPTED
                : CommerceAuthorityBatchResult.Decision.ACCEPTED_NO_CHANGE;
        return new CommerceAuthorityBatchResult(command.eventId(), command.batchId(), decision);
    }

    private void applyContract(CommerceAuthorityBatchCommand command, CommerceContractFact fact,
                               BatchChanges changes) {
        AuthoritySourceQuery query = sourceQuery(command, fact.sourceKey());
        ContractDO current = contractMapper.selectBySourceForUpdate(query);
        if (current == null) {
            requireCreate(fact.expectedPreviousSourceVersion(), "contract", fact.sourceKey());
            ContractDO row = base(new ContractDO(), command.tenantId());
            copyContract(row, command, fact);
            row.setVersion(0);
            requireWrite(contractMapper.insert(row), "合同Owner创建失败");
            changes.changed = true;
            return;
        }
        if (replayOrRequirePredecessor(current.getSourceVersion(), fact.sourceVersion(),
                fact.expectedPreviousSourceVersion(),
                canonicalizer.contractPayload(current), canonicalizer.contractPayload(fact),
                "contract", fact.sourceKey())) {
            return;
        }
        Integer expectedVersion = current.getVersion();
        copyContract(current, command, fact);
        touch(current);
        requireWrite(contractMapper.updateOwnerByVersion(
                new ContractAuthorityUpdate(command.tenantId(), current, expectedVersion)), "合同Owner更新失败");
        changes.changed = true;
    }

    private void applySalesOrder(CommerceAuthorityBatchCommand command, CommerceSalesOrderFact fact,
                                 BatchChanges changes) {
        SalesOrderDO current = salesOrderMapper.selectBySourceForUpdate(sourceQuery(command, fact.sourceKey()));
        if (current == null) {
            requireCreate(fact.expectedPreviousSourceVersion(), "salesOrder", fact.sourceKey());
            SalesOrderDO row = base(new SalesOrderDO(), command.tenantId());
            copySalesOrder(row, command, fact);
            row.setVersion(0);
            requireWrite(salesOrderMapper.insert(row), "销售订单Owner创建失败");
            changes.changed = true;
            return;
        }
        if (replayOrRequirePredecessor(current.getSourceVersion(), fact.sourceVersion(),
                fact.expectedPreviousSourceVersion(), canonicalizer.orderPayload(current),
                canonicalizer.orderPayload(fact), "salesOrder", fact.sourceKey())) {
            return;
        }
        Integer expectedVersion = current.getVersion();
        copySalesOrder(current, command, fact);
        touch(current);
        requireWrite(salesOrderMapper.updateOwnerByVersion(
                new SalesOrderAuthorityUpdate(command.tenantId(), current, expectedVersion)), "销售订单Owner更新失败");
        changes.changed = true;
    }

    private void applyOrderLine(CommerceAuthorityBatchCommand command, CommerceOrderLineFact fact,
                                BatchChanges changes) {
        SalesOrderDO order = salesOrderMapper.selectBySourceForUpdate(
                sourceQuery(command, fact.salesOrderSourceKey()));
        if (order == null) {
            throw failure(OWNER_DATA_CORRUPTED, "订单行引用的销售订单Owner不存在");
        }
        OrderLineDO current = orderLineMapper.selectBySourceForUpdate(sourceQuery(command, fact.sourceKey()));
        if (current == null) {
            requireCreate(fact.expectedPreviousSourceVersion(), "orderLine", fact.sourceKey());
            OrderLineDO row = base(new OrderLineDO(), command.tenantId());
            copyOrderLine(row, command, fact, order.getId());
            row.setVersion(0);
            requireWrite(orderLineMapper.insert(row), "订单行Owner创建失败");
            changes.changed = true;
            return;
        }
        String currentPayload = Objects.equals(current.getOrderId(), order.getId())
                ? canonicalizer.linePayload(current, fact.salesOrderSourceKey())
                : "OWNER_PARENT_MISMATCH";
        if (replayOrRequirePredecessor(current.getSourceVersion(), fact.sourceVersion(),
                fact.expectedPreviousSourceVersion(), currentPayload, canonicalizer.linePayload(fact),
                "orderLine", fact.sourceKey())) {
            return;
        }
        Integer expectedVersion = current.getVersion();
        boolean freezeScopes = requiresScopeConflict(current, fact);
        copyOrderLine(current, command, fact, order.getId());
        touch(current);
        requireWrite(orderLineMapper.updateOwnerByVersion(
                new OrderLineAuthorityUpdate(command.tenantId(), current, expectedVersion)), "订单行Owner更新失败");
        if (freezeScopes) {
            freezeAffectedScopes(command, current.getId(), fact);
        }
        changes.changed = true;
    }

    private void applyRelation(CommerceAuthorityBatchCommand command,
                               CommerceOrderContractRelationFact fact, BatchChanges changes) {
        SalesOrderDO order = salesOrderMapper.selectBySourceForUpdate(
                sourceQuery(command, fact.salesOrderSourceKey()));
        ContractDO contract = contractMapper.selectBySourceForUpdate(
                sourceQuery(command, fact.contractSourceKey()));
        if (order == null || contract == null) {
            throw failure(OWNER_DATA_CORRUPTED, "订单合同关系引用的Owner不存在");
        }
        SalesOrderContractRelationDO current = relationMapper.selectBySourcePairForUpdate(
                new AuthorityRelationQuery(command.tenantId(), command.sourceSystem(),
                        fact.salesOrderSourceKey(), fact.contractSourceKey()));
        if (current == null) {
            requireCreate(fact.expectedPreviousSourceVersion(), "orderContractRelation",
                    fact.salesOrderSourceKey() + "/" + fact.contractSourceKey());
            SalesOrderContractRelationDO row = base(new SalesOrderContractRelationDO(), command.tenantId());
            copyRelation(row, command, fact, order.getId(), contract.getId());
            requireWrite(relationMapper.insert(row), "订单合同关系Owner创建失败");
            changes.changed = true;
            return;
        }
        String currentPayload = Objects.equals(current.getSalesOrderId(), order.getId())
                && Objects.equals(current.getContractId(), contract.getId())
                ? canonicalizer.relationPayload(current) : "OWNER_PARENT_MISMATCH";
        if (replayOrRequirePredecessor(current.getSourceVersion(), fact.sourceVersion(),
                fact.expectedPreviousSourceVersion(), currentPayload, canonicalizer.relationPayload(fact),
                "orderContractRelation", fact.salesOrderSourceKey() + "/" + fact.contractSourceKey())) {
            return;
        }
        copyRelation(current, command, fact, order.getId(), contract.getId());
        touch(current);
        requireWrite(relationMapper.updateById(current), "订单合同关系Owner更新失败");
        changes.changed = true;
    }

    private boolean replayOrRequirePredecessor(String currentVersion, String incomingVersion,
                                               String expectedPrevious, String currentPayload,
                                               String incomingPayload, String objectType, String key) {
        if (Objects.equals(currentVersion, incomingVersion)) {
            if (Objects.equals(currentPayload, incomingPayload)) {
                return true;
            }
            throw failure(SOURCE_VERSION_PAYLOAD_CONFLICT,
                    objectType + "同一sourceVersion载荷冲突: " + key);
        }
        if (!Objects.equals(currentVersion, expectedPrevious)) {
            throw failure(SOURCE_VERSION_CONFLICT, objectType + "前驱sourceVersion不匹配: " + key);
        }
        return false;
    }

    private void requireCreate(String expectedPrevious, String objectType, String key) {
        if (expectedPrevious != null) {
            throw failure(SOURCE_VERSION_CONFLICT, objectType + "不存在但携带前驱sourceVersion: " + key);
        }
    }

    private void copyContract(ContractDO row, CommerceAuthorityBatchCommand command,
                              CommerceContractFact fact) {
        row.setCompanyCode(fact.companyCode());
        row.setContractNo(fact.contractNo());
        row.setCustomerCode(fact.customerCode());
        row.setCustomerName(fact.customerName());
        row.setContractAmount(fact.amount());
        row.setCurrencyCode(fact.currencyCode());
        row.setAuthorityStatus(CONFIRMED);
        source(row, command, fact.sourceKey(), fact.sourceVersion(), fact.lifecycleStatus(), fact.sourceUpdatedAt());
    }

    private void copySalesOrder(SalesOrderDO row, CommerceAuthorityBatchCommand command,
                                CommerceSalesOrderFact fact) {
        row.setCompanyCode(fact.companyCode());
        row.setOrderNo(fact.orderNo());
        row.setOrderType(fact.orderType());
        row.setCustomerCode(fact.customerCode());
        row.setCustomerName(fact.customerName());
        row.setOrderAmount(fact.amount());
        row.setCurrencyCode(fact.currencyCode());
        row.setAuthorityStatus(CONFIRMED);
        source(row, command, fact.sourceKey(), fact.sourceVersion(), fact.lifecycleStatus(), fact.sourceUpdatedAt());
    }

    private void copyOrderLine(OrderLineDO row, CommerceAuthorityBatchCommand command,
                               CommerceOrderLineFact fact, Long orderId) {
        row.setSourceSystem(command.sourceSystem());
        row.setSourceKey(fact.sourceKey());
        row.setSourceVersion(fact.sourceVersion());
        row.setOrderId(orderId);
        row.setLineCode(fact.lineCode());
        row.setItemCode(fact.itemCode());
        row.setModelCode(fact.modelCode());
        row.setQuantity(fact.quantity());
        row.setUnitCode(fact.unitCode());
        boolean qualified = fact.quantity() != null && fact.quantity().signum() > 0
                && fact.unitCode() != null && (fact.itemCode() != null || fact.modelCode() != null);
        row.setQuantityStatus(qualified ? CONFIRMED : PENDING_AUTHORITY);
        row.setSourceLifecycleStatus(fact.lifecycleStatus().name());
        row.setSourceUpdatedAt(fact.sourceUpdatedAt());
        row.setSyncedAt(LocalDateTime.now(clock));
    }

    private void copyRelation(SalesOrderContractRelationDO row, CommerceAuthorityBatchCommand command,
                              CommerceOrderContractRelationFact fact, Long orderId, Long contractId) {
        row.setSalesOrderId(orderId);
        row.setContractId(contractId);
        row.setRelationStatus(fact.effectiveTo() == null ? "ACTIVE" : "ENDED");
        row.setSourceSystem(command.sourceSystem());
        row.setSalesOrderSourceKey(fact.salesOrderSourceKey());
        row.setContractSourceKey(fact.contractSourceKey());
        row.setSourceVersion(fact.sourceVersion());
        row.setSourceEvidence(canonicalizer.relationPayload(fact));
        row.setEffectiveFrom(fact.effectiveFrom());
        row.setEffectiveTo(fact.effectiveTo());
    }

    private boolean requiresScopeConflict(OrderLineDO current, CommerceOrderLineFact incoming) {
        if (incoming.lifecycleStatus() != CommerceSourceLifecycleStatus.ACTIVE) {
            return true;
        }
        if (incoming.quantity() == null) {
            return true;
        }
        return current.getQuantity() != null && incoming.quantity().compareTo(current.getQuantity()) < 0;
    }

    private void freezeAffectedScopes(CommerceAuthorityBatchCommand command, Long orderLineId,
                                      CommerceOrderLineFact incoming) {
        List<DeliveryScopeDO> activeScopes = scopeImpactMapper.selectActiveScopesForUpdate(
                new AuthorityScopeImpactQuery(command.tenantId(), orderLineId));
        if (activeScopes.isEmpty()) {
            return;
        }
        BigDecimal allocated = activeScopes.stream().map(DeliveryScopeDO::getAllocatedQty)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (incoming.lifecycleStatus() == CommerceSourceLifecycleStatus.ACTIVE
                && incoming.quantity() != null && incoming.quantity().compareTo(allocated) >= 0) {
            return;
        }
        List<Long> scopeIds = activeScopes.stream().map(DeliveryScopeDO::getId).toList();
        List<DeliveryScopeDetailDO> details = scopeImpactMapper.selectDetailsForUpdate(
                new AuthorityScopeDetailsQuery(command.tenantId(), scopeIds));
        Map<Long, List<DeliveryScopeDetailDO>> detailsByScope = new LinkedHashMap<>();
        for (DeliveryScopeDetailDO detail : details) {
            detailsByScope.computeIfAbsent(detail.getDeliveryScopeId(), ignored -> new ArrayList<>()).add(detail);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        Set<Long> affectedProjects = new TreeSet<>();
        for (DeliveryScopeDO active : activeScopes) {
            requireWrite(scopeImpactMapper.releaseActiveScopeByVersion(
                    new AuthorityScopeReleaseUpdate(command.tenantId(), active.getId(), active.getVersion(), now, now)),
                    "结束原ACTIVE范围失败");

            DeliveryScopeDO conflict = base(new DeliveryScopeDO(), command.tenantId());
            conflict.setOrderLineId(active.getOrderLineId());
            conflict.setProjectId(active.getProjectId());
            conflict.setAllocatedQty(active.getAllocatedQty());
            conflict.setScopeStatus("CONFLICT");
            conflict.setAllocationVersion(active.getAllocationVersion() + 1);
            conflict.setSourceEvidence(conflictEvidence(command, incoming));
            conflict.setEffectiveFrom(now);
            conflict.setEffectiveTo(null);
            conflict.setVersion(0);
            requireWrite(scopeImpactMapper.insert(conflict), "追加CONFLICT范围失败");

            for (DeliveryScopeDetailDO detail : detailsByScope.getOrDefault(active.getId(), List.of())) {
                DeliveryScopeDetailDO copy = copyDetail(detail, conflict.getId(), command.tenantId());
                requireWrite(scopeImpactMapper.insertScopeDetail(copy), "复制CONFLICT范围明细失败");
            }
            affectedProjects.add(active.getProjectId());
        }
        for (Long projectId : affectedProjects) {
            incrementProjectWatermark(command.tenantId(), projectId, now);
        }
    }

    private DeliveryScopeDetailDO copyDetail(DeliveryScopeDetailDO source, Long scopeId, Long tenantId) {
        DeliveryScopeDetailDO copy = base(new DeliveryScopeDetailDO(), tenantId);
        copy.setId(IdWorker.getId());
        copy.setDeliveryScopeId(scopeId);
        copy.setOfficeDepartmentCode(source.getOfficeDepartmentCode());
        copy.setSerialNo(source.getSerialNo());
        copy.setAllocatedQty(source.getAllocatedQty());
        copy.setUnitCode(source.getUnitCode());
        copy.setProductCode(source.getProductCode());
        copy.setModelCode(source.getModelCode());
        copy.setSiteId(source.getSiteId());
        copy.setSiteLocationId(source.getSiteLocationId());
        copy.setLocationText(source.getLocationText());
        copy.setLocationResolutionStatus(source.getLocationResolutionStatus());
        copy.setDetailStatus(source.getDetailStatus());
        copy.setSourceSnapshot(source.getSourceSnapshot());
        copy.setVersion(0);
        return copy;
    }

    private void incrementProjectWatermark(Long tenantId, Long projectId, LocalDateTime now) {
        AuthorityProjectVersionQuery query = new AuthorityProjectVersionQuery(tenantId, projectId);
        DeliveryScopeProjectVersionDO row = scopeImpactMapper.selectProjectVersionForUpdate(query);
        if (row == null) {
            DeliveryScopeProjectVersionDO created = base(new DeliveryScopeProjectVersionDO(), tenantId);
            created.setId(IdWorker.getId());
            created.setProjectId(projectId);
            created.setScopeVersion(1L);
            created.setPayloadVersion(1);
            created.setLastChangeType("SOURCE_CONFLICT");
            created.setVersion(0);
            requireWrite(scopeImpactMapper.insertProjectVersion(created), "创建项目范围水位失败");
            return;
        }
        row.setScopeVersion(row.getScopeVersion() + 1);
        row.setPayloadVersion(row.getPayloadVersion() + 1);
        row.setLastChangeType("SOURCE_CONFLICT");
        row.setUpdater("0");
        row.setUpdateTime(now);
        requireWrite(scopeImpactMapper.updateProjectVersionById(row), "递增项目范围水位失败");
    }

    private String conflictEvidence(CommerceAuthorityBatchCommand command, CommerceOrderLineFact fact) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("reason", "SOURCE_AUTHORITY_CHANGED");
        value.put("sourceSystem", command.sourceSystem());
        value.put("sourceKey", fact.sourceKey());
        value.put("sourceVersion", fact.sourceVersion());
        String json = JsonUtils.toJsonString(value);
        if (json.length() > 255) {
            throw failure(OWNER_DATA_CORRUPTED, "范围冲突来源证据超过物理上限");
        }
        return json;
    }

    private void source(ContractDO row, CommerceAuthorityBatchCommand command, String sourceKey,
                        String sourceVersion, CommerceSourceLifecycleStatus lifecycleStatus,
                        LocalDateTime sourceUpdatedAt) {
        row.setSourceSystem(command.sourceSystem());
        row.setSourceKey(sourceKey);
        row.setSourceVersion(sourceVersion);
        row.setSourceLifecycleStatus(lifecycleStatus.name());
        row.setSourceUpdatedAt(sourceUpdatedAt);
        row.setSyncedAt(LocalDateTime.now(clock));
    }

    private void source(SalesOrderDO row, CommerceAuthorityBatchCommand command, String sourceKey,
                        String sourceVersion, CommerceSourceLifecycleStatus lifecycleStatus,
                        LocalDateTime sourceUpdatedAt) {
        row.setSourceSystem(command.sourceSystem());
        row.setSourceKey(sourceKey);
        row.setSourceVersion(sourceVersion);
        row.setSourceLifecycleStatus(lifecycleStatus.name());
        row.setSourceUpdatedAt(sourceUpdatedAt);
        row.setSyncedAt(LocalDateTime.now(clock));
    }

    private AuthoritySourceQuery sourceQuery(CommerceAuthorityBatchCommand command, String sourceKey) {
        return new AuthoritySourceQuery(command.tenantId(), command.sourceSystem(), sourceKey);
    }

    private PlatformCommandExecutionApi.SuccessFacts successFacts(CommerceAuthorityBatchCommand command,
                                                                  CommerceAuthorityBatchResult result) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("eventId", result.eventId());
        detail.put("batchId", result.batchId());
        detail.put("decision", result.decision().name());
        detail.put("sourceSystem", command.sourceSystem());
        detail.put("sourceWatermark", command.sourceWatermark());
        return new PlatformCommandExecutionApi.SuccessFacts("COM_AUTHORITY_BATCH_INGEST",
                "CommerceAuthorityBatch", command.batchId(), command.correlationId(),
                JsonUtils.toJsonString(detail), null, null);
    }

    private <T extends cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO> T base(T row, Long tenantId) {
        LocalDateTime now = LocalDateTime.now(clock);
        row.setTenantId(tenantId);
        row.setCreator("0");
        row.setUpdater("0");
        row.setCreateTime(now);
        row.setUpdateTime(now);
        row.setDeleted(false);
        return row;
    }

    private void touch(cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO row) {
        row.setUpdater("0");
        row.setUpdateTime(LocalDateTime.now(clock));
    }

    private void requireWrite(int affected, String message) {
        if (affected != 1) {
            throw new IllegalStateException(message);
        }
    }

    private CommerceAuthorityIngestException failure(CommerceAuthorityIngestException.Code code,
                                                      String message) {
        return new CommerceAuthorityIngestException(code, message);
    }

    private static final class BatchChanges {
        private boolean changed;
    }
}
