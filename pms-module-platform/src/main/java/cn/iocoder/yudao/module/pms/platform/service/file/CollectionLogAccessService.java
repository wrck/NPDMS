package cn.iocoder.yudao.module.pms.platform.service.file;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.collection.CollectionLogAccessApi;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.CollectionTaskMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Objects;
@Service
@RequiredArgsConstructor
public class CollectionLogAccessService implements CollectionLogAccessApi {
    private final CollectionTaskMapper tasks;
    private final FileVersionMapper versions;
    private final FileAccessTicketService tickets;
    @Override public String download(Long tenantId, Long actorId, String taskId) {
        if (!Objects.equals(tenantId, TenantContextHolder.getRequiredTenantId())) throw new IllegalArgumentException("租户不匹配");
        var task = tasks.selectByTenantAndPlatformTaskId(tenantId, taskId);
        if (task == null || task.getFileVersionId() == null) throw new IllegalStateException("日志尚不可用");
        var version = versions.selectByTenantAndId(tenantId, task.getFileVersionId());
        if (version == null || !Objects.equals(tenantId, version.getTenantId())) throw new IllegalStateException("日志不可用");
        return tickets.create(new FileAccessTicketService.AccessCommand(tenantId, actorId, version.getArtifactId(),
                version.getVersionNo(), "DOWNLOAD", "PLT", "CollectionTask", taskId, "COLLECTION_LOG",
                "result-" + task.getResultVersion())).getShortLivedUrl();
    }
}
