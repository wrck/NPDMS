package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.module.infra.api.job.JobApi;
import org.quartz.Scheduler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DataSyncQuartzRegistrar implements ApplicationRunner {
    private final JobApi jobApi;
    private final ObjectProvider<Scheduler> scheduler;
    public void run(ApplicationArguments args) {
        if(scheduler.getIfAvailable()!=null)jobApi.syncEnabledJobByHandlerName("dataSyncDispatchJob");
    }
}

