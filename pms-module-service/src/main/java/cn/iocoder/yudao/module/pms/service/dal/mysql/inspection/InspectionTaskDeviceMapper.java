package cn.iocoder.yudao.module.pms.service.dal.mysql.inspection;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.InspectionTaskDeviceDO;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.query.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface InspectionTaskDeviceMapper extends BaseMapperX<InspectionTaskDeviceDO> {
    default List<InspectionTaskDeviceDO> selectForTask(InspectionTaskRows query) {
        return selectList(new LambdaQueryWrapperX<InspectionTaskDeviceDO>()
                .eq(InspectionTaskDeviceDO::getTenantId, query.tenantId())
                .eq(InspectionTaskDeviceDO::getTaskId, query.taskId()).orderByAsc(InspectionTaskDeviceDO::getId));
    }
}
