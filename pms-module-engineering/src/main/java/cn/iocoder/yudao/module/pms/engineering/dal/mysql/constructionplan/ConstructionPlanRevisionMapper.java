package cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan;

import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.constructionplan.ConstructionPlanRevisionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.query.ConstructionPlanLockQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.query.ConstructionPlanRevisionLockQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.query.ConstructionPlanRevisionPageQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ConstructionPlanRevisionMapper {

    int insert(@Param("row") ConstructionPlanRevisionDO row);

    ConstructionPlanRevisionDO selectForUpdate(
            @Param("query") ConstructionPlanRevisionLockQuery query);

    ConstructionPlanRevisionDO selectLatestForUpdate(
            @Param("query") ConstructionPlanLockQuery query);

    List<ConstructionPlanRevisionDO> selectPage(
            @Param("query") ConstructionPlanRevisionPageQuery query);

}
