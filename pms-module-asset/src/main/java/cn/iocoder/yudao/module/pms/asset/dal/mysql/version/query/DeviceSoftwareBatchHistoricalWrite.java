package cn.iocoder.yudao.module.pms.asset.dal.mysql.version.query;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.version.DeviceSoftwareChangeBatchDO;
/** Explicit historical write: the framework must not fill missing source actors. */
public record DeviceSoftwareBatchHistoricalWrite(DeviceSoftwareChangeBatchDO values) {}
