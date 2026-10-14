package cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage.CutoverChecklistBindingConditionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.query.CutoverChecklistBindingConditionListQuery;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

/** 规则维度允许值：固定表与固定父引用，保留租户及空集合语义。 */
@Mapper
public interface CutoverChecklistBindingConditionMapper extends BaseMapperX<CutoverChecklistBindingConditionDO> {

    default List<CutoverChecklistBindingConditionDO> selectListByParents(CutoverChecklistBindingConditionListQuery query) {
        if (query == null || query.tenantId() == null || query.bindingRuleRevisionIds() == null || query.bindingRuleRevisionIds().isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<CutoverChecklistBindingConditionDO>()
                .eq(CutoverChecklistBindingConditionDO::getTenantId, query.tenantId())
                .in(CutoverChecklistBindingConditionDO::getBindingRuleRevisionId, query.bindingRuleRevisionIds())
                .orderByAsc(CutoverChecklistBindingConditionDO::getValueNo)
                .orderByAsc(CutoverChecklistBindingConditionDO::getId));
    }
}
