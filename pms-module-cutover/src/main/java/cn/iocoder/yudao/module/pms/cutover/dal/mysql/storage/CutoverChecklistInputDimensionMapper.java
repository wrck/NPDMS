package cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage.CutoverChecklistInputDimensionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.query.CutoverChecklistInputDimensionListQuery;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

/** 清单匹配输入维度：固定表与固定父引用，保留租户及空集合语义。 */
@Mapper
public interface CutoverChecklistInputDimensionMapper extends BaseMapperX<CutoverChecklistInputDimensionDO> {

    default List<CutoverChecklistInputDimensionDO> selectListByParents(CutoverChecklistInputDimensionListQuery query) {
        if (query == null || query.tenantId() == null || query.checklistIds() == null || query.checklistIds().isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<CutoverChecklistInputDimensionDO>()
                .eq(CutoverChecklistInputDimensionDO::getTenantId, query.tenantId())
                .in(CutoverChecklistInputDimensionDO::getChecklistId, query.checklistIds())
                .orderByAsc(CutoverChecklistInputDimensionDO::getValueNo)
                .orderByAsc(CutoverChecklistInputDimensionDO::getId));
    }
}
