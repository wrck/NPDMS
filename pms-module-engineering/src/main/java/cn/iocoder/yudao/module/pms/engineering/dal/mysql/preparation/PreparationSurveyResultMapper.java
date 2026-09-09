package cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation;

import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.PreparationSurveyResultDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.PreparationItemRowQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PreparationSurveyResultMapper {
    PreparationSurveyResultDO selectByItem(@Param("query") PreparationItemRowQuery query);
    int insert(@Param("row") PreparationSurveyResultDO row);
    int update(@Param("row") PreparationSurveyResultDO row);
}
