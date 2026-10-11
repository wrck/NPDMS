package cn.iocoder.yudao.module.pms.asset.api.device;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.service.configurationlog.DeviceLogArchiveService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class DeviceConfigurationLogImportApiImpl implements DeviceConfigurationLogImportApi {
    private final DeviceLogArchiveService archives;

    @Override
    public Result importLog(Command command) {
        if (command == null) throw new IllegalArgumentException("日志导入请求不能为空");
        requireActor(command.tenantId(), command.actorId());
        var result = archives.upload(command.projectId(), command.operationId(), command.fileName(), command.content());
        return new Result(result.id(), result.outcome(), result.rejectionReason(), result.files().stream()
                .filter(file -> file.deviceId() != null)
                .map(file -> new File(file.id(), file.deviceId(), file.serialNumber(), file.available())).toList());
    }

    @Override
    public void validateFile(FileQuery query) {
        if (query == null) throw new IllegalArgumentException("日志文件引用不能为空");
        requireActor(query.tenantId(), query.actorId());
        boolean available = archives.history(query.projectId(), query.deviceId()).stream()
                .anyMatch(file -> Objects.equals(query.fileId(), file.id()) && file.available());
        if (!available) throw new IllegalStateException("配置日志文件不可用，请重新上传有效日志");
    }

    private void requireActor(Long tenant, Long actor) {
        if (tenant == null || actor == null || !Objects.equals(tenant, TenantContextHolder.getRequiredTenantId())
                || !Objects.equals(actor, SecurityFrameworkUtils.getLoginUserId()))
            throw new IllegalArgumentException("设备日志调用身份与当前会话不符");
    }
}
