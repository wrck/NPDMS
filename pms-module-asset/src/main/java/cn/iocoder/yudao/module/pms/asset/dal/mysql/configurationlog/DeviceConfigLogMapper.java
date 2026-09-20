package cn.iocoder.yudao.module.pms.asset.dal.mysql.configurationlog;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.asset.controller.admin.device.vo.DeviceConfigurationLogPageReqVO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.configurationlog.DeviceConfigLogDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.configurationlog.query.DeviceConfigurationLogListQuery;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface DeviceConfigLogMapper extends BaseMapperX<DeviceConfigLogDO> {

    default List<DeviceConfigLogDO> selectList(DeviceConfigurationLogListQuery query) {
        return selectList(new LambdaQueryWrapperX<DeviceConfigLogDO>()
                .eq(DeviceConfigLogDO::getTenantId, query.tenantId())
                .eq(DeviceConfigLogDO::getDeviceId, query.deviceId())
                .orderByDesc(DeviceConfigLogDO::getCollectedAt)
                .orderByDesc(DeviceConfigLogDO::getId));
    }

    default PageResult<DeviceConfigLogDO> selectPage(DeviceConfigurationLogPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<DeviceConfigLogDO>()
                .eqIfPresent(DeviceConfigLogDO::getDeviceId, reqVO.getDeviceId())
                .eqIfPresent(DeviceConfigLogDO::getConfigType, reqVO.getConfigType())
                .likeIfPresent(DeviceConfigLogDO::getSourceSystem, reqVO.getSourceSystem())
                .eqIfPresent(DeviceConfigLogDO::getFileHash, reqVO.getFileHash())
                .betweenIfPresent(DeviceConfigLogDO::getCollectedAt, reqVO.getCollectedAt())
                .orderByDesc(DeviceConfigLogDO::getCollectedAt));
    }

    /**
     * 项目维度分页；deviceIds 为调用方已解析的项目设备集合（空集合须由调用方先行短路）。
     */
    default PageResult<DeviceConfigLogDO> selectPageByDevices(DeviceConfigurationLogPageReqVO reqVO,
                                                              java.util.Collection<Long> deviceIds) {
        return selectPage(reqVO, new LambdaQueryWrapperX<DeviceConfigLogDO>()
                .eqIfPresent(DeviceConfigLogDO::getDeviceId, reqVO.getDeviceId())
                .in(DeviceConfigLogDO::getDeviceId, deviceIds)
                .eqIfPresent(DeviceConfigLogDO::getConfigType, reqVO.getConfigType())
                .likeIfPresent(DeviceConfigLogDO::getSourceSystem, reqVO.getSourceSystem())
                .eqIfPresent(DeviceConfigLogDO::getFileHash, reqVO.getFileHash())
                .betweenIfPresent(DeviceConfigLogDO::getCollectedAt, reqVO.getCollectedAt())
                .orderByDesc(DeviceConfigLogDO::getCollectedAt));
    }
}
