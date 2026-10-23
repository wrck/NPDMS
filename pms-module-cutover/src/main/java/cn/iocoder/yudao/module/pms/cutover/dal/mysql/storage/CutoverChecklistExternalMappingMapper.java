package cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage.CutoverChecklistExternalMappingDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.query.CutoverChecklistExternalMappingListQuery;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

/** 外部参数与返回映射：固定表与固定父引用，保留租户及空集合语义。 */
@Mapper
public interface CutoverChecklistExternalMappingMapper extends BaseMapperX<CutoverChecklistExternalMappingDO> {

    default List<CutoverChecklistExternalMappingDO> selectListByParents(CutoverChecklistExternalMappingListQuery query) {
        if (query == null || query.tenantId() == null || query.itemDefinitionRevisionIds() == null || query.itemDefinitionRevisionIds().isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<CutoverChecklistExternalMappingDO>()
                .eq(CutoverChecklistExternalMappingDO::getTenantId, query.tenantId())
                .in(CutoverChecklistExternalMappingDO::getItemDefinitionRevisionId, query.itemDefinitionRevisionIds())
                .orderByAsc(CutoverChecklistExternalMappingDO::getMappingNo)
                .orderByAsc(CutoverChecklistExternalMappingDO::getId));
    }
}
