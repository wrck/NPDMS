package cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage.CutoverResultBusinessSummaryDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.query.CutoverResultBusinessSummaryListQuery;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

/** 业务简述答案：固定表与固定父引用，保留租户及空集合语义。 */
@Mapper
public interface CutoverResultBusinessSummaryMapper extends BaseMapperX<CutoverResultBusinessSummaryDO> {

    default List<CutoverResultBusinessSummaryDO> selectListByParents(CutoverResultBusinessSummaryListQuery query) {
        if (query == null || query.tenantId() == null || query.checklistResultIds() == null || query.checklistResultIds().isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<CutoverResultBusinessSummaryDO>()
                .eq(CutoverResultBusinessSummaryDO::getTenantId, query.tenantId())
                .in(CutoverResultBusinessSummaryDO::getChecklistResultId, query.checklistResultIds())
                .orderByAsc(CutoverResultBusinessSummaryDO::getId));
    }
}
