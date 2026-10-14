package cn.iocoder.yudao.module.pms.cutover.job;

import cn.iocoder.yudao.module.infra.api.job.JobApi;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.TriggerKey;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Registers the platform-owned retry schedules while retaining existing Quartz state. */
@Component
@RequiredArgsConstructor
public class CutoverJobRegistrar implements ApplicationRunner {
    private final JobApi jobs;
    private final ObjectProvider<Scheduler> scheduler;

    @Override
    public void run(ApplicationArguments args) throws SchedulerException {
        Scheduler current = scheduler.getIfAvailable();
        if (current == null) return;
        for (String handler : List.of("cutoverApprovalNotificationJob", "cutoverResultDeliveryJob")) {
            if (current.getTrigger(TriggerKey.triggerKey(handler)) == null) {
                jobs.syncEnabledJobByHandlerName(handler);
            }
        }
    }
}
