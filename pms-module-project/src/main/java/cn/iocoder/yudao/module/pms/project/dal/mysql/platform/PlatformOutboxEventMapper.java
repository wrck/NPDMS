package cn.iocoder.yudao.module.pms.project.dal.mysql.platform;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.platform.PlatformOutboxEventDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PlatformOutboxEventMapper extends BaseMapperX<PlatformOutboxEventDO> {
}
