package cn.iocoder.yudao.module.pms.asset.service.configurationlog;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.job.TenantJob;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.configurationlog.DeviceLogParseJobMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.configurationlog.query.DeviceLogParsePendingQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
@ConditionalOnProperty(prefix="pms.integration.device-ops",name="enabled",havingValue="true")
public class DeviceArchivedLogParsingJob {
    private final DeviceLogParseJobMapper jobs;
    private final DeviceArchivedLogParsingService parsing;
    @Scheduled(fixedDelay=5000,initialDelay=5000) @TenantJob
    public String reconcile() {
        int completed=0,waiting=0;
        for (var job:jobs.pending(new DeviceLogParsePendingQuery(TenantContextHolder.getRequiredTenantId(),50))) {
            try {
                var view=parsing.reconcile(job.getId());
                if (view.deviceProjection()!=null) completed++;else waiting++;
            } catch (RuntimeException incomplete) {
                // Keep the job retryable; its UI query reports authorization/source/projection errors.
                waiting++;
            }
        }
        return "设备日志解析关联完成 "+completed+"，待处理 "+waiting;
    }
}
