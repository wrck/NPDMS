package cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.delivery.DeliveryMaterialDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query.DeliveryMaterialArchiveRetryQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query.DeliveryMaterialArchiveStateQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query.DeliveryMaterialIdLockQuery;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query.DeliveryRequirementMaterialQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query.DeliverySourceFileQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query.DeliverySourceBusinessQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query.DeliverySourceIdentityQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query.DeliveryMaterialOwnerQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query.DeliveryMaterialOriginUpdate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface DeliveryMaterialMapper extends BaseMapperX<DeliveryMaterialDO> {
    int withdrawIfVersion(@Param("query") cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query.DeliveryMaterialWithdrawalQuery query);
    int assignSourceIdentityIfMissing(@Param("query") cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query.DeliverySourceIdentityAssignment query);


    /** 归档补偿队列：按归档状态取材料（补偿方按文件锚点分组推进）。 */
    default List<DeliveryMaterialDO> selectByArchiveStatus(String archiveStatus) {
        return selectList(new LambdaQueryWrapperX<DeliveryMaterialDO>()
                .eq(DeliveryMaterialDO::getArchiveStatus, archiveStatus)
                .orderByAsc(DeliveryMaterialDO::getId));
    }

    /** 归档补偿锁定读：补偿事务内固定材料行，防止并发双写归档状态。 */
    List<DeliveryMaterialDO> selectMaterialsForUpdate(@Param("query") DeliveryMaterialIdLockQuery query);

    /** 归档状态推进：仅当行仍为 PENDING_COMPENSATION 时生效（返回是否更新）。 */
    int updateArchiveStateIfPending(@Param("query") DeliveryMaterialArchiveStateQuery query);

    /** 归档失败水位：仅当行仍为 PENDING_COMPENSATION 时累加重试计数。 */
    int bumpArchiveRetryIfPending(@Param("query") DeliveryMaterialArchiveRetryQuery query);

    default List<DeliveryMaterialDO> selectByEntity(String ownerModule, String entityType, Long entityId,
                                                    String typeCode) {
        return selectList(new LambdaQueryWrapperX<DeliveryMaterialDO>()
                .eq(DeliveryMaterialDO::getOwnerModule, ownerModule)
                .eq(DeliveryMaterialDO::getEntityType, entityType)
                .eq(DeliveryMaterialDO::getEntityId, entityId)
                .eqIfPresent(DeliveryMaterialDO::getTypeCode, typeCode)
                .orderByDesc(DeliveryMaterialDO::getId));
    }

    default List<DeliveryMaterialDO> selectByMaterialIds(Collection<Long> ids) {
        return selectList(new LambdaQueryWrapperX<DeliveryMaterialDO>()
                .in(DeliveryMaterialDO::getId, ids));
    }

    /** 项目级汇总：材料的项目上下文维度（不改 owner 三元组语义）。 */
    default List<DeliveryMaterialDO> selectByProject(Long projectId) {
        return selectList(new LambdaQueryWrapperX<DeliveryMaterialDO>()
                .eq(DeliveryMaterialDO::getProjectId, projectId)
                .orderByDesc(DeliveryMaterialDO::getId));
    }

    /** BUSINESS_RESULT 行幂等查询：同一来源实体 + 业务对象 + 修订锚。 */
    default DeliveryMaterialDO selectByBusinessObject(String ownerModule, String entityType, Long entityId,
                                                      String businessObjectType, String businessObjectId,
                                                      Long businessRevisionNo) {
        List<DeliveryMaterialDO> rows = selectList(new LambdaQueryWrapperX<DeliveryMaterialDO>()
                .eq(DeliveryMaterialDO::getOwnerModule, ownerModule)
                .eq(DeliveryMaterialDO::getEntityType, entityType)
                .eq(DeliveryMaterialDO::getEntityId, entityId)
                .eq(DeliveryMaterialDO::getBusinessObjectType, businessObjectType)
                .eq(DeliveryMaterialDO::getBusinessObjectId, businessObjectId)
                .eqIfPresent(DeliveryMaterialDO::getBusinessRevisionNo, businessRevisionNo)
                .orderByDesc(DeliveryMaterialDO::getId));
        return rows.isEmpty() ? null : rows.get(0);
    }

    List<DeliveryMaterialDO> selectListForOwner(@Param("query") DeliveryMaterialOwnerQuery query);
    int requireArchiveIfNotRequired(@Param("query") cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query.DeliveryMaterialArchiveObligationQuery query);

    int assignOriginIfMissing(@Param("query") DeliveryMaterialOriginUpdate query);

    /** 要求使用关系查询：投影退出状态，不修改共享材料本身。 */
    default List<DeliveryMaterialDO> selectByRequirement(Long requirementId) {
        return selectListForRequirement(new DeliveryRequirementMaterialQuery(
                TenantContextHolder.getRequiredTenantId(), requirementId));
    }

    List<DeliveryMaterialDO> selectListForRequirement(@Param("query") DeliveryRequirementMaterialQuery query);
    DeliveryMaterialDO selectSourceIdentityForUpdate(@Param("query") DeliverySourceIdentityQuery query);
    /** 保留历史重复登记行，以确定的最早登记材料作为来源版本的复用身份。 */
    DeliveryMaterialDO selectSourceFile(@Param("query") DeliverySourceFileQuery query);
    /** 历史多要求成果登记不重写；按精确修订（含NULL）选择最早材料。 */
    DeliveryMaterialDO selectSourceBusiness(@Param("query") DeliverySourceBusinessQuery query);

    /** 模板冻结要求的 BUSINESS_RESULT 幂等：同一要求 + 业务对象 + 修订锚。 */
    default DeliveryMaterialDO selectByRequirementAndBusinessObject(Long requirementId,
                                                                    String businessObjectType,
                                                                    String businessObjectId,
                                                                    Long businessRevisionNo) {
        List<DeliveryMaterialDO> rows = selectList(new LambdaQueryWrapperX<DeliveryMaterialDO>()
                .eq(DeliveryMaterialDO::getRequirementId, requirementId)
                .eq(DeliveryMaterialDO::getBusinessObjectType, businessObjectType)
                .eq(DeliveryMaterialDO::getBusinessObjectId, businessObjectId)
                .eqIfPresent(DeliveryMaterialDO::getBusinessRevisionNo, businessRevisionNo)
                .orderByDesc(DeliveryMaterialDO::getId));
        return rows.isEmpty() ? null : rows.get(0);
    }
}
