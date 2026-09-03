package cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.acceptancereport.AcceptanceActivityDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.query.AcceptanceActivityIdLockQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.query.AcceptanceActivityCompleteUpdate;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.query.AcceptanceActivityIdentityLockQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.query.AcceptanceActivityScopeQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AcceptanceActivityMapper extends BaseMapperX<AcceptanceActivityDO> {

    AcceptanceActivityDO selectByIdentityForUpdate(
            @Param("query") AcceptanceActivityIdentityLockQuery query);

    AcceptanceActivityDO selectByIdForUpdate(@Param("query") AcceptanceActivityIdLockQuery query);

    List<AcceptanceActivityDO> selectByProjectScope(@Param("query") AcceptanceActivityScopeQuery query);

    int completeIfPending(@Param("update") AcceptanceActivityCompleteUpdate update);
}
