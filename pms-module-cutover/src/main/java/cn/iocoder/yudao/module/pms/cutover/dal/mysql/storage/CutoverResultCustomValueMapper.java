package cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage.CutoverResultCustomValueDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.query.CutoverResultCustomValueListQuery;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

/** 真正自定义的对象/单值答案：固定表与固定父引用，保留租户及空集合语义。 */
@Mapper
public interface CutoverResultCustomValueMapper extends BaseMapperX<CutoverResultCustomValueDO> {

    default List<CutoverResultCustomValueDO> selectListByParents(CutoverResultCustomValueListQuery query) {
        if (query == null || query.tenantId() == null || query.checklistResultIds() == null || query.checklistResultIds().isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<CutoverResultCustomValueDO>()
                .eq(CutoverResultCustomValueDO::getTenantId, query.tenantId())
                .in(CutoverResultCustomValueDO::getChecklistResultId, query.checklistResultIds())
                .orderByAsc(CutoverResultCustomValueDO::getId));
    }
}
