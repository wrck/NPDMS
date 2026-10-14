package cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage.CutoverChecklistResultQueryReferenceDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.query.CutoverChecklistResultQueryReferenceListQuery;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

/** 结果采用的查询集合：固定表与固定父引用，保留租户及空集合语义。 */
@Mapper
public interface CutoverChecklistResultQueryReferenceMapper extends BaseMapperX<CutoverChecklistResultQueryReferenceDO> {

    default List<CutoverChecklistResultQueryReferenceDO> selectListByParents(CutoverChecklistResultQueryReferenceListQuery query) {
        if (query == null || query.tenantId() == null || query.checklistResultIds() == null || query.checklistResultIds().isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<CutoverChecklistResultQueryReferenceDO>()
                .eq(CutoverChecklistResultQueryReferenceDO::getTenantId, query.tenantId())
                .in(CutoverChecklistResultQueryReferenceDO::getChecklistResultId, query.checklistResultIds())
                .orderByAsc(CutoverChecklistResultQueryReferenceDO::getSortOrder)
                .orderByAsc(CutoverChecklistResultQueryReferenceDO::getId));
    }
}
