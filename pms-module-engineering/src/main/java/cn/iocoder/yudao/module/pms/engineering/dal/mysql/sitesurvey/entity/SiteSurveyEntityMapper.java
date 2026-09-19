package cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.entity;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.entity.query.SiteSurveyEntityPageQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.sitesurvey.entity.SiteSurveyEntityDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * PMS 现场工勘 Mapper（FR-ENG-001）。
 */
@Mapper
public interface SiteSurveyEntityMapper extends BaseMapperX<SiteSurveyEntityDO> {
    java.util.List<String> selectResultInventory(@org.apache.ibatis.annotations.Param("query")
            cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.entity.query.SurveyResultInventoryQuery query);

    java.util.List<SiteSurveyEntityDO> selectAssociationPage(@org.apache.ibatis.annotations.Param("query")
            cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.entity.query.SiteSurveyEntityAssociationPageQuery query);
    java.util.List<SiteSurveyEntityDO> selectTaskCandidates(@org.apache.ibatis.annotations.Param("query")
            cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.entity.query.SiteSurveyEntityTaskCandidateQuery query);

    SiteSurveyEntityDO selectTaskObject(@org.apache.ibatis.annotations.Param("query")
            cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.entity.query.SiteSurveyEntityTaskObjectQuery query);

    SiteSurveyEntityDO selectTaskObjectForUpdate(@org.apache.ibatis.annotations.Param("query")
            cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.entity.query.SiteSurveyEntityTaskObjectQuery query);

    int deleteDraft(@org.apache.ibatis.annotations.Param("query")
            cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.entity.query.SiteSurveyEntityMutation query);

    default PageResult<SiteSurveyEntityDO> selectPage(SiteSurveyEntityPageQuery reqVO) {
        if (reqVO.getVisibleProjectIds() == null || reqVO.getVisibleProjectIds().isEmpty()) {
            return new PageResult<>(java.util.List.of(), 0L);
        }
        return selectPage(reqVO, new LambdaQueryWrapperX<SiteSurveyEntityDO>()
                .eq(SiteSurveyEntityDO::getTenantId, reqVO.getTenantId())
                .in(SiteSurveyEntityDO::getProjectId, reqVO.getVisibleProjectIds())
                .eqIfPresent(SiteSurveyEntityDO::getProjectId, reqVO.getProjectId())
                .likeIfPresent(SiteSurveyEntityDO::getCode, reqVO.getCode())
                .likeIfPresent(SiteSurveyEntityDO::getName, reqVO.getName())
                .eqIfPresent(SiteSurveyEntityDO::getStatus, reqVO.getStatus())
                .eqIfPresent(SiteSurveyEntityDO::getSurveyorUserId, reqVO.getSurveyorUserId())
                .orderByDesc(SiteSurveyEntityDO::getId));
    }

    default SiteSurveyEntityDO selectByProjectIdAndCode(Long projectId, String code) {
        return selectOne(SiteSurveyEntityDO::getProjectId, projectId, SiteSurveyEntityDO::getCode, code);
    }
}
