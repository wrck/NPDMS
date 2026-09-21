package cn.iocoder.yudao.module.pms.asset.dal.mysql.device;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.ProjectDeviceSelectionQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ProjectDeviceSelectionMapper extends BaseMapperX<DeviceDO> {
    default PageResult<DeviceDO> selectSelectionPage(ProjectDeviceSelectionQuery query) {
        if (query.getProjectId() == null || query.getProjectId() <= 0) return PageResult.empty();
        return selectPage(query, new LambdaQueryWrapperX<DeviceDO>()
                .eq(DeviceDO::getTenantId, query.getTenantId())
                .likeIfPresent(DeviceDO::getSn, query.getSn())
                .likeIfPresent(DeviceDO::getName, query.getName())
                .likeIfPresent(DeviceDO::getProductModel, query.getProductModel())
                .likeIfPresent(DeviceDO::getContractNo, query.getContractNo())
                .and(w -> {
                    w.eq(DeviceDO::getProjectId, query.getProjectId());
                    if (query.getContractNumbers() != null && !query.getContractNumbers().isEmpty()) {
                        w.or().in(DeviceDO::getContractNo, query.getContractNumbers());
                    }
                })
                .select(DeviceDO::getId, DeviceDO::getSn, DeviceDO::getName, DeviceDO::getProductCode,
                        DeviceDO::getProductModel, DeviceDO::getContractNo, DeviceDO::getProjectId,
                        DeviceDO::getCustomerId, DeviceDO::getStatus)
                .orderByDesc(DeviceDO::getId));
    }

    List<DeviceDO> selectSelectionForUpdate(@Param("query") ProjectDeviceSelectionQuery query);
}
