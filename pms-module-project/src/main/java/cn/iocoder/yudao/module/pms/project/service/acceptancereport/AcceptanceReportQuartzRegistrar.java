package cn.iocoder.yudao.module.pms.project.service.acceptancereport;

import cn.iocoder.yudao.module.infra.api.job.JobApi;
import org.quartz.Scheduler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class AcceptanceReportQuartzRegistrar implements ApplicationRunner {

    public static final String OUTBOX_HANDLER_NAME = "acceptanceReportOutboxDeliveryJob";
    public static final String ARCHIVE_HANDLER_NAME = "acceptanceReportArchiveCompensationJob";

    private final JobApi jobApi;
    private final ObjectProvider<Scheduler> schedulerProvider;

    public AcceptanceReportQuartzRegistrar(JobApi jobApi, ObjectProvider<Scheduler> schedulerProvider) {
        this.jobApi = jobApi;
        this.schedulerProvider = schedulerProvider;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (schedulerProvider.getIfAvailable() == null) {
            return;
        }
        jobApi.syncEnabledJobByHandlerName(OUTBOX_HANDLER_NAME);
        jobApi.syncEnabledJobByHandlerName(ARCHIVE_HANDLER_NAME);
    }
}
