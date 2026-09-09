package cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation;

import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.PreparationSurveyDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.PreparationRowQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PreparationSurveyMapper {
    PreparationSurveyDO selectByPreparation(@Param("query") PreparationRowQuery query);
    int insert(@Param("row") PreparationSurveyDO row);
    int update(@Param("row") PreparationSurveyDO row);
}
