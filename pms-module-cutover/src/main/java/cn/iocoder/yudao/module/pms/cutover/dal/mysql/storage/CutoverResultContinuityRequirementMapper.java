package cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage.CutoverResultContinuityRequirementDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.query.CutoverResultContinuityRequirementListQuery;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

/** 连续性要求答案：固定表与固定父引用，保留租户及空集合语义。 */
@Mapper
public interface CutoverResultContinuityRequirementMapper extends BaseMapperX<CutoverResultContinuityRequirementDO> {

    default List<CutoverResultContinuityRequirementDO> selectListByParents(CutoverResultContinuityRequirementListQuery query) {
        if (query == null || query.tenantId() == null || query.checklistResultIds() == null || query.checklistResultIds().isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<CutoverResultContinuityRequirementDO>()
                .eq(CutoverResultContinuityRequirementDO::getTenantId, query.tenantId())
                .in(CutoverResultContinuityRequirementDO::getChecklistResultId, query.checklistResultIds())
                .orderByAsc(CutoverResultContinuityRequirementDO::getId));
    }
}
