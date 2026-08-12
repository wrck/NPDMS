package cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.requirement.vo.RequirementPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.requirement.RequirementDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * PMS 需求分析 Mapper（FR-ENG-004）。
 */
@Mapper
public interface RequirementMapper extends BaseMapperX<RequirementDO> {

    default PageResult<RequirementDO> selectPage(RequirementPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<RequirementDO>()
                .eqIfPresent(RequirementDO::getProjectId, reqVO.getProjectId())
                .likeIfPresent(RequirementDO::getCode, reqVO.getCode())
                .likeIfPresent(RequirementDO::getName, reqVO.getName())
                .eqIfPresent(RequirementDO::getRequirementType, reqVO.getRequirementType())
                .eqIfPresent(RequirementDO::getStatus, reqVO.getStatus())
                .orderByDesc(RequirementDO::getId));
    }

    default RequirementDO selectByProjectIdAndCode(Long projectId, String code) {
        return selectOne(RequirementDO::getProjectId, projectId, RequirementDO::getCode, code);
    }
}
