package cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage.CutoverChecklistExternalQueryParameterDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.query.CutoverChecklistExternalQueryParameterListQuery;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

/** 外部查询实际参数：固定表与固定父引用，保留租户及空集合语义。 */
@Mapper
public interface CutoverChecklistExternalQueryParameterMapper extends BaseMapperX<CutoverChecklistExternalQueryParameterDO> {

    default List<CutoverChecklistExternalQueryParameterDO> selectListByParents(CutoverChecklistExternalQueryParameterListQuery query) {
        if (query == null || query.tenantId() == null || query.externalQueryIds() == null || query.externalQueryIds().isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<CutoverChecklistExternalQueryParameterDO>()
                .eq(CutoverChecklistExternalQueryParameterDO::getTenantId, query.tenantId())
                .in(CutoverChecklistExternalQueryParameterDO::getExternalQueryId, query.externalQueryIds())
                .orderByAsc(CutoverChecklistExternalQueryParameterDO::getId));
    }
}
