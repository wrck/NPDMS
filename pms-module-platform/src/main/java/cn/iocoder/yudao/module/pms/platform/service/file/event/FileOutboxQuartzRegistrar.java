package cn.iocoder.yudao.module.pms.platform.service.file.event;

import cn.iocoder.yudao.module.infra.api.job.JobApi;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class FileOutboxQuartzRegistrar implements ApplicationRunner {

    public static final String HANDLER_NAME = "fileOutboxDeliveryJob";

    private final JobApi jobApi;

    public FileOutboxQuartzRegistrar(JobApi jobApi) {
        this.jobApi = jobApi;
    }

    @Override
    public void run(ApplicationArguments args) {
        jobApi.syncEnabledJobByHandlerName(HANDLER_NAME);
    }
}
