package cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage.CutoverChecklistFieldDefinitionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.query.CutoverChecklistFieldDefinitionListQuery;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

/** 统一结构化字段定义：固定表与固定父引用，保留租户及空集合语义。 */
@Mapper
public interface CutoverChecklistFieldDefinitionMapper extends BaseMapperX<CutoverChecklistFieldDefinitionDO> {

    default List<CutoverChecklistFieldDefinitionDO> selectListByParents(CutoverChecklistFieldDefinitionListQuery query) {
        if (query == null || query.tenantId() == null || (query.itemDefinitionRevisionIds() == null || query.itemDefinitionRevisionIds().isEmpty())
                == (query.customChecklistItemIds() == null || query.customChecklistItemIds().isEmpty())) {
            return List.of();
        }
        var condition = new LambdaQueryWrapperX<CutoverChecklistFieldDefinitionDO>()
                .eq(CutoverChecklistFieldDefinitionDO::getTenantId, query.tenantId());
        if (query.itemDefinitionRevisionIds() != null && !query.itemDefinitionRevisionIds().isEmpty()) {
            condition.in(CutoverChecklistFieldDefinitionDO::getItemDefinitionRevisionId, query.itemDefinitionRevisionIds());
        } else {
            condition.in(CutoverChecklistFieldDefinitionDO::getCustomChecklistItemId, query.customChecklistItemIds());
        }
        return selectList(condition.orderByAsc(CutoverChecklistFieldDefinitionDO::getSortOrder)
                .orderByAsc(CutoverChecklistFieldDefinitionDO::getId));
    }
}
