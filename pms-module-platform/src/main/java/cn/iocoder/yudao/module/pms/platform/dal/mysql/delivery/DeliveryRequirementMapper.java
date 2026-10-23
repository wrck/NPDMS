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
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query.DeliveryRequirementEntityQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query.DeliveryRequirementProjectQuery;

@Mapper
public interface DeliveryRequirementMapper extends BaseMapperX<DeliveryRequirementDO> {

    default List<DeliveryRequirementDO> selectByEntity(DeliveryRequirementEntityQuery query) {
        return selectList(new LambdaQueryWrapperX<DeliveryRequirementDO>()
                .eq(DeliveryRequirementDO::getArchivedReopenSnapshotId, 0L)
                .eq(DeliveryRequirementDO::getTenantId, query.tenantId())
                .eq(DeliveryRequirementDO::getOwnerModule, query.ownerModule())
                .eq(DeliveryRequirementDO::getEntityType, query.entityType())
                .eq(DeliveryRequirementDO::getEntityId, query.entityId())
                .orderByAsc(DeliveryRequirementDO::getTypeCode));
    }

    /** 项目维度全量要求（跨 owner 三元组）：模板冻结链项目级视图使用。 */
    default List<DeliveryRequirementDO> selectByProject(DeliveryRequirementProjectQuery query) {
        return selectList(new LambdaQueryWrapperX<DeliveryRequirementDO>()
                .eq(DeliveryRequirementDO::getArchivedReopenSnapshotId, 0L)
                .eq(DeliveryRequirementDO::getTenantId, query.tenantId())
                .eq(DeliveryRequirementDO::getProjectId, query.projectId())
                .orderByAsc(DeliveryRequirementDO::getId));
    }

    DeliveryRequirementDO selectIdForUpdate(@Param("query") DeliveryRequirementIdLockQuery query);

    DeliveryRequirementDO selectIdentity(@Param("query") DeliveryRequirementIdentityQuery query);

    DeliveryRequirementDO selectIdentityForUpdate(@Param("query") DeliveryRequirementIdentityLockQuery query);

    List<DeliveryRequirementDO> selectTaskForUpdate(@Param("query") DeliveryRequirementTaskLockQuery query);

    List<DeliveryRequirementDO> selectScopeForUpdate(@Param("query") DeliveryRequirementScopeLockQuery query);
}
