package cn.iocoder.yudao.module.infra.service.job;

import cn.iocoder.yudao.module.infra.dal.mysql.job.JobMapper;
import cn.iocoder.yudao.module.infra.enums.job.JobStatusEnum;
import lombok.RequiredArgsConstructor;
import org.quartz.Scheduler;
import org.quartz.TriggerKey;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Restore persisted job definitions when an in-memory scheduler starts empty. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class JobStartupRegistrar implements ApplicationRunner {
    private final JobMapper mapper;
    private final JobService jobs;
    private final ObjectProvider<Scheduler> scheduler;
    private final BeanFactory beans;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        Scheduler current = scheduler.getIfAvailable();
        if (current == null) return;
        boolean resume = current.isStarted() && !current.isInStandbyMode();
        current.standby();
        // Fail startup on invalid definitions; never start a partially restored scheduler.
        for (var job : mapper.selectList()) {
            if (!JobStatusEnum.NORMAL.getStatus().equals(job.getStatus())
                    && !JobStatusEnum.STOP.getStatus().equals(job.getStatus())) continue;
            // Optional features may keep a paused definition while their handler is not installed.
            // Enabling that definition still goes through the normal handler validation.
            if (JobStatusEnum.STOP.getStatus().equals(job.getStatus())
                    && !beans.containsBean(job.getHandlerName())) continue;
            if (current.getTrigger(TriggerKey.triggerKey(job.getHandlerName())) == null) {
                jobs.syncJobByHandlerName(job.getHandlerName());
            }
        }
        if (resume) current.start();
    }
}
