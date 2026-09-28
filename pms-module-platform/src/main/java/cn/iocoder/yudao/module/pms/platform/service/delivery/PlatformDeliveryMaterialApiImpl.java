package cn.iocoder.yudao.module.pms.platform.service.delivery;

import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryMaterialApi;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.delivery.DeliveryMaterialDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.DeliveryMaterialMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 统一交付材料跨模块 API 实现：登记动作复用 DeliveryMaterialService 的证据校验与幂等语义，
 * 查询动作只读。模块间唯一写入口（P06R 设计 2.2）。
 */
@Service
@RequiredArgsConstructor
public class PlatformDeliveryMaterialApiImpl implements PlatformDeliveryMaterialApi {

    private final DeliveryMaterialService materialService;
    private final DeliveryMaterialMapper materialMapper;

    @Override
    @Transactional
    public Long registerFileMaterial(String ownerModule, String entityType, Long entityId, String typeCode,
                                     Long fileReferenceId, String title, String sourceKind, Long projectId) {
        return materialService.registerFile(ownerModule, entityType, entityId, typeCode, fileReferenceId,
                title, sourceKind, projectId, null).getId();
    }

    @Override
    @Transactional
    public Long registerBusinessResultMaterial(String ownerModule, String entityType, Long entityId, String typeCode,
                                               String businessObjectType, String businessObjectId,
                                               Long businessRevisionNo, String title, Long projectId) {
        return materialService.registerBusinessResult(ownerModule, entityType, entityId, typeCode,
                businessObjectType, businessObjectId, businessRevisionNo, title, projectId).getId();
    }

    @Override
    public List<DeliveryMaterialView> listByEntity(String ownerModule, String entityType, Long entityId) {
        return materialMapper.selectByEntity(ownerModule, entityType, entityId, null).stream()
                .map(PlatformDeliveryMaterialApiImpl::toView).toList();
    }

    @Override
    public List<DeliveryMaterialView> listByEntityAndType(String ownerModule, String entityType, Long entityId,
                                                          String typeCode) {
        return materialMapper.selectByEntity(ownerModule, entityType, entityId, typeCode).stream()
                .map(PlatformDeliveryMaterialApiImpl::toView).toList();
    }

    @Override
    public List<DeliveryMaterialView> listByProject(Long projectId) {
        return materialMapper.selectByProject(projectId).stream()
                .map(PlatformDeliveryMaterialApiImpl::toView).toList();
    }

    static DeliveryMaterialView toView(DeliveryMaterialDO row) {
        return new DeliveryMaterialView(row.getId(), row.getOwnerModule(), row.getEntityType(), row.getEntityId(),
                row.getTypeCode(), row.getMaterialKind(), row.getRequirementId(), row.getProjectId(),
                row.getFileArtifactId(), row.getFileVersionNo(), row.getFileSha256(), row.getFileName(),
                row.getBusinessObjectType(), row.getBusinessObjectId(), row.getBusinessRevisionNo(),
                row.getTitle(), row.getSourceKind(), row.getStatus(), row.getArchiveStatus(),
                row.getCreator(), row.getCreateTime());
    }
}
