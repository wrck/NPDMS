package cn.iocoder.yudao.module.pms.project.dal.mysql.projectschedule;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectschedule.StageSuggestionRuleDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectschedule.query.StageSuggestionRuleListQuery;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 3.1 工期建议计划时间规则 Mapper。
 */
@Mapper
public interface StageSuggestionRuleMapper extends BaseMapperX<StageSuggestionRuleDO> {

    /** 参与推算的有效规则：启用行，含该签约方式精确行与全部变体行；租户条件由 TenantBaseDO 自动生效。 */
    default List<StageSuggestionRuleDO> selectActiveRules(StageSuggestionRuleListQuery query) {
        return selectList(new LambdaQueryWrapperX<StageSuggestionRuleDO>()
                .eq(StageSuggestionRuleDO::getEnabled, true)
                .and(wrapper -> wrapper.eq(StageSuggestionRuleDO::getSigningMethod, query.getSigningMethod())
                        .or().isNull(StageSuggestionRuleDO::getSigningMethod)));
    }
}
