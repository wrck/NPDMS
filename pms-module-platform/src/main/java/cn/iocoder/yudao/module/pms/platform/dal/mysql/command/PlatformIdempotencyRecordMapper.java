package cn.iocoder.yudao.module.pms.platform.dal.mysql.command;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.command.PlatformIdempotencyRecordDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.command.query.IdempotencyScopeQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PlatformIdempotencyRecordMapper extends BaseMapperX<PlatformIdempotencyRecordDO> {

    int insertIfAbsent(PlatformIdempotencyRecordDO record);

    PlatformIdempotencyRecordDO selectByScope(@Param("query") IdempotencyScopeQuery query);
}
