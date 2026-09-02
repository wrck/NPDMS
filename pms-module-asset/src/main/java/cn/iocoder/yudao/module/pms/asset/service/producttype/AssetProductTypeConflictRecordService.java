package cn.iocoder.yudao.module.pms.asset.service.producttype;

import cn.iocoder.yudao.module.pms.asset.dal.dataobject.producttype.AssetProductTypeDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.producttype.AssetProductTypeSourceMappingDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.producttype.AssetProductTypeMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.producttype.AssetProductTypeSourceMappingMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.producttype.query.ProductTypeSourceMappingConflictUpdate;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.producttype.query.ProductTypeSourceMappingLockQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AssetProductTypeConflictRecordService {

    private final AssetProductTypeSourceMappingMapper sourceMappingMapper;
    private final AssetProductTypeMapper productTypeMapper;
    private final AssetProductTypeAuditService auditService;

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void record(Long tenantId, Long actorId, AssetProductTypeImportRejectedException rejection) {
        AssetProductTypeSourceMappingDO mapping = sourceMappingMapper.selectForUpdate(
                new ProductTypeSourceMappingLockQuery(tenantId, rejection.command().sourceSystem(),
                        rejection.command().sourceKey()));
        String currentProductTypeCode = rejection.currentProductTypeCode();
        String currentSourceSystem = rejection.currentSourceSystem();
        String currentSourceKey = rejection.currentSourceKey();
        String currentSourceVersion = rejection.currentSourceVersion();
        java.time.LocalDateTime currentSourceUpdatedAt = rejection.observedSourceUpdatedAt();
        if (mapping != null) {
            AssetProductTypeDO currentProductType = mapping.getProductTypeId() == null
                    ? null : productTypeMapper.selectById(mapping.getProductTypeId());
            currentSourceSystem = mapping.getSourceSystem();
            currentSourceKey = mapping.getSourceKey();
            currentSourceVersion = mapping.getSourceVersion();
            currentSourceUpdatedAt = mapping.getSourceUpdatedAt();
            if (currentProductType != null) {
                currentProductTypeCode = currentProductType.getTypeCode();
            }
            if (rejection.observedSourceUpdatedAt() == null
                    || !mapping.getSourceUpdatedAt().isAfter(rejection.observedSourceUpdatedAt())) {
                sourceMappingMapper.markConflict(new ProductTypeSourceMappingConflictUpdate(
                        tenantId, mapping.getId(), rejection.command().productTypeCode(),
                        rejection.command().sourceVersion(), rejection.command().sourceUpdatedAt(),
                        rejection.command().payloadHash()));
            }
        }
        auditService.recordConflict(tenantId, actorId, rejection, currentProductTypeCode,
                currentSourceSystem, currentSourceKey, currentSourceVersion, currentSourceUpdatedAt);
    }
}
