package cn.iocoder.yudao.module.pms.project.dal.mysql.portfolio;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.portfolio.ProjectPortfolioRuleDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * PMS 项目组合动态规则 Mapper
 */
@Mapper
public interface ProjectPortfolioRuleMapper extends BaseMapperX<ProjectPortfolioRuleDO> {

    default List<ProjectPortfolioRuleDO> selectListByPortfolioId(Long portfolioId) {
        return selectList(new LambdaQueryWrapperX<ProjectPortfolioRuleDO>()
                .eq(ProjectPortfolioRuleDO::getPortfolioId, portfolioId)
                .orderByAsc(ProjectPortfolioRuleDO::getId));
    }

    default int deleteByPortfolioId(Long portfolioId) {
        return delete(new LambdaQueryWrapperX<ProjectPortfolioRuleDO>()
                .eq(ProjectPortfolioRuleDO::getPortfolioId, portfolioId));
    }

}
