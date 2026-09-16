package cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.entity;

import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.sitesurvey.entity.*;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.entity.query.SiteSurveyDetailQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface SiteSurveyDetailMapper {
    List<SiteSurveyConditionDO> selectConditions(@Param("query") SiteSurveyDetailQuery query);
    List<SiteSurveyMaterialDO> selectMaterials(@Param("query") SiteSurveyDetailQuery query);
    int deleteConditions(@Param("query") SiteSurveyDetailQuery query);
    int deleteMaterials(@Param("query") SiteSurveyDetailQuery query);
    int insertCondition(@Param("row") SiteSurveyConditionDO row);
    int insertMaterial(@Param("row") SiteSurveyMaterialDO row);
}
