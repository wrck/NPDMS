package cn.iocoder.yudao.module.pms.asset.service.warranty;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.warranty.DeviceWarrantyDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.warranty.DeviceWarrantyRecordDO;

public record DeviceWarrantyResult(
        DeviceWarrantyDO current,
        PageResult<DeviceWarrantyRecordDO> records) {
}
