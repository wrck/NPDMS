package cn.iocoder.yudao.module.pms.project.service.satisfaction;

import cn.iocoder.yudao.framework.quartz.core.handler.JobHandler;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.job.TenantJob;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.acceptancereport.ProjectDeliverableSourceVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.ProjectDeliverableSourceVersionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.query.PendingArchiveSourceTypeQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
@Slf4j
@RequiredArgsConstructor
public class SatisfactionResultArchiveCompensationJob implements JobHandler {
    private static final int BATCH_SIZE = 20;
    private final ProjectDeliverableSourceVersionMapper sourceMapper;
    private final SatisfactionResultArchiveCompensationService compensationService;
    private final Environment environment;

    @Override
    @TenantJob
    public String execute(String param) {
        if (TenantContextHolder.getTenantId() != null) return archivePending();
        if (environment.getProperty("yudao.tenant.enable", Boolean.class, true)) {
            TenantContextHolder.getRequiredTenantId();
        }
        String[] result = new String[1];
        TenantUtils.execute(0L, () -> result[0] = archivePending());
        return result[0];
    }

    private String archivePending() {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        List<ProjectDeliverableSourceVersionDO> sources = sourceMapper.selectPendingArchiveBySourceType(
                new PendingArchiveSourceTypeQuery(tenantId, "SatisfactionResult",
                        Set.of("CURRENT", "SUPERSEDED", "REVOKED"), BATCH_SIZE));
        int archived = 0;
        int pending = 0;
        for (ProjectDeliverableSourceVersionDO source : sources) {
            try {
                compensationService.archive(tenantId, source.getId());
                archived++;
            } catch (RuntimeException failure) {
                compensationService.recordFailure(tenantId, source.getId(), "ARCHIVE_FAILED");
                pending++;
                log.warn("[execute][满意度来源({})归档失败，保留待补偿]", source.getId(), failure);
            }
        }
        return String.format("满意度归档成功 %d 条，继续待补偿 %d 条", archived, pending);
    }
}
