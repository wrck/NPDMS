package cn.iocoder.yudao.module.pms.platform.service.file;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.collection.CollectionLogAccessApi;
import cn.iocoder.yudao.module.pms.platform.api.file.AuthenticatedFileContentApi;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionQuery;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
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
    private final AuthenticatedFileContentApi content;
    @Override public byte[] read(ContentQuery query) {
        if (query == null || !Objects.equals(query.tenantId(), TenantContextHolder.getRequiredTenantId())
                || !Objects.equals(query.actorId(), SecurityFrameworkUtils.getLoginUserId()) || query.actorId() == null)
            throw new IllegalArgumentException("采集日志读取身份不匹配");
        var task = tasks.selectByTenantAndPlatformTaskId(query.tenantId(), query.platformTaskId());
        if (task == null || query.resultVersion() == null || query.fileVersionId() == null
                || !Objects.equals(query.resultVersion(), task.getResultVersion())
                || !Objects.equals(query.fileVersionId(), task.getFileVersionId()))
            throw new IllegalStateException("采集日志结果版本已变化");
        var version = versions.selectByTenantAndId(query.tenantId(), query.fileVersionId());
        if (version == null) throw new IllegalStateException("采集日志不可用");
        return content.read(new AuthenticatedFileContentApi.ReadCommand(new FileArtifactVersionQuery(
                version.getArtifactId(), version.getVersionNo(), "PLT", "CollectionTask", query.platformTaskId(),
                "COLLECTION_LOG", "result-" + query.resultVersion(), "DOWNLOAD"), 20L * 1024 * 1024));
    }
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
