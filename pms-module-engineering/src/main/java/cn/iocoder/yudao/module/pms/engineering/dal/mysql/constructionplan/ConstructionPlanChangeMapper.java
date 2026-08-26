package cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.constructionplan.ConstructionPlanChangeDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.query.ConstructionPlanChangeLockQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.query.ConstructionPlanChangePageQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.query.ConstructionPlanChangeProcessQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.query.ConstructionPlanChangeVersionUpdate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ConstructionPlanChangeMapper extends BaseMapperX<ConstructionPlanChangeDO> {

    ConstructionPlanChangeDO selectForUpdate(@Param("query") ConstructionPlanChangeLockQuery query);

    ConstructionPlanChangeDO selectByProcessInstanceId(
            @Param("query") ConstructionPlanChangeProcessQuery query);

    List<ConstructionPlanChangeDO> selectPage(
            @Param("query") ConstructionPlanChangePageQuery query);

    int updateVersionIfMatch(@Param("update") ConstructionPlanChangeVersionUpdate update);

}
