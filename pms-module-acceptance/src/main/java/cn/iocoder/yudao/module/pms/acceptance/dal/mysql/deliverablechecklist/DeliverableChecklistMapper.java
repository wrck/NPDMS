package cn.iocoder.yudao.module.pms.acceptance.dal.mysql.deliverablechecklist;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.ProjectScopedCodeMapper;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.deliverablechecklist.DeliverableChecklistDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.deliverablechecklist.query.DeliverableChecklistPageQuery;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.deliverablechecklist.query.DeliverableChecklistAcceptanceQuery;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Objects;

@Mapper
public interface DeliverableChecklistMapper extends ProjectScopedCodeMapper<DeliverableChecklistDO> {
    DeliverableChecklistDO selectOwnerForUpdate(@org.apache.ibatis.annotations.Param("query") cn.iocoder.yudao.module.pms.acceptance.dal.mysql.deliverablechecklist.query.DeliverableChecklistOwnerLockQuery query);

    default PageResult<DeliverableChecklistDO> selectPage(DeliverableChecklistPageQuery query) {
        Objects.requireNonNull(query.getVisibleProjectIds());
        if (query.getVisibleProjectIds().isEmpty()) {
            return new PageResult<>(List.of(), 0L);
        }
        return selectPage(query, new LambdaQueryWrapperX<DeliverableChecklistDO>()
                .eq(DeliverableChecklistDO::getTenantId, query.getTenantId())
                .in(DeliverableChecklistDO::getProjectId, query.getVisibleProjectIds())
                .eqIfPresent(DeliverableChecklistDO::getProjectId, query.getProjectId())
                .likeIfPresent(DeliverableChecklistDO::getCode, query.getCode())
                .likeIfPresent(DeliverableChecklistDO::getName, query.getName())
                .eqIfPresent(DeliverableChecklistDO::getAcceptanceId, query.getAcceptanceId())
                .eqIfPresent(DeliverableChecklistDO::getDeliverableType, query.getDeliverableType())
                .eqIfPresent(DeliverableChecklistDO::getStatus, query.getStatus())
                .orderByDesc(DeliverableChecklistDO::getId));
    }

    /**
     * 查询某验收下指定类型的交付件列表（FR-ACC-005 门禁校验数据源）
     */
    default List<DeliverableChecklistDO> selectByAcceptance(DeliverableChecklistAcceptanceQuery query) {
        return selectList(new LambdaQueryWrapperX<DeliverableChecklistDO>()
                .eq(DeliverableChecklistDO::getTenantId, query.tenantId())
                .eq(DeliverableChecklistDO::getAcceptanceId, query.acceptanceId())
                .eqIfPresent(DeliverableChecklistDO::getDeliverableType, query.deliverableType()));
    }

}
