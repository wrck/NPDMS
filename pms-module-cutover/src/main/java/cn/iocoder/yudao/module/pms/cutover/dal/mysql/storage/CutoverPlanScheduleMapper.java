package cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage.CutoverPlanScheduleDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.query.CutoverPlanScheduleListQuery;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

/** 方案计划安排行：固定表与固定父引用，保留租户及空集合语义。 */
@Mapper
public interface CutoverPlanScheduleMapper extends BaseMapperX<CutoverPlanScheduleDO> {

    default List<CutoverPlanScheduleDO> selectListByParents(CutoverPlanScheduleListQuery query) {
        if (query == null || query.tenantId() == null || query.planRevisionIds() == null || query.planRevisionIds().isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<CutoverPlanScheduleDO>()
                .eq(CutoverPlanScheduleDO::getTenantId, query.tenantId())
                .in(CutoverPlanScheduleDO::getPlanRevisionId, query.planRevisionIds())
                .orderByAsc(CutoverPlanScheduleDO::getSequenceNo)
                .orderByAsc(CutoverPlanScheduleDO::getId));
    }
}
