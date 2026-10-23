package cn.iocoder.yudao.module.pms.cutover.dal.mysql.task;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverTaskDeviceScopeDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.query.CutoverActiveDeviceQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.query.CutoverTaskDeviceListQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.query.CutoverTaskDeviceReleaseUpdate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CutoverTaskDeviceScopeMapper extends BaseMapperX<CutoverTaskDeviceScopeDO> {
    List<CutoverTaskDeviceScopeDO> selectActiveByTask(@Param("query") CutoverTaskDeviceListQuery query);

    List<CutoverTaskDeviceScopeDO> selectActiveForUpdate(@Param("query") CutoverActiveDeviceQuery query);

    List<CutoverTaskDeviceScopeDO> selectActiveByTaskForUpdate(@Param("query") CutoverTaskDeviceListQuery query);

    int releaseActiveByTask(@Param("query") CutoverTaskDeviceReleaseUpdate query);
}
