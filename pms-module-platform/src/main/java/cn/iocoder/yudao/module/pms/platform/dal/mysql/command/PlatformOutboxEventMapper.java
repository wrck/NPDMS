package cn.iocoder.yudao.module.pms.platform.dal.mysql.command;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.command.PlatformOutboxEventDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PlatformOutboxEventMapper extends BaseMapperX<PlatformOutboxEventDO> {
}
