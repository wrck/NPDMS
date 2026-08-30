package cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.satisfaction.SatisfactionCollectionTaskDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.query.SatisfactionTaskTriggerLockQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SatisfactionCollectionTaskMapper extends BaseMapperX<SatisfactionCollectionTaskDO> {
    SatisfactionCollectionTaskDO selectByTriggerForUpdate(@Param("query") SatisfactionTaskTriggerLockQuery query);
}
