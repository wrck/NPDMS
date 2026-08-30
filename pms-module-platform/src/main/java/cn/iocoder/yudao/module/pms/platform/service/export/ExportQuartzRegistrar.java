package cn.iocoder.yudao.module.pms.platform.service.export;

import cn.iocoder.yudao.module.infra.api.job.JobApi;
import org.quartz.Scheduler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class ExportQuartzRegistrar implements ApplicationRunner {

    static final String EXECUTION_HANDLER = "exportTaskExecutionJob";
    static final String EXPIRATION_HANDLER = "exportFileExpirationJob";
    private final JobApi jobApi;
    private final ObjectProvider<Scheduler> schedulerProvider;

    public ExportQuartzRegistrar(JobApi jobApi, ObjectProvider<Scheduler> schedulerProvider) {
        this.jobApi = jobApi;
        this.schedulerProvider = schedulerProvider;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (schedulerProvider.getIfAvailable() == null) return;
        jobApi.syncEnabledJobByHandlerName(EXECUTION_HANDLER);
        jobApi.syncEnabledJobByHandlerName(EXPIRATION_HANDLER);
    }
}
