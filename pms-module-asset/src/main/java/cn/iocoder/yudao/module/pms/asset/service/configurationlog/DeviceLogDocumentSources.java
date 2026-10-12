package cn.iocoder.yudao.module.pms.asset.service.configurationlog;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.configurationlog.DeviceLogFileMapper;
import cn.iocoder.yudao.module.pms.platform.api.file.FileDocumentSourceProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.*;

@Component @RequiredArgsConstructor
public class DeviceLogDocumentSources implements FileDocumentSourceProvider {
    public static final String SOURCE = "AST.DEVICE_CONFIGURATION_LOG";
    private final DeviceLogFileMapper files;
    @Override public List<Descriptor> descriptors() { return List.of(new Descriptor(SOURCE, "设备配置日志（按设备归档）")); }
    @Override public Scope resolve(Long tenant, String owner, String type, String objectId, String purpose) {
        if (!Objects.equals(tenant, TenantContextHolder.getRequiredTenantId()) || !"AST".equals(owner)
                || !DeviceLogFilePolicyProvider.OBJECT.equals(type) || !DeviceLogFilePolicyProvider.PURPOSE.equals(purpose)) return null;
        Long id;
        try { id = Long.valueOf(objectId); } catch (RuntimeException failure) { return null; }
        var row = files.selectById(id);
        if (row == null || !tenant.equals(row.getTenantId())) return null;
        String source = row.getDeviceId() == null ? "AST.DEVICE_CONFIGURATION_LOG_SOURCE" : SOURCE;
        return new Scope(row.getProjectId(), source, "AST", "device-log-file", row.getId(), row.getDeviceVersion());
    }
}
