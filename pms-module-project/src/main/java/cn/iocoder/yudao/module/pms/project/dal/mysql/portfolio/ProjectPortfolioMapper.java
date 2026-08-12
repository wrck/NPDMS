package cn.iocoder.yudao.module.pms.project.dal.mysql.portfolio;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.controller.admin.portfolio.vo.ProjectPortfolioPageReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.portfolio.ProjectPortfolioDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * PMS 项目组合 Mapper
 */
@Mapper
public interface ProjectPortfolioMapper extends BaseMapperX<ProjectPortfolioDO> {

    default PageResult<ProjectPortfolioDO> selectPage(ProjectPortfolioPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ProjectPortfolioDO>()
                .likeIfPresent(ProjectPortfolioDO::getCode, reqVO.getCode())
                .likeIfPresent(ProjectPortfolioDO::getName, reqVO.getName())
                .eqIfPresent(ProjectPortfolioDO::getStatus, reqVO.getStatus())
                .eqIfPresent(ProjectPortfolioDO::getMemberType, reqVO.getMemberType())
                .eqIfPresent(ProjectPortfolioDO::getOwnerUserId, reqVO.getOwnerUserId())
                .orderByDesc(ProjectPortfolioDO::getId));
    }

    default ProjectPortfolioDO selectByCode(String code) {
        return selectOne(ProjectPortfolioDO::getCode, code);
    }

}
