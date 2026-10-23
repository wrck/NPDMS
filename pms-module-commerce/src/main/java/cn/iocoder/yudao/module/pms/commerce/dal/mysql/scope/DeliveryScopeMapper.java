package cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query.DeliveryScopeAcceptanceLockQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query.DeliveryScopeIdQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query.DeliveryScopeOrderLineQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query.DeliveryScopePageQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query.DeliveryScopeOrderContractQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query.DeliveryScopeOrderContractRow;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query.DeliveryScopeVersionLockRow;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query.DeliveryScopeProjectQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query.DeliveryScopeEvidenceQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query.DeliveryScopeSelectionQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query.DeliveryScopeOrderFieldsQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query.DeliveryScopeOrderFieldsRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DeliveryScopeMapper extends BaseMapperX<DeliveryScopeDO> {
    default List<DeliveryScopeDO> selectConfirmedSelection(DeliveryScopeSelectionQuery query) {
        if (query.scopeIds() == null || query.scopeIds().isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapperX<DeliveryScopeDO>()
                .eq(DeliveryScopeDO::getTenantId, query.tenantId())
                .eq(DeliveryScopeDO::getProjectId, query.projectId())
                .in(DeliveryScopeDO::getId, query.scopeIds())
                .eq(DeliveryScopeDO::getAllocationConfirmed, true)
                .isNull(DeliveryScopeDO::getEffectiveTo));
    }

    default List<DeliveryScopeDO> selectActiveByOrderLineIds(DeliveryScopeOrderLineQuery query) {
        if (query.orderLineIds() == null || query.orderLineIds().isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<DeliveryScopeDO>()
                .eq(DeliveryScopeDO::getTenantId, query.tenantId())
                .in(DeliveryScopeDO::getOrderLineId, query.orderLineIds())
                .eq(DeliveryScopeDO::getAllocationConfirmed, true)
                .eq(DeliveryScopeDO::getScopeStatus, "ACTIVE")
                .isNull(DeliveryScopeDO::getEffectiveTo));
    }

    default List<DeliveryScopeDO> selectActiveByProjectId(DeliveryScopeProjectQuery query) {
        return selectList(new LambdaQueryWrapperX<DeliveryScopeDO>()
                .eq(DeliveryScopeDO::getTenantId, query.tenantId())
                .eq(DeliveryScopeDO::getProjectId, query.projectId())
                .eq(DeliveryScopeDO::getAllocationConfirmed, true)
                .eq(DeliveryScopeDO::getScopeStatus, "ACTIVE")
                .isNull(DeliveryScopeDO::getEffectiveTo)
                .orderByAsc(DeliveryScopeDO::getOrderLineId, DeliveryScopeDO::getId));
    }

    default List<DeliveryScopeDO> selectBySourceEvidencePrefix(DeliveryScopeEvidenceQuery query) {
        if (query.sourceEvidencePrefix() == null || query.sourceEvidencePrefix().isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapperX<DeliveryScopeDO>()
                .eq(DeliveryScopeDO::getTenantId, query.tenantId())
                .eq(DeliveryScopeDO::getAllocationConfirmed, true)
                .likeRight(DeliveryScopeDO::getSourceEvidence, query.sourceEvidencePrefix())
                .orderByAsc(DeliveryScopeDO::getId));
    }

    default DeliveryScopeDO selectCurrentById(DeliveryScopeIdQuery query) {
        return selectOne(new LambdaQueryWrapperX<DeliveryScopeDO>()
                .eq(DeliveryScopeDO::getTenantId, query.tenantId())
                .eq(DeliveryScopeDO::getId, query.deliveryScopeId())
                .eq(DeliveryScopeDO::getAllocationConfirmed, true)
                .isNull(DeliveryScopeDO::getEffectiveTo));
    }

    List<DeliveryScopeDO> selectActiveByProjectIdForUpdate(@Param("query") DeliveryScopeProjectQuery query);

    List<DeliveryScopeDO> selectCurrentByProjectId(@Param("query") DeliveryScopeProjectQuery query);

    List<DeliveryScopeDO> selectCurrentByProjectIdForUpdate(@Param("query") DeliveryScopeProjectQuery query);

    List<DeliveryScopeDO> selectActiveByOrderLineIdsForUpdate(
            @Param("query") DeliveryScopeOrderLineQuery query);

    List<DeliveryScopeDO> selectCurrentByOrderLineIdsForUpdate(
            @Param("query") DeliveryScopeOrderLineQuery query);

    DeliveryScopeDO selectCurrentByIdForUpdate(@Param("query") DeliveryScopeIdQuery query);

    List<DeliveryScopeVersionLockRow> selectCurrentVersionsForAcceptanceLock(
            @Param("query") DeliveryScopeAcceptanceLockQuery query);

    Long selectCountByProjectScope(@Param("query") DeliveryScopePageQuery query);

    List<DeliveryScopeDO> selectPageByProjectScope(@Param("query") DeliveryScopePageQuery query);

    List<DeliveryScopeOrderFieldsRow> selectOrderFields(@Param("query") DeliveryScopeOrderFieldsQuery query);
    List<DeliveryScopeOrderContractRow> selectOrderContracts(@Param("query") DeliveryScopeOrderContractQuery query);
}
