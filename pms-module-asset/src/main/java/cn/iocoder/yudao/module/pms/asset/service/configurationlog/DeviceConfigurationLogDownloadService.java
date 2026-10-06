package cn.iocoder.yudao.module.pms.asset.service.configurationlog;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.NativeConfigurationFileLocator;
import cn.iocoder.yudao.module.pms.platform.api.file.NativeGeneratedFileApi;
import cn.iocoder.yudao.module.pms.platform.api.file.FileEvidenceApi;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryMaterialApi;
import jakarta.annotation.Resource;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.configurationlog.DeviceDownloadGrantDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.configurationlog.DeviceConfigLogDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.configurationlog.DeviceDownloadGrantMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.configurationlog.DeviceConfigLogMapper;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.pms.asset.service.security.DeviceAccessScopeService;
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

    @Resource private NativeGeneratedFileApi nativeFiles;
    @Resource private FileEvidenceApi evidence;
    @Resource private PlatformDeliveryMaterialApi materials;
    private final DeviceMapper deviceMapper;
    private final DeviceConfigLogMapper configurationLogMapper;
    private final DeviceDownloadGrantMapper grantMapper;
    private final PermissionApi permissionApi;
    private final FileApi fileApi;
    private final DeviceConfigurationFileContentClient contentClient;
    private final DeviceAccessScopeService accessScopeService;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    public DeviceConfigurationLogDownloadService(
            DeviceMapper deviceMapper,
            DeviceConfigLogMapper configurationLogMapper,
            DeviceDownloadGrantMapper grantMapper,
            PermissionApi permissionApi,
            FileApi fileApi,
            DeviceConfigurationFileContentClient contentClient,
            DeviceAccessScopeService accessScopeService) {
        this(deviceMapper, configurationLogMapper, grantMapper, permissionApi, fileApi, contentClient,
                accessScopeService, Clock.systemUTC());
    }

    DeviceConfigurationLogDownloadService(
            DeviceMapper deviceMapper,
            DeviceConfigLogMapper configurationLogMapper,
            DeviceDownloadGrantMapper grantMapper,
            PermissionApi permissionApi,
            FileApi fileApi,
            DeviceConfigurationFileContentClient contentClient,
            DeviceAccessScopeService accessScopeService,
            Clock clock) {
        this.deviceMapper = deviceMapper;
        this.configurationLogMapper = configurationLogMapper;
        this.grantMapper = grantMapper;
        this.permissionApi = permissionApi;
        this.fileApi = fileApi;
        this.contentClient = contentClient;
        this.accessScopeService = accessScopeService;
        this.clock = clock;
    }

    @Transactional
    public DeviceConfigurationDownloadGrant issueGrant(Long tenantId, Long userId, Long deviceId, Long logId) {
        assertTenant(tenantId);
        assertDownloadPermission(userId);
        accessScopeService.assertVisible(tenantId, userId, deviceId);
        DeviceDO device = requireDevice(tenantId, deviceId);
        DeviceConfigLogDO log = requireLog(tenantId, deviceId, logId);
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
        accessScopeService.assertVisible(tenantId, userId, deviceId);
        DeviceDO device = requireDevice(tenantId, deviceId);
        if (!device.getSn().equals(grant.getDeviceSn())) {
            throw exception(AST_DEVICE_CONFIGURATION_LOG_DOWNLOAD_INVALID);
        }
        DeviceConfigLogDO log = requireLog(tenantId, deviceId, grant.getConfigurationLogId());
        requireFile(log);
        if (grantMapper.consume(tenantId, tokenDigest, userId, now) != 1) {
            throw exception(AST_DEVICE_CONFIGURATION_LOG_DOWNLOAD_INVALID);
        }
        String internalUrl = resolveDownloadUrl(log);
        return new DeviceConfigurationFileContent(
                "configuration-log-" + log.getId() + ".txt",
                contentClient.open(internalUrl));
    }

    private String resolveDownloadUrl(DeviceConfigLogDO log) {
        NativeConfigurationFileLocator locator;
        try { locator=NativeConfigurationFileLocator.parse(log.getFileUrl()); }
        catch (IllegalArgumentException invalid) { throw exception(AST_DEVICE_CONFIGURATION_LOG_DOWNLOAD_INVALID); }
        if(locator==null)return fileApi.presignGetUrl(log.getFileUrl(),PRESIGNED_URL_TTL_SECONDS);
        var material=materials.listByEntityAndType("IMP","configuration",locator.configurationId(),"IMP.CONFIGURATION_LOG").stream()
                .filter(row->locator.materialId().equals(row.id()) && "FILE".equals(row.materialKind())
                        && PlatformDeliveryMaterialApi.STATUS_ACTIVE.equals(row.status())).findFirst().orElse(null);
        if(material==null)throw exception(AST_DEVICE_CONFIGURATION_LOG_DOWNLOAD_INVALID);
        // Native download locks its actual Owner before file rows, matching native writes/withdrawals.
        // Keep the URL internal until material state and frozen evidence are checked under that lock.
        String internalUrl=nativeFiles.requestDownload("IMP","configuration",locator.configurationId(),locator.materialId());
        boolean stillActive=materials.listByEntityAndType("IMP","configuration",locator.configurationId(),"IMP.CONFIGURATION_LOG").stream()
                .anyMatch(row->locator.materialId().equals(row.id()) && "FILE".equals(row.materialKind())
                        && PlatformDeliveryMaterialApi.STATUS_ACTIVE.equals(row.status())
                        && java.util.Objects.equals(material.fileArtifactId(),row.fileArtifactId())
                        && java.util.Objects.equals(material.fileVersionNo(),row.fileVersionNo())
                        && java.util.Objects.equals(material.fileSha256(),row.fileSha256()));
        if(!stillActive)throw exception(AST_DEVICE_CONFIGURATION_LOG_DOWNLOAD_INVALID);
        var document=evidence.inspectDocumentByArtifact(log.getTenantId(),material.fileArtifactId(),material.fileVersionNo());
        if(document==null || !document.available() || !"IMP".equals(document.ownerContext())
                || !"configuration".equals(document.objectType()) || !locator.configurationId().toString().equals(document.objectId())
                || !"CONFIGURATION_LOG".equals(document.purposeCode()) || !java.util.Objects.equals(material.fileSha256(),document.sha256()))
            throw exception(AST_DEVICE_CONFIGURATION_LOG_DOWNLOAD_INVALID);
        var fact=evidence.lockAndRevalidate(new FileEvidenceApi.Query(log.getTenantId(),material.fileArtifactId(),material.fileVersionNo(),
                document.ownerContext(),document.objectType(),document.objectId(),document.purposeCode(),document.referenceKey(),material.fileSha256()));
        if(fact==null || !fact.valid())throw exception(AST_DEVICE_CONFIGURATION_LOG_DOWNLOAD_INVALID);
        return internalUrl;
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

    private DeviceConfigLogDO requireLog(Long tenantId, Long deviceId, Long logId) {
        DeviceConfigLogDO log = configurationLogMapper.selectById(logId);
        if (log == null || !tenantId.equals(log.getTenantId()) || !deviceId.equals(log.getDeviceId())) {
            throw exception(AST_EQUIPMENT_CONFIG_LOG_NOT_EXISTS);
        }
        return log;
    }

    private void requireFile(DeviceConfigLogDO log) {
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
