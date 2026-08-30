package cn.iocoder.yudao.module.pms.platform.service.export;

import cn.iocoder.yudao.framework.quartz.core.handler.JobHandler;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.job.TenantJob;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExportTaskExecutionJob implements JobHandler {

    private final ExportTaskExecutionService service;

    @Override
    @TenantJob
    public String execute(String param) {
        int count = service.executeRequested(TenantContextHolder.getRequiredTenantId());
        return "统一导出执行 " + count + " 条";
    }
}
