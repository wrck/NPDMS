package cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.entity;

import cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.entity.SiteSurveyImportSource;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.entity.query.SiteSurveyImportQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** The engineering module reads its own legacy source; it never updates it. */
@Mapper
public interface SiteSurveyImportMapper {
    SiteSurveyImportSource lockSource(@Param("query") SiteSurveyImportQuery query);
    cn.iocoder.yudao.module.pms.engineering.dal.dataobject.sitesurvey.entity.SiteSurveyEntityDO lockTarget(
            @Param("query") SiteSurveyImportQuery query);
}
