package cn.iocoder.yudao.module.pms.project.service.runtimegraph;

import cn.iocoder.yudao.module.infra.api.job.JobApi;
import lombok.RequiredArgsConstructor;
import org.quartz.Scheduler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Reuses the platform technical Outbox scheduler; this is not a business-calendar trigger. */
@Component
@RequiredArgsConstructor
public class ProjectRuleQuartzRegistrar implements ApplicationRunner {
    private final JobApi jobs;
    private final ObjectProvider<Scheduler> scheduler;

    @Override public void run(ApplicationArguments args) throws org.quartz.SchedulerException {
        Scheduler current = scheduler.getIfAvailable();
        if (current == null) return;
        var key = org.quartz.TriggerKey.triggerKey("projectRuleOutboxDeliveryJob");
        var trigger = current.getTrigger(key);
        if (trigger == null) {
            jobs.syncEnabledJobByHandlerName("projectRuleOutboxDeliveryJob");
        } else if (current.getTriggerState(key) == org.quartz.Trigger.TriggerState.BLOCKED) {
            // Preserve the job and use Quartz's store/concurrency checks, never repair scheduler tables directly.
            // A restart must not delete a durable job while Quartz is completing its recovered execution.
            current.rescheduleJob(key, trigger.getTriggerBuilder().startNow().build());
        }
    }
}
