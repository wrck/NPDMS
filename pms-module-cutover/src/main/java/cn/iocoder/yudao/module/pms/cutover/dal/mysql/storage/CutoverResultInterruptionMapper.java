package cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage.CutoverResultInterruptionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.query.CutoverResultInterruptionListQuery;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

/** 中断次数答案：固定表与固定父引用，保留租户及空集合语义。 */
@Mapper
public interface CutoverResultInterruptionMapper extends BaseMapperX<CutoverResultInterruptionDO> {

    default List<CutoverResultInterruptionDO> selectListByParents(CutoverResultInterruptionListQuery query) {
        if (query == null || query.tenantId() == null || query.checklistResultIds() == null || query.checklistResultIds().isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<CutoverResultInterruptionDO>()
                .eq(CutoverResultInterruptionDO::getTenantId, query.tenantId())
                .in(CutoverResultInterruptionDO::getChecklistResultId, query.checklistResultIds())
                .orderByAsc(CutoverResultInterruptionDO::getId));
    }
}
