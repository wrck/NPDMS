package cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage.CutoverChecklistExternalQueryDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.query.CutoverChecklistExternalQueryListQuery;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

/** 外部加载记录：固定表与固定父引用，保留租户及空集合语义。 */
@Mapper
public interface CutoverChecklistExternalQueryMapper extends BaseMapperX<CutoverChecklistExternalQueryDO> {

    default List<CutoverChecklistExternalQueryDO> selectListByParents(CutoverChecklistExternalQueryListQuery query) {
        if (query == null || query.tenantId() == null || query.checklistItemIds() == null || query.checklistItemIds().isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<CutoverChecklistExternalQueryDO>()
                .eq(CutoverChecklistExternalQueryDO::getTenantId, query.tenantId())
                .in(CutoverChecklistExternalQueryDO::getChecklistItemId, query.checklistItemIds())
                .orderByAsc(CutoverChecklistExternalQueryDO::getQueryNo)
                .orderByAsc(CutoverChecklistExternalQueryDO::getId));
    }
}
