package cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage.CutoverResultIpPlanDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.query.CutoverResultIpPlanListQuery;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

/** IP与网关规划结果行：固定表与固定父引用，保留租户及空集合语义。 */
@Mapper
public interface CutoverResultIpPlanMapper extends BaseMapperX<CutoverResultIpPlanDO> {

    default List<CutoverResultIpPlanDO> selectListByParents(CutoverResultIpPlanListQuery query) {
        if (query == null || query.tenantId() == null || query.checklistResultIds() == null || query.checklistResultIds().isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<CutoverResultIpPlanDO>()
                .eq(CutoverResultIpPlanDO::getTenantId, query.tenantId())
                .in(CutoverResultIpPlanDO::getChecklistResultId, query.checklistResultIds())
                .orderByAsc(CutoverResultIpPlanDO::getRowNo)
                .orderByAsc(CutoverResultIpPlanDO::getId));
    }
}
