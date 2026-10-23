package cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage.CutoverResultPerformanceDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.query.CutoverResultPerformanceListQuery;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

/** 性能调研结果行：固定表与固定父引用，保留租户及空集合语义。 */
@Mapper
public interface CutoverResultPerformanceMapper extends BaseMapperX<CutoverResultPerformanceDO> {

    default List<CutoverResultPerformanceDO> selectListByParents(CutoverResultPerformanceListQuery query) {
        if (query == null || query.tenantId() == null || query.checklistResultIds() == null || query.checklistResultIds().isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<CutoverResultPerformanceDO>()
                .eq(CutoverResultPerformanceDO::getTenantId, query.tenantId())
                .in(CutoverResultPerformanceDO::getChecklistResultId, query.checklistResultIds())
                .orderByAsc(CutoverResultPerformanceDO::getRowNo)
                .orderByAsc(CutoverResultPerformanceDO::getId));
    }
}
