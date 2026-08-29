package cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.configuration.CutoverChecklistBindingRuleRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration.query.CutoverConfigurationChildrenQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CutoverChecklistBindingRuleRevisionMapper
        extends BaseMapperX<CutoverChecklistBindingRuleRevisionDO> {

    default List<CutoverChecklistBindingRuleRevisionDO> selectListByRevision(
            CutoverConfigurationChildrenQuery query) {
        return selectList(new LambdaQueryWrapperX<CutoverChecklistBindingRuleRevisionDO>()
                .eq(CutoverChecklistBindingRuleRevisionDO::getConfigurationRevisionId,
                        query.configurationRevisionId())
                .orderByDesc(CutoverChecklistBindingRuleRevisionDO::getPriority)
                .orderByAsc(CutoverChecklistBindingRuleRevisionDO::getId));
    }

    int hardDeleteByRevisionId(@Param("query") CutoverConfigurationChildrenQuery query);
}
