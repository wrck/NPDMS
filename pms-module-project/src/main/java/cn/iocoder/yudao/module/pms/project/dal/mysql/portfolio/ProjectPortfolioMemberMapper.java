package cn.iocoder.yudao.module.pms.project.dal.mysql.portfolio;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.portfolio.ProjectPortfolioMemberDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * PMS 项目组合成员 Mapper
 */
@Mapper
public interface ProjectPortfolioMemberMapper extends BaseMapperX<ProjectPortfolioMemberDO> {

    default List<ProjectPortfolioMemberDO> selectListByPortfolioId(Long portfolioId) {
        return selectList(new LambdaQueryWrapperX<ProjectPortfolioMemberDO>()
                .eq(ProjectPortfolioMemberDO::getPortfolioId, portfolioId)
                .orderByAsc(ProjectPortfolioMemberDO::getId));
    }

    default ProjectPortfolioMemberDO selectByPortfolioIdAndProjectId(Long portfolioId, Long projectId) {
        return selectOne(new LambdaQueryWrapperX<ProjectPortfolioMemberDO>()
                .eq(ProjectPortfolioMemberDO::getPortfolioId, portfolioId)
                .eq(ProjectPortfolioMemberDO::getProjectId, projectId));
    }

    default int deleteByPortfolioId(Long portfolioId) {
        return delete(new LambdaQueryWrapperX<ProjectPortfolioMemberDO>()
                .eq(ProjectPortfolioMemberDO::getPortfolioId, portfolioId));
    }

}
