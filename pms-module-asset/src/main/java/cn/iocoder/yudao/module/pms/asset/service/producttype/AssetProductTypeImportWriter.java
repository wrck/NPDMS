package cn.iocoder.yudao.module.pms.asset.service.producttype;

import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.producttype.AssetProductTypeDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.producttype.AssetProductTypeSourceMappingDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.producttype.DeviceCurrentProductTypeDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.producttype.AssetProductTypeMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.producttype.AssetProductTypeSourceMappingMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.producttype.DeviceCurrentProductTypeMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.producttype.query.DeviceCurrentProductTypeClose;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.producttype.query.DeviceCurrentProductTypeLockQuery;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.producttype.query.ProductTypeCodeLockQuery;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.producttype.query.ProductTypeSourceMappingImportLockQuery;
import cn.iocoder.yudao.module.pms.asset.service.producttype.command.DeviceCurrentProductTypeInput;
import cn.iocoder.yudao.module.pms.asset.service.producttype.command.ImportAssetProductTypeCommand;
import cn.iocoder.yudao.module.pms.asset.service.producttype.command.ImportAssetProductTypeResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.asset.enums.ErrorCodeConstants.AST_PRODUCT_TYPE_INVALID_REQUEST;

@Service
@RequiredArgsConstructor
public class AssetProductTypeImportWriter {

    private static final Set<String> RESOLUTION_STATUSES = Set.of("RESOLVED", "UNKNOWN", "CONFLICT", "UNRESOLVED");

    private final AssetProductTypeSourceOrder sourceOrder;
    private final AssetProductTypeMapper productTypeMapper;
    private final AssetProductTypeSourceMappingMapper sourceMappingMapper;
    private final DeviceCurrentProductTypeMapper currentProductTypeMapper;
    private final DeviceMapper deviceMapper;

    @Transactional(rollbackFor = Exception.class)
    public ImportAssetProductTypeResult importOnce(Long tenantId, Long actorId,
                                                   ImportAssetProductTypeCommand command) {
        validate(tenantId, actorId, command);
        AssetProductTypeSourceMappingDO mapping = sourceMappingMapper.selectForImportUpdate(
                new ProductTypeSourceMappingImportLockQuery(tenantId, command.sourceSystem(), command.sourceKey()));
        if (mapping != null && Boolean.TRUE.equals(mapping.getDeleted())) {
            throw AssetProductTypeImportRejectedException.sourceKeyReserved(command, mapping.getSourceUpdatedAt());
        }
        AssetProductTypeDO mappedProductType = mapping == null || mapping.getProductTypeId() == null
                ? null : productTypeMapper.selectById(mapping.getProductTypeId());
        if (mapping != null) {
            AssetProductTypeSourceOrder.Decision decision = sourceOrder.decide(
                    command.sourceUpdatedAt(), command.sourceVersion(), command.payloadHash(), command.productTypeCode(),
                    mapping.getSourceUpdatedAt(), mapping.getSourceVersion(), mapping.getPayloadHash(),
                    mappedProductType == null ? null : mappedProductType.getTypeCode());
            if (decision == AssetProductTypeSourceOrder.Decision.STALE_SOURCE) {
                throw AssetProductTypeImportRejectedException.stale(command, mapping.getSourceUpdatedAt());
            }
            if (decision == AssetProductTypeSourceOrder.Decision.SOURCE_CONFLICT) {
                throw AssetProductTypeImportRejectedException.sourceConflict(command,
                        mappedProductType == null ? null : mappedProductType.getTypeCode(), mapping.getSourceUpdatedAt());
            }
            if (decision == AssetProductTypeSourceOrder.Decision.IDEMPOTENT_REPLAY) {
                return new ImportAssetProductTypeResult(mapping.getProductTypeId(), mapping.getId(),
                        command.productTypeCode(), true);
            }
            if (mappedProductType != null && !Objects.equals(mappedProductType.getTypeCode(), command.productTypeCode())) {
                throw AssetProductTypeImportRejectedException.sourceConflict(
                        command, mappedProductType.getTypeCode(), mapping.getSourceUpdatedAt());
            }
        }

        LocalDateTime now = LocalDateTime.now();
        AssetProductTypeDO productType = productTypeMapper.selectByCodeForUpdate(
                new ProductTypeCodeLockQuery(tenantId, command.productTypeCode()));
        if (productType != null && (!Objects.equals(productType.getSourceSystem(), command.sourceSystem())
                || !Objects.equals(productType.getSourceKey(), command.sourceKey()))) {
            throw AssetProductTypeImportRejectedException.codeConflict(command, productType.getSourceSystem(),
                    productType.getSourceKey(), productType.getSourceVersion(), productType.getSourceUpdatedAt());
        }
        if (productType == null) {
            productType = newProductType(tenantId, actorId, command, now);
            productTypeMapper.insert(productType);
        } else {
            updateProductType(productType, command, now);
            if (productTypeMapper.updateById(productType) != 1) {
                throw exception(AST_PRODUCT_TYPE_INVALID_REQUEST);
            }
        }

        if (mapping == null) {
            mapping = newMapping(tenantId, actorId, command, productType.getId(), now);
            sourceMappingMapper.insert(mapping);
        } else {
            updateMapping(mapping, command, productType.getId(), now);
            if (sourceMappingMapper.updateById(mapping) != 1) {
                throw exception(AST_PRODUCT_TYPE_INVALID_REQUEST);
            }
        }

        for (DeviceCurrentProductTypeInput input : command.devices().stream()
                .sorted(Comparator.comparing(DeviceCurrentProductTypeInput::deviceId)).toList()) {
            applyDeviceCurrent(tenantId, actorId, command, mapping, productType, input, now);
        }
        return new ImportAssetProductTypeResult(productType.getId(), mapping.getId(), command.productTypeCode(), false);
    }

    private void applyDeviceCurrent(Long tenantId, Long actorId, ImportAssetProductTypeCommand command,
                                    AssetProductTypeSourceMappingDO mapping, AssetProductTypeDO productType,
                                    DeviceCurrentProductTypeInput input, LocalDateTime now) {
        DeviceDO device = deviceMapper.selectByTenantAndIdForUpdate(tenantId, input.deviceId());
        if (device == null || !Objects.equals(device.getTenantId(), tenantId)) {
            throw AssetProductTypeImportRejectedException.crossTenant(command);
        }
        DeviceCurrentProductTypeDO current = currentProductTypeMapper.selectCurrentForUpdate(
                new DeviceCurrentProductTypeLockQuery(tenantId, input.deviceId()));
        if (current != null && currentProductTypeMapper.closeCurrent(
                new DeviceCurrentProductTypeClose(tenantId, input.deviceId(), now)) != 1) {
            throw exception(AST_PRODUCT_TYPE_INVALID_REQUEST);
        }
        DeviceCurrentProductTypeDO next = new DeviceCurrentProductTypeDO();
        next.setTenantId(tenantId);
        next.setDeviceId(input.deviceId());
        next.setResolutionStatus(input.resolutionStatus());
        if ("RESOLVED".equals(input.resolutionStatus())) {
            next.setProductTypeId(productType.getId());
            next.setProductTypeCode(productType.getTypeCode());
            next.setSourceMappingId(mapping.getId());
        } else {
            next.setSourceMappingId(mapping.getId());
        }
        next.setSourceVersion(command.sourceVersion());
        next.setSourceUpdatedAt(command.sourceUpdatedAt());
        next.setEffectiveFrom(now);
        next.setVersion(0);
        next.setCreator(String.valueOf(actorId));
        next.setUpdater(String.valueOf(actorId));
        currentProductTypeMapper.insert(next);
    }

    private AssetProductTypeDO newProductType(Long tenantId, Long actorId, ImportAssetProductTypeCommand command,
                                              LocalDateTime now) {
        AssetProductTypeDO productType = new AssetProductTypeDO();
        productType.setTenantId(tenantId);
        productType.setTypeCode(command.productTypeCode());
        updateProductType(productType, command, now);
        productType.setVersion(0);
        productType.setCreator(String.valueOf(actorId));
        productType.setUpdater(String.valueOf(actorId));
        return productType;
    }

    private void updateProductType(AssetProductTypeDO productType, ImportAssetProductTypeCommand command,
                                   LocalDateTime now) {
        productType.setDisplayName(command.displayName());
        productType.setEnabled(command.enabled());
        productType.setSourceSystem(command.sourceSystem());
        productType.setSourceKey(command.sourceKey());
        productType.setSourceVersion(command.sourceVersion());
        productType.setSourceUpdatedAt(command.sourceUpdatedAt());
        productType.setPayloadHash(command.payloadHash());
        productType.setSyncStatus("FRESH");
        productType.setLastSyncAttemptAt(now);
        productType.setSyncedAt(now);
    }

    private AssetProductTypeSourceMappingDO newMapping(Long tenantId, Long actorId,
                                                       ImportAssetProductTypeCommand command,
                                                       Long productTypeId, LocalDateTime now) {
        AssetProductTypeSourceMappingDO mapping = new AssetProductTypeSourceMappingDO();
        mapping.setTenantId(tenantId);
        updateMapping(mapping, command, productTypeId, now);
        mapping.setVersion(0);
        mapping.setCreator(String.valueOf(actorId));
        mapping.setUpdater(String.valueOf(actorId));
        return mapping;
    }

    private void updateMapping(AssetProductTypeSourceMappingDO mapping, ImportAssetProductTypeCommand command,
                               Long productTypeId, LocalDateTime now) {
        mapping.setSourceSystem(command.sourceSystem());
        mapping.setSourceKey(command.sourceKey());
        mapping.setSourceVersion(command.sourceVersion());
        mapping.setSourceUpdatedAt(command.sourceUpdatedAt());
        mapping.setPayloadHash(command.payloadHash());
        mapping.setProductTypeId(productTypeId);
        mapping.setMappingStatus("RESOLVED");
        mapping.setConflictProductTypeCode(null);
        mapping.setConflictSourceVersion(null);
        mapping.setConflictSourceUpdatedAt(null);
        mapping.setConflictPayloadHash(null);
        mapping.setSyncedAt(now);
    }

    private void validate(Long tenantId, Long actorId, ImportAssetProductTypeCommand command) {
        if (tenantId == null || actorId == null || command == null
                || isBlank(command.operationId()) || command.operationId().length() > 128
                || isBlank(command.idempotencyKey()) || command.idempotencyKey().length() > 128
                || isBlank(command.productTypeCode()) || command.productTypeCode().length() > 64
                || isBlank(command.displayName()) || command.displayName().length() > 128
                || isBlank(command.sourceSystem()) || command.sourceSystem().length() > 32
                || isBlank(command.sourceKey()) || command.sourceKey().length() > 128
                || isBlank(command.sourceVersion()) || command.sourceVersion().length() > 128
                || command.sourceUpdatedAt() == null || command.payloadHash() == null
                || !command.payloadHash().matches("[0-9a-fA-F]{64}")) {
            throw exception(AST_PRODUCT_TYPE_INVALID_REQUEST);
        }
        if (command.devices().stream().anyMatch(input -> input == null || input.deviceId() == null
                || input.deviceId() <= 0 || !RESOLUTION_STATUSES.contains(input.resolutionStatus()))
                || command.devices().stream().map(DeviceCurrentProductTypeInput::deviceId).distinct().count()
                != command.devices().size()) {
            throw exception(AST_PRODUCT_TYPE_INVALID_REQUEST);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
