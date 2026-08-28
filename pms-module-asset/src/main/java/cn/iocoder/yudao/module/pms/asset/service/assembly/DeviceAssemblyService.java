package cn.iocoder.yudao.module.pms.asset.service.assembly;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.assembly.DeviceAssemblyDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.assembly.DeviceAssemblyMapper;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.assembly.query.DeviceAssemblyPathQuery;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.assembly.query.DeviceAssemblySourceQuery;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.assembly.query.DeviceAssemblyTreeQuery;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper;
import cn.iocoder.yudao.module.pms.asset.service.assembly.command.ApplyDeviceAssemblyCommand;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.asset.enums.ErrorCodeConstants.AST_DEVICE_ASSEMBLY_COMMAND_INVALID;
import static cn.iocoder.yudao.module.pms.asset.enums.ErrorCodeConstants.AST_DEVICE_ASSEMBLY_CYCLE;
import static cn.iocoder.yudao.module.pms.asset.enums.ErrorCodeConstants.AST_DEVICE_ASSEMBLY_DEVICE_NOT_EXISTS;

@Service
public class DeviceAssemblyService {

    private final DeviceMapper deviceMapper;
    private final DeviceAssemblyMapper assemblyMapper;

    public DeviceAssemblyService(
            DeviceMapper deviceMapper,
            DeviceAssemblyMapper assemblyMapper) {
        this.deviceMapper = deviceMapper;
        this.assemblyMapper = assemblyMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public void apply(ApplyDeviceAssemblyCommand command) {
        validate(command);
        DeviceAssemblySourceQuery sourceQuery = new DeviceAssemblySourceQuery(
                command.tenantId(), command.sourceSystem(), command.sourceKey());
        if (assemblyMapper.existsBySource(sourceQuery)) {
            return;
        }
        if (deviceMapper.selectByTenantAndSn(command.tenantId(), command.parentDeviceSn()) == null
                || deviceMapper.selectByTenantAndSn(command.tenantId(), command.childDeviceSn()) == null) {
            throw exception(AST_DEVICE_ASSEMBLY_DEVICE_NOT_EXISTS);
        }
        if (command.parentDeviceSn().equals(command.childDeviceSn())
                || assemblyMapper.existsPath(new DeviceAssemblyPathQuery(
                        command.tenantId(), command.childDeviceSn(), command.parentDeviceSn()))) {
            throw exception(AST_DEVICE_ASSEMBLY_CYCLE);
        }
        assemblyMapper.closeCurrentByChild(
                command.tenantId(), command.childDeviceSn(), command.effectiveAt());
        assemblyMapper.closeCurrentByPosition(
                command.tenantId(), command.parentDeviceSn(),
                command.positionCode(), command.effectiveAt());
        assemblyMapper.insert(toEntity(command));
    }

    public List<DeviceAssemblyDO> getCurrentTree(Long tenantId, Long deviceId) {
        Long currentTenantId = TenantContextHolder.getTenantId();
        if (currentTenantId == null) {
            var loginUser = SecurityFrameworkUtils.getLoginUser();
            currentTenantId = loginUser == null ? null : loginUser.getTenantId();
        }
        if (currentTenantId == null || !currentTenantId.equals(tenantId)) {
            throw exception(AST_DEVICE_ASSEMBLY_DEVICE_NOT_EXISTS);
        }
        DeviceDO device = deviceMapper.selectByTenantAndId(currentTenantId, deviceId);
        if (device == null) {
            throw exception(AST_DEVICE_ASSEMBLY_DEVICE_NOT_EXISTS);
        }
        return assemblyMapper.selectCurrentTree(new DeviceAssemblyTreeQuery(currentTenantId, device.getSn()));
    }

    private void validate(ApplyDeviceAssemblyCommand command) {
        if (command == null
                || command.tenantId() == null
                || blank(command.parentDeviceSn())
                || blank(command.childDeviceSn())
                || blank(command.positionCode())
                || blank(command.assemblyType())
                || command.effectiveAt() == null
                || blank(command.sourceSystem())
                || blank(command.sourceKey())) {
            throw exception(AST_DEVICE_ASSEMBLY_COMMAND_INVALID);
        }
    }

    private DeviceAssemblyDO toEntity(ApplyDeviceAssemblyCommand command) {
        DeviceAssemblyDO entity = new DeviceAssemblyDO();
        entity.setTenantId(command.tenantId());
        entity.setParentDeviceSn(command.parentDeviceSn());
        entity.setChildDeviceSn(command.childDeviceSn());
        entity.setPositionCode(command.positionCode());
        entity.setAssemblyType(command.assemblyType());
        entity.setEffectiveFrom(command.effectiveAt());
        entity.setEvidenceRef(command.evidenceRef());
        entity.setSourceSystem(command.sourceSystem());
        entity.setSourceKey(command.sourceKey());
        entity.setSourceVersion(command.sourceVersion());
        entity.setVersion(0);
        entity.setCreator("");
        entity.setUpdater("");
        return entity;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
