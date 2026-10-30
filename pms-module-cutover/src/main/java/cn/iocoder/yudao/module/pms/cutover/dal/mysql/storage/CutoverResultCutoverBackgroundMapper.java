package cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage.CutoverResultCutoverBackgroundDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.query.CutoverResultCutoverBackgroundListQuery;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

/** 割接背景答案：固定表与固定父引用，保留租户及空集合语义。 */
@Mapper
public interface CutoverResultCutoverBackgroundMapper extends BaseMapperX<CutoverResultCutoverBackgroundDO> {

    default List<CutoverResultCutoverBackgroundDO> selectListByParents(CutoverResultCutoverBackgroundListQuery query) {
        if (query == null || query.tenantId() == null || query.checklistResultIds() == null || query.checklistResultIds().isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<CutoverResultCutoverBackgroundDO>()
                .eq(CutoverResultCutoverBackgroundDO::getTenantId, query.tenantId())
                .in(CutoverResultCutoverBackgroundDO::getChecklistResultId, query.checklistResultIds())
                .orderByAsc(CutoverResultCutoverBackgroundDO::getId));
    }
}
