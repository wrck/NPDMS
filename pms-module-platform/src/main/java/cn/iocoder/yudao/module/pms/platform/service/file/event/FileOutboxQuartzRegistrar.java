package cn.iocoder.yudao.module.pms.platform.service.file.event;

import cn.iocoder.yudao.module.infra.api.job.JobApi;
import org.quartz.Scheduler;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class FileOutboxQuartzRegistrar implements ApplicationRunner {

    public static final String HANDLER_NAME = "fileOutboxDeliveryJob";

    private final JobApi jobApi;
    private final ObjectProvider<Scheduler> schedulerProvider;

    public FileOutboxQuartzRegistrar(JobApi jobApi, ObjectProvider<Scheduler> schedulerProvider) {
        this.jobApi = jobApi;
        this.schedulerProvider = schedulerProvider;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (schedulerProvider.getIfAvailable() == null) {
            return;
        }
        jobApi.syncEnabledJobByHandlerName(HANDLER_NAME);
    }
}
