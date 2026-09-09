package cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.query.SiteSurveyPageQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.query.SiteSurveyRowQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.query.SiteSurveyWriteCondition;
import org.apache.ibatis.annotations.Param;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.sitesurvey.SiteSurveyDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * PMS 现场工勘 Mapper（FR-ENG-001）。
 */
@Mapper
public interface SiteSurveyMapper extends BaseMapperX<SiteSurveyDO> {

    SiteSurveyDO selectByRow(@Param("query") SiteSurveyRowQuery query);

    SiteSurveyDO selectForUpdate(@Param("query") SiteSurveyRowQuery query);

    int updateDraftIfMatch(@Param("condition") SiteSurveyWriteCondition condition,
                           @Param("survey") SiteSurveyDO survey);

    int initializeLocationIfMatch(@Param("condition") SiteSurveyWriteCondition condition,
                                  @Param("survey") SiteSurveyDO survey);

    int updateStatusIfMatch(@Param("condition") SiteSurveyWriteCondition condition,
                            @Param("newStatus") Integer newStatus);

    int deleteDraftIfMatch(@Param("condition") SiteSurveyWriteCondition condition);

    default PageResult<SiteSurveyDO> selectPage(SiteSurveyPageQuery reqVO) {
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
