package cn.iocoder.yudao.module.pms.asset.api.device;

import cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceConfigLogRecordCommand;

/**
 * 设备配置日志记录 API（AST Owner）。
 * <p>
 * 供工程实施等业务模块在手动上传配置 Log 后写入设备配置日志档案（ast_device_config_log），
 * 使 EQP-01 设备工作台与配置日志页面可以看到真实记录。
 * 只新增记录，不修改、不删除既有日志，不回填历史数据。
 */
public interface DeviceConfigLogRecordApi {

    /**
     * 记录一条设备配置日志。
     *
     * @return 日志记录编号
     * @throws cn.iocoder.yudao.framework.common.exception.ServiceException 设备不存在（当前租户）时抛出
     */
    Long recordConfigLog(DeviceConfigLogRecordCommand command);
}
