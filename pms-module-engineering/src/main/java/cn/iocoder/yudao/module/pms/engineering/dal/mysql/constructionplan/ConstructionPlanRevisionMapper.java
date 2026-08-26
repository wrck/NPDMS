package cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan;

import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.constructionplan.ConstructionPlanRevisionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.query.ConstructionPlanLockQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.query.ConstructionPlanRevisionLockQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.query.ConstructionPlanRevisionListQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.query.ConstructionPlanRevisionPageQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.query.ConstructionPlanRevisionDraftUpdate;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.query.ConstructionPlanRevisionSubmitUpdate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ConstructionPlanRevisionMapper {

    int insert(@Param("row") ConstructionPlanRevisionDO row);

    ConstructionPlanRevisionDO selectForUpdate(
            @Param("query") ConstructionPlanRevisionLockQuery query);

    ConstructionPlanRevisionDO selectById(
            @Param("query") ConstructionPlanRevisionLockQuery query);

    List<ConstructionPlanRevisionDO> selectListByIds(
            @Param("query") ConstructionPlanRevisionListQuery query);

    ConstructionPlanRevisionDO selectLatestForUpdate(
            @Param("query") ConstructionPlanLockQuery query);

    List<ConstructionPlanRevisionDO> selectPage(
            @Param("query") ConstructionPlanRevisionPageQuery query);

    int updateDraftIfMatch(@Param("update") ConstructionPlanRevisionDraftUpdate update);

    int freezeForSubmitIfMatch(@Param("update") ConstructionPlanRevisionSubmitUpdate update);

}
