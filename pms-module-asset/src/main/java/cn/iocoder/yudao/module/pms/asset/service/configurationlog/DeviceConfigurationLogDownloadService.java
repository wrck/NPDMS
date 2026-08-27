package cn.iocoder.yudao.module.pms.asset.service.configurationlog;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.configurationlog.DeviceDownloadGrantDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.equipmentconfiglog.EquipmentConfigLogDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.configurationlog.DeviceDownloadGrantMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.equipmentconfiglog.EquipmentConfigLogMapper;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.asset.enums.ErrorCodeConstants.AST_DEVICE_CONFIGURATION_LOG_DOWNLOAD_FORBIDDEN;
import static cn.iocoder.yudao.module.pms.asset.enums.ErrorCodeConstants.AST_DEVICE_CONFIGURATION_LOG_DOWNLOAD_INVALID;
import static cn.iocoder.yudao.module.pms.asset.enums.ErrorCodeConstants.AST_EQUIPMENT_CONFIG_LOG_NOT_EXISTS;
import static cn.iocoder.yudao.module.pms.asset.enums.ErrorCodeConstants.AST_EQUIPMENT_NOT_EXISTS;

@Service
public class DeviceConfigurationLogDownloadService {

    public static final String DOWNLOAD_PERMISSION = DeviceConfigurationLogQueryService.DOWNLOAD_PERMISSION;
    private static final int GRANT_TTL_SECONDS = 300;
    private static final int PRESIGNED_URL_TTL_SECONDS = 60;

    private final DeviceMapper deviceMapper;
    private final EquipmentConfigLogMapper configurationLogMapper;
    private final DeviceDownloadGrantMapper grantMapper;
    private final PermissionApi permissionApi;
    private final FileApi fileApi;
    private final DeviceConfigurationFileContentClient contentClient;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    public DeviceConfigurationLogDownloadService(
            DeviceMapper deviceMapper,
            EquipmentConfigLogMapper configurationLogMapper,
            DeviceDownloadGrantMapper grantMapper,
            PermissionApi permissionApi,
            FileApi fileApi,
            DeviceConfigurationFileContentClient contentClient) {
        this(deviceMapper, configurationLogMapper, grantMapper, permissionApi, fileApi, contentClient, Clock.systemUTC());
    }

    DeviceConfigurationLogDownloadService(
            DeviceMapper deviceMapper,
            EquipmentConfigLogMapper configurationLogMapper,
            DeviceDownloadGrantMapper grantMapper,
            PermissionApi permissionApi,
            FileApi fileApi,
            DeviceConfigurationFileContentClient contentClient,
            Clock clock) {
        this.deviceMapper = deviceMapper;
        this.configurationLogMapper = configurationLogMapper;
        this.grantMapper = grantMapper;
        this.permissionApi = permissionApi;
        this.fileApi = fileApi;
        this.contentClient = contentClient;
        this.clock = clock;
    }

    @Transactional
    public DeviceConfigurationDownloadGrant issueGrant(Long tenantId, Long userId, Long deviceId, Long logId) {
        assertTenant(tenantId);
        assertDownloadPermission(userId);
        DeviceDO device = requireDevice(tenantId, deviceId);
        EquipmentConfigLogDO log = requireLog(tenantId, deviceId, logId);
        requireFile(log);
        String rawToken = generateToken();
        LocalDateTime expiresAt = now().plusSeconds(GRANT_TTL_SECONDS);
        DeviceDownloadGrantDO grant = new DeviceDownloadGrantDO();
        grant.setTokenDigest(digest(rawToken));
        grant.setUserId(userId);
        grant.setDeviceSn(device.getSn());
        grant.setConfigurationLogId(logId);
        grant.setExpiresAt(expiresAt);
        grant.setTenantId(tenantId);
        grantMapper.insert(grant);
        return new DeviceConfigurationDownloadGrant(
                "/pms/asset/devices/" + deviceId + "/configuration-logs/download?token=" + rawToken,
                expiresAt);
    }

    @Transactional
    public DeviceConfigurationFileContent download(Long tenantId, Long userId, Long deviceId, String rawToken) {
        assertTenant(tenantId);
        String tokenDigest = digest(rawToken);
        DeviceDownloadGrantDO grant = grantMapper.selectByTokenDigest(tokenDigest);
        LocalDateTime now = now();
        if (grant == null || !tenantId.equals(grant.getTenantId()) || !grant.getUserId().equals(userId)
                || grant.getConsumedAt() != null || !grant.getExpiresAt().isAfter(now)) {
            throw exception(AST_DEVICE_CONFIGURATION_LOG_DOWNLOAD_INVALID);
        }
        assertDownloadPermission(userId);
        DeviceDO device = requireDevice(tenantId, deviceId);
        if (!device.getSn().equals(grant.getDeviceSn())) {
            throw exception(AST_DEVICE_CONFIGURATION_LOG_DOWNLOAD_INVALID);
        }
        EquipmentConfigLogDO log = requireLog(tenantId, deviceId, grant.getConfigurationLogId());
        requireFile(log);
        if (grantMapper.consume(tenantId, tokenDigest, userId, now) != 1) {
            throw exception(AST_DEVICE_CONFIGURATION_LOG_DOWNLOAD_INVALID);
        }
        String internalUrl = fileApi.presignGetUrl(log.getFileUrl(), PRESIGNED_URL_TTL_SECONDS);
        return new DeviceConfigurationFileContent(
                "configuration-log-" + log.getId() + ".txt",
                contentClient.open(internalUrl));
    }

    String digest(String token) {
        if (token == null || token.isBlank()) {
            throw exception(AST_DEVICE_CONFIGURATION_LOG_DOWNLOAD_INVALID);
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256摘要算法不可用", ex);
        }
    }

    private void assertTenant(Long tenantId) {
        if (!TenantContextHolder.getRequiredTenantId().equals(tenantId)) {
            throw exception(AST_EQUIPMENT_NOT_EXISTS);
        }
    }

    private void assertDownloadPermission(Long userId) {
        if (userId == null || !permissionApi.hasAnyPermissions(userId, DOWNLOAD_PERMISSION)) {
            throw exception(AST_DEVICE_CONFIGURATION_LOG_DOWNLOAD_FORBIDDEN);
        }
    }

    private DeviceDO requireDevice(Long tenantId, Long deviceId) {
        DeviceDO device = deviceMapper.selectByTenantAndId(tenantId, deviceId);
        if (device == null) {
            throw exception(AST_EQUIPMENT_NOT_EXISTS);
        }
        return device;
    }

    private EquipmentConfigLogDO requireLog(Long tenantId, Long deviceId, Long logId) {
        EquipmentConfigLogDO log = configurationLogMapper.selectById(logId);
        if (log == null || !tenantId.equals(log.getTenantId()) || !deviceId.equals(log.getEquipmentId())) {
            throw exception(AST_EQUIPMENT_CONFIG_LOG_NOT_EXISTS);
        }
        return log;
    }

    private void requireFile(EquipmentConfigLogDO log) {
        if (log.getFileUrl() == null || log.getFileUrl().isBlank()) {
            throw exception(AST_EQUIPMENT_CONFIG_LOG_NOT_EXISTS);
        }
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), clock.getZone());
    }
}
