package cn.iocoder.yudao.module.pms.project.service.satisfaction;

import cn.iocoder.yudao.module.infra.api.job.JobApi;
import org.quartz.Scheduler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class SatisfactionResultQuartzRegistrar implements ApplicationRunner {
    public static final String HANDLER_NAME = "satisfactionResultOutboxDeliveryJob";
    private final JobApi jobApi;
    private final ObjectProvider<Scheduler> schedulerProvider;

    public SatisfactionResultQuartzRegistrar(JobApi jobApi, ObjectProvider<Scheduler> schedulerProvider) {
        this.jobApi = jobApi;
        this.schedulerProvider = schedulerProvider;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (schedulerProvider.getIfAvailable() != null) {
            jobApi.syncEnabledJobByHandlerName(HANDLER_NAME);
        }
    }
}
