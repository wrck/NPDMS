package cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query.DeliveryScopeAcceptanceLockQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query.DeliveryScopeVersionLockRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

@Mapper
public interface DeliveryScopeMapper extends BaseMapperX<DeliveryScopeDO> {
    default List<DeliveryScopeDO> selectActiveByOrderLineIds(Collection<Long> orderLineIds) {
        if (orderLineIds == null || orderLineIds.isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<DeliveryScopeDO>()
                .in(DeliveryScopeDO::getOrderLineId, orderLineIds)
                .eq(DeliveryScopeDO::getScopeStatus, "ACTIVE")
                .isNull(DeliveryScopeDO::getEffectiveTo));
    }

    default List<DeliveryScopeDO> selectActiveByProjectId(Long projectId) {
        return selectList(new LambdaQueryWrapperX<DeliveryScopeDO>()
                .eq(DeliveryScopeDO::getProjectId, projectId)
                .eq(DeliveryScopeDO::getScopeStatus, "ACTIVE")
                .isNull(DeliveryScopeDO::getEffectiveTo)
                .orderByAsc(DeliveryScopeDO::getOrderLineId, DeliveryScopeDO::getId));
    }

    default List<DeliveryScopeDO> selectBySourceEvidencePrefix(Long tenantId, String sourceEvidencePrefix) {
        return selectList(new LambdaQueryWrapperX<DeliveryScopeDO>()
                .eq(DeliveryScopeDO::getTenantId, tenantId)
                .likeRight(DeliveryScopeDO::getSourceEvidence, sourceEvidencePrefix)
                .orderByAsc(DeliveryScopeDO::getId));
    }

    @Select("""
            SELECT *
            FROM com_delivery_scope
            WHERE tenant_id = #{tenantId}
              AND project_id = #{projectId}
              AND scope_status = 'ACTIVE'
              AND effective_to IS NULL
              AND deleted = b'0'
            ORDER BY order_line_id, id
            FOR UPDATE
            """)
    List<DeliveryScopeDO> selectActiveByProjectIdForUpdate(@Param("tenantId") Long tenantId,
                                                           @Param("projectId") Long projectId);

    List<DeliveryScopeVersionLockRow> selectCurrentVersionsForAcceptanceLock(
            @Param("query") DeliveryScopeAcceptanceLockQuery query);
}
