package cn.iocoder.yudao.module.pms.commerce.service.authority;

import cn.iocoder.yudao.module.pms.commerce.api.authority.CommerceAuthorityWriteApi;
import cn.iocoder.yudao.module.pms.commerce.api.authority.dto.AuthorityWriteResult;
import cn.iocoder.yudao.module.pms.commerce.api.authority.dto.CommerceAuthorityWriteCommand;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.contract.ContractDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderLineDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.common.query.AuthoritySourceLockQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.ContractMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.SalesOrderLineMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.SalesOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CommerceAuthorityWriteService implements CommerceAuthorityWriteApi {

    private final ContractMapper contractMapper;
    private final SalesOrderMapper orderMapper;
    private final SalesOrderLineMapper lineMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AuthorityWriteResult apply(CommerceAuthorityWriteCommand command) {
        validateCommand(command);
        boolean replayed = true;
        for (CommerceAuthorityWriteCommand.ContractSourceRecord source : safe(command.contracts())) {
            replayed &= !writeContract(command.tenantId(), source);
        }
        Map<String, SalesOrderDO> orders = new HashMap<>();
        for (CommerceAuthorityWriteCommand.SalesOrderSourceRecord source : safe(command.salesOrders())) {
            WriteResult result = writeOrder(command.tenantId(), source);
            replayed &= !result.changed();
            orders.put(sourceKey(source.sourceSystem(), source.sourceRecordKey()), result.order());
        }
        for (CommerceAuthorityWriteCommand.SalesOrderLineSourceRecord source : safe(command.salesOrderLines())) {
            SalesOrderDO parent = orders.get(sourceKey(source.sourceSystem(), source.orderSourceRecordKey()));
            if (parent == null) {
                parent = orderMapper.selectBySourceForUpdate(new AuthoritySourceLockQuery(
                        command.tenantId(), source.sourceSystem(), source.orderSourceRecordKey()));
                if (parent == null) {
                    throw new IllegalStateException("COMMERCE_AUTHORITY_PARENT_ORDER_MISSING");
                }
            }
            replayed &= !writeLine(command.tenantId(), parent, source);
        }
        return new AuthorityWriteResult(command.sourceBatchId(), replayed,
                safe(command.contracts()).size(), safe(command.salesOrders()).size(),
                safe(command.salesOrderLines()).size());
    }

    private boolean writeContract(Long tenantId, CommerceAuthorityWriteCommand.ContractSourceRecord source) {
        validateSource(source.sourceSystem(), source.sourceRecordKey(), source.sourceVersion(), source.sourceUpdatedAt());
        ContractDO current = contractMapper.selectBySourceForUpdate(
                new AuthoritySourceLockQuery(tenantId, source.sourceSystem(), source.sourceRecordKey()));
        if (current != null && (!Objects.equals(current.getCompanyCode(), source.companyCode())
                || !Objects.equals(current.getContractNo(), source.contractNo()))) {
            throw new IllegalStateException("COMMERCE_AUTHORITY_IDENTITY_CONFLICT");
        }
        if (current != null && !shouldApply(current.getMasterSourceVersion(), current.getSourceUpdatedAt(),
                source.sourceVersion(), source.sourceUpdatedAt(), sameContract(current, source))) {
            return false;
        }
        ContractDO target = current == null ? new ContractDO() : current;
        target.setTenantId(tenantId);
        target.setMasterSourceSystem(source.sourceSystem());
        target.setMasterSourceRecordKey(source.sourceRecordKey());
        target.setMasterSourceVersion(source.sourceVersion());
        target.setCompanyCode(required(source.companyCode()));
        target.setContractNo(required(source.contractNo()));
        target.setContractName(source.contractName());
        target.setStatus(required(source.status()));
        target.setSourceUpdatedAt(source.sourceUpdatedAt());
        target.setSourceSyncTime(LocalDateTime.now());
        if (current == null) {
            target.setVersion(0);
            contractMapper.insert(target);
        } else {
            contractMapper.updateById(target);
        }
        return true;
    }

    private WriteResult writeOrder(Long tenantId, CommerceAuthorityWriteCommand.SalesOrderSourceRecord source) {
        validateSource(source.sourceSystem(), source.sourceRecordKey(), source.sourceVersion(), source.sourceUpdatedAt());
        SalesOrderDO current = orderMapper.selectBySourceForUpdate(
                new AuthoritySourceLockQuery(tenantId, source.sourceSystem(), source.sourceRecordKey()));
        if (current != null && (!Objects.equals(current.getCompanyCode(), source.companyCode())
                || !Objects.equals(current.getOrderType(), source.orderType())
                || !Objects.equals(current.getOrderNo(), source.orderNo()))) {
            throw new IllegalStateException("COMMERCE_AUTHORITY_IDENTITY_CONFLICT");
        }
        if (current != null && !shouldApply(current.getSourceVersion(), current.getSourceUpdatedAt(),
                source.sourceVersion(), source.sourceUpdatedAt(), sameOrder(current, source))) {
            return new WriteResult(current, false);
        }
        SalesOrderDO target = current == null ? new SalesOrderDO() : current;
        target.setTenantId(tenantId);
        target.setSourceSystem(source.sourceSystem());
        target.setSourceRecordKey(source.sourceRecordKey());
        target.setSourceVersion(source.sourceVersion());
        target.setCompanyCode(required(source.companyCode()));
        target.setOrderType(required(source.orderType()));
        target.setOrderNo(required(source.orderNo()));
        target.setStatus(required(source.status()));
        target.setSourceUpdatedAt(source.sourceUpdatedAt());
        target.setSourceSyncTime(LocalDateTime.now());
        if (current == null) {
            target.setVersion(0);
            orderMapper.insert(target);
        } else {
            orderMapper.updateById(target);
        }
        return new WriteResult(target, true);
    }

    private boolean writeLine(Long tenantId, SalesOrderDO parent,
                              CommerceAuthorityWriteCommand.SalesOrderLineSourceRecord source) {
        validateSource(source.sourceSystem(), source.sourceRecordKey(), source.sourceVersion(), source.sourceUpdatedAt());
        if (source.unitScale() == null || source.unitScale() < 0 || source.unitScale() > 6) {
            throw new IllegalArgumentException("COMMERCE_AUTHORITY_UNIT_SCALE_INVALID");
        }
        validateQuantities(source);
        SalesOrderLineDO current = lineMapper.selectBySourceForUpdate(
                new AuthoritySourceLockQuery(tenantId, source.sourceSystem(), source.sourceRecordKey()));
        if (current != null && (!Objects.equals(current.getOrderId(), parent.getId())
                || !Objects.equals(current.getLineNo(), source.lineNo()))) {
            throw new IllegalStateException("COMMERCE_AUTHORITY_IDENTITY_CONFLICT");
        }
        if (current != null && !shouldApply(current.getSourceVersion(), current.getSourceUpdatedAt(),
                source.sourceVersion(), source.sourceUpdatedAt(), sameLine(current, parent.getId(), source))) {
            return false;
        }
        SalesOrderLineDO target = current == null ? new SalesOrderLineDO() : current;
        target.setTenantId(tenantId);
        target.setOrderId(parent.getId());
        target.setSourceSystem(source.sourceSystem());
        target.setSourceRecordKey(source.sourceRecordKey());
        target.setSourceVersion(source.sourceVersion());
        target.setLineNo(required(source.lineNo()));
        target.setItemCode(source.itemCode());
        target.setItemDesc(source.itemDescription());
        target.setProductCode(source.productCode());
        target.setOrderQty(source.orderQuantity());
        target.setOpenQty(source.openQuantity());
        target.setDeliveredQty(source.deliveredQuantity());
        target.setUnitCode(required(source.unitCode()));
        target.setUnitScale(source.unitScale());
        target.setQuantityStatus(required(source.quantityStatus()));
        target.setStatus(required(source.status()));
        target.setSourceUpdatedAt(source.sourceUpdatedAt());
        target.setSourceSyncTime(LocalDateTime.now());
        target.setCompanyId(parent.getCompanyId());
        target.setCompanyCode(parent.getCompanyCode());
        target.setCompanyName(parent.getCompanyName());
        target.setOrderType(parent.getOrderType());
        target.setOrderNo(parent.getOrderNo());
        if (current == null) {
            target.setVersion(0);
            lineMapper.insert(target);
        } else {
            lineMapper.updateById(target);
        }
        return true;
    }

    private boolean shouldApply(String currentVersion, LocalDateTime currentTime,
                                String sourceVersion, LocalDateTime sourceTime, boolean samePayload) {
        if (Objects.equals(currentVersion, sourceVersion)) {
            if (!samePayload) {
                throw new IllegalStateException("COMMERCE_AUTHORITY_SAME_VERSION_CONFLICT");
            }
            return false;
        }
        if (currentTime != null && !sourceTime.isAfter(currentTime)) {
            if (sourceTime.isEqual(currentTime)) {
                throw new IllegalStateException("COMMERCE_AUTHORITY_SOURCE_ORDER_CONFLICT");
            }
            return false;
        }
        return true;
    }

    private boolean sameContract(ContractDO current, CommerceAuthorityWriteCommand.ContractSourceRecord source) {
        return Objects.equals(current.getCompanyCode(), source.companyCode())
                && Objects.equals(current.getContractNo(), source.contractNo())
                && Objects.equals(current.getContractName(), source.contractName())
                && Objects.equals(current.getStatus(), source.status());
    }

    private boolean sameOrder(SalesOrderDO current, CommerceAuthorityWriteCommand.SalesOrderSourceRecord source) {
        return Objects.equals(current.getCompanyCode(), source.companyCode())
                && Objects.equals(current.getOrderType(), source.orderType())
                && Objects.equals(current.getOrderNo(), source.orderNo())
                && Objects.equals(current.getStatus(), source.status());
    }

    private boolean sameLine(SalesOrderLineDO current, Long orderId,
                             CommerceAuthorityWriteCommand.SalesOrderLineSourceRecord source) {
        return Objects.equals(current.getOrderId(), orderId)
                && Objects.equals(current.getLineNo(), source.lineNo())
                && Objects.equals(current.getItemCode(), source.itemCode())
                && Objects.equals(current.getItemDesc(), source.itemDescription())
                && Objects.equals(current.getProductCode(), source.productCode())
                && decimalEquals(current.getOrderQty(), source.orderQuantity())
                && decimalEquals(current.getOpenQty(), source.openQuantity())
                && decimalEquals(current.getDeliveredQty(), source.deliveredQuantity())
                && Objects.equals(current.getUnitCode(), source.unitCode())
                && Objects.equals(current.getUnitScale(), source.unitScale())
                && Objects.equals(current.getQuantityStatus(), source.quantityStatus())
                && Objects.equals(current.getStatus(), source.status());
    }

    private void validateCommand(CommerceAuthorityWriteCommand command) {
        if (command == null || command.tenantId() == null || required(command.sourceBatchId()) == null
                || required(command.operationId()) == null) {
            throw new IllegalArgumentException("COMMERCE_AUTHORITY_INVALID_ARGUMENT");
        }
    }

    private void validateSource(String system, String key, String version, LocalDateTime updatedAt) {
        if (required(system) == null || required(key) == null || required(version) == null || updatedAt == null) {
            throw new IllegalArgumentException("COMMERCE_AUTHORITY_INVALID_SOURCE");
        }
    }

    private String required(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    private String sourceKey(String sourceSystem, String sourceRecordKey) {
        return sourceSystem + '\u0000' + sourceRecordKey;
    }

    private boolean decimalEquals(java.math.BigDecimal left, java.math.BigDecimal right) {
        return left == null ? right == null : right != null && left.compareTo(right) == 0;
    }

    private void validateQuantities(CommerceAuthorityWriteCommand.SalesOrderLineSourceRecord source) {
        if ("CONFIRMED".equals(source.quantityStatus()) && source.orderQuantity() == null) {
            throw new IllegalArgumentException("COMMERCE_AUTHORITY_CONFIRMED_QUANTITY_REQUIRED");
        }
        for (java.math.BigDecimal quantity : new java.math.BigDecimal[]{
                source.orderQuantity(), source.openQuantity(), source.deliveredQuantity()}) {
            if (quantity == null) {
                continue;
            }
            if (quantity.signum() < 0 || Math.max(quantity.stripTrailingZeros().scale(), 0) > source.unitScale()) {
                throw new IllegalArgumentException("COMMERCE_AUTHORITY_QUANTITY_PRECISION_INVALID");
            }
        }
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private record WriteResult(SalesOrderDO order, boolean changed) {
    }
}
