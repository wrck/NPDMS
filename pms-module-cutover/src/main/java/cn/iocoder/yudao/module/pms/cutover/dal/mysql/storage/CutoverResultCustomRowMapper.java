package cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage.CutoverResultCustomRowDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.query.CutoverResultCustomRowListQuery;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

/** 真正自定义的表格结果行：固定表与固定父引用，保留租户及空集合语义。 */
@Mapper
public interface CutoverResultCustomRowMapper extends BaseMapperX<CutoverResultCustomRowDO> {

    default List<CutoverResultCustomRowDO> selectListByParents(CutoverResultCustomRowListQuery query) {
        if (query == null || query.tenantId() == null || query.checklistResultIds() == null || query.checklistResultIds().isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<CutoverResultCustomRowDO>()
                .eq(CutoverResultCustomRowDO::getTenantId, query.tenantId())
                .in(CutoverResultCustomRowDO::getChecklistResultId, query.checklistResultIds())
                .orderByAsc(CutoverResultCustomRowDO::getRowNo)
                .orderByAsc(CutoverResultCustomRowDO::getId));
    }
}
