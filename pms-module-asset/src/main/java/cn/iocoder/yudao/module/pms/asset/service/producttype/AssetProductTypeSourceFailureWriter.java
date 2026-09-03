package cn.iocoder.yudao.module.pms.asset.service.producttype;

import cn.iocoder.yudao.module.pms.asset.dal.dataobject.producttype.AssetProductTypeDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.producttype.AssetProductTypeSourceMappingDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.producttype.AssetProductTypeMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.producttype.AssetProductTypeSourceMappingMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.producttype.query.ProductTypeSourceMappingLockQuery;
import cn.iocoder.yudao.module.pms.asset.service.producttype.command.RecordAssetProductTypeSourceFailureCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AssetProductTypeSourceFailureWriter {

    private final AssetProductTypeSourceMappingMapper sourceMappingMapper;
    private final AssetProductTypeMapper productTypeMapper;
    private final AssetProductTypeAuditService auditService;

    @Transactional(rollbackFor = Exception.class)
    public void markFailed(Long tenantId, Long actorId, RecordAssetProductTypeSourceFailureCommand command) {
        AssetProductTypeSourceMappingDO mapping = sourceMappingMapper.selectForUpdate(
                new ProductTypeSourceMappingLockQuery(tenantId, command.sourceSystem(), command.sourceKey()));
        if (mapping != null && mapping.getProductTypeId() != null) {
            AssetProductTypeDO productType = productTypeMapper.selectById(mapping.getProductTypeId());
            if (productType != null) {
                productType.setSyncStatus("FAILED");
                productType.setLastSyncAttemptAt(LocalDateTime.now());
                productTypeMapper.updateById(productType);
            }
        }
        auditService.recordSourceFailure(tenantId, actorId, command.operationId(),
                command.sourceSystem(), command.sourceKey(), command.failureCode());
    }
}
