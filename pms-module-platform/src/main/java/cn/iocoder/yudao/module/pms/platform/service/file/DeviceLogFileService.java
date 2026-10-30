package cn.iocoder.yudao.module.pms.platform.service.file;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.infra.api.file.FileStorageContentApi;
import cn.iocoder.yudao.module.pms.platform.api.collection.CollectionSourceAdapter;
import cn.iocoder.yudao.module.pms.platform.api.file.*;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.*;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.*;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileVersionMapper;
import cn.iocoder.yudao.module.pms.platform.service.delivery.DeliveryMaterialService;
import cn.iocoder.yudao.module.pms.platform.service.file.command.*;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Reuses native file validation, scans, receipts, reference policies and immutable material registration. */
@Service @RequiredArgsConstructor
public class DeviceLogFileService implements DeviceLogFileApi {
    private static final int MAX_BYTES = 20 * 1024 * 1024;
    private final CollectionTaskMapper tasks;
    private final CollectionRequestMapper requests;
    private final ObjectProvider<CollectionSourceAdapter> sources;
    private final FileVersionMapper versions;
    private final FileStorageContentApi storage;
    private final FileBusinessObjectPolicyRegistry policies;
    private final FileUploadApplicationService uploads;
    private final DeliveryMaterialService materials;
    private final PermissionApi permissions;

    @Override public byte[] readCollection(Origin origin) {
        authorizeOrigin(origin);
        var version = versions.selectByTenantAndId(origin.tenantId(), origin.fileVersionId());
        if (version == null || !"AVAILABLE".equals(version.getAvailabilityStatusCode())
                || !Set.of("PASSED", "SKIPPED").contains(version.getScanStatusCode())
                || version.getSizeBytes() == null || version.getSizeBytes() < 1 || version.getSizeBytes() > MAX_BYTES)
            throw new IllegalStateException("采集日志文件不可用");
        return storage.read(new FileStorageContentApi.Query(origin.tenantId(),version.getInfraFileId(),
                version.getSizeBytes(),version.getSha256(),MAX_BYTES));
    }

    @Override @Transactional(rollbackFor=Exception.class)
    public NativeGeneratedFileApi.RegisteredFile store(Command command) {
        if (command == null || !Objects.equals(command.tenantId(), TenantContextHolder.getRequiredTenantId())
                || command.actorId() == null || command.logFileId() == null)
            throw new IllegalArgumentException("设备日志文件范围无效");
        if (command.origin() == null) {
            if (!Objects.equals(command.actorId(), SecurityFrameworkUtils.getLoginUserId())
                    || !permissions.hasAnyPermissions(command.actorId(), "pms:file:upload"))
                throw new IllegalStateException("设备日志上传权限不足");
        } else {
            if (!command.tenantId().equals(command.origin().tenantId()) || !command.actorId().equals(command.origin().actorId()))
                throw new IllegalArgumentException("设备日志来源不一致");
            authorizeOrigin(command.origin());
        }
        var policyQuery = new NativeGeneratedFilePolicyQuery(command.tenantId(), command.actorId(), "AST",
                "DeviceConfigurationLogFile", command.logFileId(), 1L, "DEVICE_CONFIGURATION_LOG", null);
        var policy = policies.lockNativeGeneratedFile(policyQuery);
        byte[] bytes = command.content();
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) throw new IllegalArgumentException("日志文件大小无效");
        String digest = org.apache.commons.codec.digest.DigestUtils.sha256Hex(bytes);
        var initialized = uploads.initializeAuthorized(new FileUploadInitializeCommand(command.tenantId(), command.actorId(),
                command.operationId(), "CREATE_ARTIFACT", null, null, "AST", "DeviceConfigurationLogFile",
                command.logFileId().toString(), "DEVICE_CONFIGURATION_LOG", "device-log", command.fileName(),
                "DEVICE_CONFIGURATION_LOG", (long)bytes.length, "text/plain", digest), policy);
        policy = policies.lockNativeGeneratedFile(new NativeGeneratedFilePolicyQuery(command.tenantId(), command.actorId(),
                "AST", "DeviceConfigurationLogFile", command.logFileId(), 1L, "DEVICE_CONFIGURATION_LOG", policy.scopeVersion()));
        var completed = uploads.completeAuthorized(new FileUploadCompleteCommand(command.tenantId(), command.actorId(),
                command.operationId(), initialized.artifactId(), initialized.sessionId(), null, digest), bytes, policy);
        var material = materials.registerNativeGeneratedDocument(completed.referenceId());
        return new NativeGeneratedFileApi.RegisteredFile(material.getId(), completed.referenceId(), completed.artifactId(),
                completed.versionNo(), completed.sha256(), command.fileName());
    }

    private void authorizeOrigin(Origin origin) {
        if (origin == null || !Objects.equals(origin.tenantId(), TenantContextHolder.getRequiredTenantId()) || origin.actorId() == null
                || origin.projectId()==null || origin.deviceId()==null || origin.platformTaskId()==null
                || origin.resultVersion()==null || origin.resultVersion()<=0 || origin.fileVersionId()==null)
            throw new IllegalArgumentException("采集日志来源无效");
        var task = tasks.selectByTenantAndPlatformTaskId(origin.tenantId(), origin.platformTaskId());
        var request = requests.findByTask(origin.tenantId(), origin.platformTaskId());
        if (task == null || request == null || !"configuration".equals(request.getEntry())
                || !"IMP".equals(task.getSourceContext()) || !"Configuration".equals(task.getSourceObjectType())
                || !Set.of("RESULT_AVAILABLE", "COMPLETED").contains(task.getStatus())
                || !Set.of("SUCCESS", "SUCCEEDED").contains(task.getExternalStatus())
                || !Objects.equals(origin.resultVersion(), task.getResultVersion()) || !Objects.equals(origin.fileVersionId(), task.getFileVersionId())
                || !Objects.equals(origin.actorId(), request.getActorId()) || !origin.projectId().toString().equals(task.getProjectId())
                || !origin.deviceId().toString().equals(task.getDeviceId()) || !request.getObjectId().toString().equals(task.getSourceObjectId()))
            throw new IllegalStateException("采集日志来源或结果已变化");
        var matches = sources.orderedStream().filter(s -> s.entry().equals("configuration")).toList();
        if (matches.size() != 1) throw new IllegalStateException("采集来源授权入口不可用");
        var source = matches.getFirst().authorize(CollectionSourceAdapter.AuthorizationQuery.builder()
                .tenantId(origin.tenantId())
                .actorId(origin.actorId())
                .objectId(request.getObjectId())
                .deviceId(origin.deviceId())
                .access(CollectionSourceAdapter.Access.CONSUME)
                .expectedVersion(null)
                .build());
        if (!Objects.equals(source.projectId(), origin.projectId()) || !Objects.equals(source.deviceId(), origin.deviceId()))
            throw new IllegalStateException("采集记录设备归属已变化");
    }
}
