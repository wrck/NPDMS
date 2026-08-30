package cn.iocoder.yudao.module.pms.project.service.satisfaction;

import cn.iocoder.yudao.module.infra.api.job.JobApi;
import org.quartz.Scheduler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class SatisfactionResultQuartzRegistrar implements ApplicationRunner {
    public static final java.util.List<String> HANDLER_NAMES = java.util.List.of(
            "satisfactionTaskOutboxDeliveryJob",
            "satisfactionResultOutboxDeliveryJob",
            "satisfactionResultArchiveCompensationJob");
    private final JobApi jobApi;
    private final ObjectProvider<Scheduler> schedulerProvider;

    public SatisfactionResultQuartzRegistrar(JobApi jobApi, ObjectProvider<Scheduler> schedulerProvider) {
        this.jobApi = jobApi;
        this.schedulerProvider = schedulerProvider;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (schedulerProvider.getIfAvailable() != null) {
            HANDLER_NAMES.forEach(jobApi::syncEnabledJobByHandlerName);
        }
    }
}
