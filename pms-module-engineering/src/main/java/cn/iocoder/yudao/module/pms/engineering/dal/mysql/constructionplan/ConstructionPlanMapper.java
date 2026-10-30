package cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.constructionplan.ConstructionPlanDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.query.ConstructionPlanLockQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.query.ConstructionPlanVersionUpdate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ConstructionPlanMapper extends BaseMapperX<ConstructionPlanDO> {

    /** P12-B1：insert/selectById 与 BaseMapper 契约重名，更名为专用方法以继承统一持久化映射。 */
    int insertRow(@Param("row") ConstructionPlanDO row);

    ConstructionPlanDO selectByProjectId(@Param("tenantId") Long tenantId,
                                         @Param("projectId") Long projectId);

    ConstructionPlanDO selectByLockQuery(@Param("query") ConstructionPlanLockQuery query);

    ConstructionPlanDO selectForUpdate(@Param("query") ConstructionPlanLockQuery query);

    int updateVersionIfMatch(@Param("update") ConstructionPlanVersionUpdate update);

}
