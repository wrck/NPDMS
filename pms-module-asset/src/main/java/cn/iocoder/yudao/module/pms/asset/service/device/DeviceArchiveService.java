package cn.iocoder.yudao.module.pms.asset.service.device;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.asset.controller.admin.device.vo.DeviceArchivePageReqVO;
import cn.iocoder.yudao.module.pms.asset.controller.admin.device.vo.DeviceArchiveSaveReqVO;
import cn.iocoder.yudao.module.pms.asset.controller.admin.device.vo.DeviceArchiveStatusChangeReqVO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceVersionDO;

import java.util.List;

/**
 * 设备档案管理 Service（ast_device/ast_device_version 承载，自 pms_equipment 旧链承接）。
 */
public interface DeviceArchiveService {

    Long createDevice(DeviceArchiveSaveReqVO createReqVO);

    void updateDevice(DeviceArchiveSaveReqVO updateReqVO);

    void deleteDevice(Long id);

    void changeDeviceStatus(Long id, DeviceArchiveStatusChangeReqVO reqVO);

    PageResult<DeviceDO> getDeviceArchivePage(DeviceArchivePageReqVO pageReqVO);

    DeviceDO getDeviceArchiveRecord(Long id);

    List<DeviceVersionDO> getDeviceVersionList(Long deviceId);
}
