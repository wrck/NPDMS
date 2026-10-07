package cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.delivery.DeliveryRequirementDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query.DeliveryRequirementIdLockQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query.DeliveryRequirementIdentityLockQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query.DeliveryRequirementIdentityQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query.DeliveryRequirementScopeLockQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query.DeliveryRequirementTaskLockQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface DeliveryRequirementMapper extends BaseMapperX<DeliveryRequirementDO> {

    default Optional<DeliveryRequirementDO> selectByOwnerAndType(String ownerModule, String entityType,
                                                                 Long entityId, String typeCode) {
        return Optional.ofNullable(selectOne(new LambdaQueryWrapperX<DeliveryRequirementDO>()
                .eq(DeliveryRequirementDO::getOwnerModule, ownerModule)
                .eq(DeliveryRequirementDO::getEntityType, entityType)
                .eq(DeliveryRequirementDO::getEntityId, entityId)
                .eq(DeliveryRequirementDO::getTypeCode, typeCode)));
    }

    default List<DeliveryRequirementDO> selectByEntity(String ownerModule, String entityType, Long entityId) {
        return selectList(new LambdaQueryWrapperX<DeliveryRequirementDO>()
                .eq(DeliveryRequirementDO::getOwnerModule, ownerModule)
                .eq(DeliveryRequirementDO::getEntityType, entityType)
                .eq(DeliveryRequirementDO::getEntityId, entityId)
                .orderByAsc(DeliveryRequirementDO::getTypeCode));
    }

    /** 项目维度全量要求（跨 owner 三元组）：模板冻结链项目级视图使用。 */
    default List<DeliveryRequirementDO> selectByProject(Long projectId) {
        return selectList(new LambdaQueryWrapperX<DeliveryRequirementDO>()
                .eq(DeliveryRequirementDO::getProjectId, projectId)
                .orderByAsc(DeliveryRequirementDO::getId));
    }

    DeliveryRequirementDO selectIdForUpdate(@Param("query") DeliveryRequirementIdLockQuery query);

    DeliveryRequirementDO selectIdentity(@Param("query") DeliveryRequirementIdentityQuery query);

    DeliveryRequirementDO selectIdentityForUpdate(@Param("query") DeliveryRequirementIdentityLockQuery query);

    List<DeliveryRequirementDO> selectTaskForUpdate(@Param("query") DeliveryRequirementTaskLockQuery query);

    List<DeliveryRequirementDO> selectScopeForUpdate(@Param("query") DeliveryRequirementScopeLockQuery query);
}
