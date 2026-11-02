package cn.iocoder.yudao.module.pms.project.service.projectmanual;

import cn.iocoder.yudao.module.infra.api.job.JobApi;
import lombok.RequiredArgsConstructor;
import org.quartz.Scheduler;
import org.quartz.TriggerKey;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProjectContractProjectionQuartzRegistrar implements ApplicationRunner {
    private final JobApi jobs;
    private final ObjectProvider<Scheduler> scheduler;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        Scheduler current = scheduler.getIfAvailable();
        if (current != null && current.getTrigger(TriggerKey.triggerKey("projectContractProjectionJob")) == null) {
            jobs.syncEnabledJobByHandlerName("projectContractProjectionJob");
        }
    }
}
