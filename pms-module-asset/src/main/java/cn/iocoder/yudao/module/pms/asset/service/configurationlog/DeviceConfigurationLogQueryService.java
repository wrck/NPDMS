package cn.iocoder.yudao.module.pms.asset.service.configurationlog;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.equipmentconfiglog.EquipmentConfigLogMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.equipmentconfiglog.query.DeviceConfigurationLogListQuery;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.springframework.stereotype.Service;

import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.asset.enums.ErrorCodeConstants.AST_EQUIPMENT_NOT_EXISTS;

@Service
public class DeviceConfigurationLogQueryService {

    public static final String DOWNLOAD_PERMISSION = "pms:device-configuration-log:download";

    private final DeviceMapper deviceMapper;
    private final EquipmentConfigLogMapper configurationLogMapper;
    private final PermissionApi permissionApi;

    public DeviceConfigurationLogQueryService(
            DeviceMapper deviceMapper,
            EquipmentConfigLogMapper configurationLogMapper,
            PermissionApi permissionApi) {
        this.deviceMapper = deviceMapper;
        this.configurationLogMapper = configurationLogMapper;
        this.permissionApi = permissionApi;
    }

    public List<DeviceConfigurationLogMetadata> getList(Long tenantId, Long userId, Long deviceId) {
        Long currentTenantId = TenantContextHolder.getTenantId();
        if (currentTenantId == null) {
            var loginUser = SecurityFrameworkUtils.getLoginUser();
            currentTenantId = loginUser == null ? null : loginUser.getTenantId();
        }
        if (currentTenantId == null || !currentTenantId.equals(tenantId)) {
            throw exception(AST_EQUIPMENT_NOT_EXISTS);
        }
        DeviceDO device = deviceMapper.selectByTenantAndId(currentTenantId, deviceId);
        if (device == null) {
            throw exception(AST_EQUIPMENT_NOT_EXISTS);
        }
        boolean downloadable = userId != null && permissionApi.hasAnyPermissions(userId, DOWNLOAD_PERMISSION);
        return configurationLogMapper.selectList(new DeviceConfigurationLogListQuery(currentTenantId, deviceId))
                .stream()
                .map(log -> new DeviceConfigurationLogMetadata(
                        log.getId(), log.getConfigType(), log.getSourceSystem(), log.getCollectedAt(),
                        log.getFileHash(), log.getRemark(), downloadable && hasFile(log.getFileUrl())))
                .toList();
    }

    private static boolean hasFile(String fileUrl) {
        return fileUrl != null && !fileUrl.isBlank();
    }
}
