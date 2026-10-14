package cn.iocoder.yudao.module.pms.cutover.dal.mysql.task;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverTaskDeviceScopeDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.query.CutoverTaskDeviceListQuery;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface CutoverFrozenDeviceMapper extends BaseMapperX<CutoverTaskDeviceScopeDO> {
    default List<CutoverTaskDeviceScopeDO> byTask(CutoverTaskDeviceListQuery query) {
        return selectList(new LambdaQueryWrapperX<CutoverTaskDeviceScopeDO>()
                .eq(CutoverTaskDeviceScopeDO::getTenantId, query.tenantId())
                .eq(CutoverTaskDeviceScopeDO::getCutoverTaskId, query.taskId())
                .orderByAsc(CutoverTaskDeviceScopeDO::getDeviceId));
    }
}
