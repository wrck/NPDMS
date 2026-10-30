package cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage.CutoverResultInterfacePlanDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.query.CutoverResultInterfacePlanListQuery;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

/** 接口互联规划结果行：固定表与固定父引用，保留租户及空集合语义。 */
@Mapper
public interface CutoverResultInterfacePlanMapper extends BaseMapperX<CutoverResultInterfacePlanDO> {

    default List<CutoverResultInterfacePlanDO> selectListByParents(CutoverResultInterfacePlanListQuery query) {
        if (query == null || query.tenantId() == null || query.checklistResultIds() == null || query.checklistResultIds().isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<CutoverResultInterfacePlanDO>()
                .eq(CutoverResultInterfacePlanDO::getTenantId, query.tenantId())
                .in(CutoverResultInterfacePlanDO::getChecklistResultId, query.checklistResultIds())
                .orderByAsc(CutoverResultInterfacePlanDO::getRowNo)
                .orderByAsc(CutoverResultInterfacePlanDO::getId));
    }
}
