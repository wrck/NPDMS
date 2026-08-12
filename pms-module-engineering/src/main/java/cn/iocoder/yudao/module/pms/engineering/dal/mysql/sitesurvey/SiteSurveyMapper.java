package cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.sitesurvey.vo.SiteSurveyPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.sitesurvey.SiteSurveyDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * PMS 现场工勘 Mapper（FR-ENG-001）。
 */
@Mapper
public interface SiteSurveyMapper extends BaseMapperX<SiteSurveyDO> {

    default PageResult<SiteSurveyDO> selectPage(SiteSurveyPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<SiteSurveyDO>()
                .eqIfPresent(SiteSurveyDO::getProjectId, reqVO.getProjectId())
                .likeIfPresent(SiteSurveyDO::getCode, reqVO.getCode())
                .likeIfPresent(SiteSurveyDO::getName, reqVO.getName())
                .eqIfPresent(SiteSurveyDO::getStatus, reqVO.getStatus())
                .eqIfPresent(SiteSurveyDO::getSurveyorUserId, reqVO.getSurveyorUserId())
                .orderByDesc(SiteSurveyDO::getId));
    }

    default SiteSurveyDO selectByProjectIdAndCode(Long projectId, String code) {
        return selectOne(SiteSurveyDO::getProjectId, projectId, SiteSurveyDO::getCode, code);
    }
}
