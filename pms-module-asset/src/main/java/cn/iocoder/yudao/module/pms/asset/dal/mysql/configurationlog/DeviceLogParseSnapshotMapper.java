package cn.iocoder.yudao.module.pms.asset.dal.mysql.configurationlog;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.configurationlog.DeviceLogParseSnapshotDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DeviceLogParseSnapshotMapper extends BaseMapperX<DeviceLogParseSnapshotDO> {
    DeviceLogParseSnapshotDO selectByFileForUpdate(@org.apache.ibatis.annotations.Param("query")
                                 cn.iocoder.yudao.module.pms.asset.dal.mysql.configurationlog.query.DeviceLogParseSnapshotLockQuery query);
}
