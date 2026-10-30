package cn.iocoder.yudao.module.pms.asset.api.device;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceConfigLogRecordCommand;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.configurationlog.DeviceConfigLogDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.configurationlog.DeviceConfigLogMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.asset.enums.ErrorCodeConstants.AST_EQUIPMENT_NOT_EXISTS;

@Service
public class DeviceConfigLogRecordApiImpl implements DeviceConfigLogRecordApi {

    private final DeviceMapper deviceMapper;
    private final DeviceConfigLogMapper configLogMapper;

    public DeviceConfigLogRecordApiImpl(DeviceMapper deviceMapper, DeviceConfigLogMapper configLogMapper) {
        this.deviceMapper = deviceMapper;
        this.configLogMapper = configLogMapper;
    }

    @Override
    public Long recordConfigLog(DeviceConfigLogRecordCommand command) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        DeviceDO device = deviceMapper.selectByTenantAndId(tenantId, command.deviceId());
        if (device == null) {
            throw exception(AST_EQUIPMENT_NOT_EXISTS);
        }
        DeviceConfigLogDO configLog = new DeviceConfigLogDO();
        configLog.setDeviceId(command.deviceId());
        configLog.setConfigType(command.configType());
        configLog.setSourceSystem(command.sourceSystem());
        configLog.setCollectedAt(command.collectedAt() != null ? command.collectedAt() : LocalDateTime.now());
        configLog.setFileUrl(command.fileUrl());
        configLog.setFileHash(command.fileHash());
        configLog.setRemark(command.remark());
        configLog.setVersion(0);
        configLogMapper.insert(configLog);
        return configLog.getId();
    }
}
