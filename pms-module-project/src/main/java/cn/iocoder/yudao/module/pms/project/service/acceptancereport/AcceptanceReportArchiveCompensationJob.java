package cn.iocoder.yudao.module.pms.project.service.acceptancereport;

import cn.iocoder.yudao.framework.quartz.core.handler.JobHandler;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.job.TenantJob;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.acceptancereport.ProjectDeliverableSourceVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.ProjectDeliverableSourceVersionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.query.PendingArchiveSourceQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class AcceptanceReportArchiveCompensationJob implements JobHandler {

    private static final int BATCH_SIZE = 20;
    private final ProjectDeliverableSourceVersionMapper sourceMapper;
    private final AcceptanceReportArchiveCompensationService compensationService;

    @Override
    @TenantJob
    public String execute(String param) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        List<ProjectDeliverableSourceVersionDO> sources = sourceMapper.selectPendingArchive(
                new PendingArchiveSourceQuery(tenantId, BATCH_SIZE));
        int archived = 0;
        int pending = 0;
        for (ProjectDeliverableSourceVersionDO source : sources) {
            try {
                compensationService.archive(tenantId, source.getId());
                archived++;
            } catch (RuntimeException failure) {
                compensationService.recordFailure(tenantId, source.getId(), "ARCHIVE_FAILED");
                pending++;
                log.warn("[execute][报告来源({})归档失败，保留待补偿]", source.getId(), failure);
            }
        }
        return String.format("报告归档成功 %d 条，继续待补偿 %d 条", archived, pending);
    }
}
