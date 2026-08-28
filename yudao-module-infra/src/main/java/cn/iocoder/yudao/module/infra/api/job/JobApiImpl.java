package cn.iocoder.yudao.module.infra.api.job;

import cn.iocoder.yudao.module.infra.service.job.JobService;
import org.quartz.SchedulerException;
import org.springframework.stereotype.Service;

@Service
public class JobApiImpl implements JobApi {

    private final JobService jobService;

    public JobApiImpl(JobService jobService) {
        this.jobService = jobService;
    }

    @Override
    public void syncEnabledJobByHandlerName(String handlerName) {
        try {
            jobService.syncEnabledJobByHandlerName(handlerName);
        } catch (SchedulerException exception) {
            throw new IllegalStateException("JOB_SYNC_FAILED", exception);
        }
    }
}
