package cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskDeviceScopeDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.query.CutoverActiveDeviceQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.query.CutoverTaskDeviceListQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CutoverTaskDeviceScopeMapper extends BaseMapperX<CutoverTaskDeviceScopeDO> {
    List<CutoverTaskDeviceScopeDO> selectActiveByTask(@Param("query") CutoverTaskDeviceListQuery query);

    List<CutoverTaskDeviceScopeDO> selectActiveForUpdate(@Param("query") CutoverActiveDeviceQuery query);
}
