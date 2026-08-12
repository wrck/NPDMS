package cn.iocoder.yudao.module.pms.engineering.dal.mysql.solution;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.solution.vo.SolutionPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.solution.SolutionDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * PMS 实施方案 Mapper（FR-ENG-011 / FR-ENG-013）。
 */
@Mapper
public interface SolutionMapper extends BaseMapperX<SolutionDO> {

    default PageResult<SolutionDO> selectPage(SolutionPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<SolutionDO>()
                .eqIfPresent(SolutionDO::getProjectId, reqVO.getProjectId())
                .likeIfPresent(SolutionDO::getCode, reqVO.getCode())
                .likeIfPresent(SolutionDO::getName, reqVO.getName())
                .eqIfPresent(SolutionDO::getSolutionType, reqVO.getSolutionType())
                .eqIfPresent(SolutionDO::getStatus, reqVO.getStatus())
                .eqIfPresent(SolutionDO::getReviewLevel, reqVO.getReviewLevel())
                .orderByDesc(SolutionDO::getId));
    }

    default SolutionDO selectByProjectIdAndCode(Long projectId, String code) {
        return selectOne(SolutionDO::getProjectId, projectId, SolutionDO::getCode, code);
    }
}
