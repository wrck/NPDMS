package cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage.CutoverResultCheckFeedbackDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.query.CutoverResultCheckFeedbackListQuery;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

/** 风险及双机检查反馈：固定表与固定父引用，保留租户及空集合语义。 */
@Mapper
public interface CutoverResultCheckFeedbackMapper extends BaseMapperX<CutoverResultCheckFeedbackDO> {

    default List<CutoverResultCheckFeedbackDO> selectListByParents(CutoverResultCheckFeedbackListQuery query) {
        if (query == null || query.tenantId() == null || query.checklistResultIds() == null || query.checklistResultIds().isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<CutoverResultCheckFeedbackDO>()
                .eq(CutoverResultCheckFeedbackDO::getTenantId, query.tenantId())
                .in(CutoverResultCheckFeedbackDO::getChecklistResultId, query.checklistResultIds())
                .orderByAsc(CutoverResultCheckFeedbackDO::getId));
    }
}
