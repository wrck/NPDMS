package cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.constructionplan.ConstructionPlanDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.query.ConstructionPlanLockQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.query.ConstructionPlanVersionUpdate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ConstructionPlanMapper extends BaseMapperX<ConstructionPlanDO> {

    ConstructionPlanDO selectByProjectId(@Param("tenantId") Long tenantId,
                                         @Param("projectId") Long projectId);

    ConstructionPlanDO selectForUpdate(@Param("query") ConstructionPlanLockQuery query);

    int updateVersionIfMatch(@Param("update") ConstructionPlanVersionUpdate update);

}
