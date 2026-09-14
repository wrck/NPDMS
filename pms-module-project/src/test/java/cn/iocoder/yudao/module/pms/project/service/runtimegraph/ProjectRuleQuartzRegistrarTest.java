package cn.iocoder.yudao.module.pms.project.service.runtimegraph;

import cn.iocoder.yudao.module.infra.api.job.JobApi;
import org.junit.jupiter.api.Test;
import org.quartz.*;
import org.springframework.beans.factory.ObjectProvider;

import static org.mockito.Mockito.*;

class ProjectRuleQuartzRegistrarTest {
    @Test void restoresBlockedTriggerWithoutDeletingTheJobOrOverridingPausedJobs() throws Exception {
        var jobs = mock(JobApi.class);
        var scheduler = mock(Scheduler.class);
        @SuppressWarnings("unchecked") ObjectProvider<Scheduler> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(scheduler);
        var key = TriggerKey.triggerKey("projectRuleOutboxDeliveryJob");
        var trigger = TriggerBuilder.newTrigger().withIdentity(key).withSchedule(CronScheduleBuilder.cronSchedule("0/30 * * * * ?")).build();
        when(scheduler.getTrigger(key)).thenReturn(trigger);
        var registrar = new ProjectRuleQuartzRegistrar(jobs, provider);
        when(scheduler.getTriggerState(key)).thenReturn(Trigger.TriggerState.PAUSED);
        registrar.run(null);
        verify(scheduler, never()).rescheduleJob(any(), any());
        when(scheduler.getTriggerState(key)).thenReturn(Trigger.TriggerState.NORMAL);
        registrar.run(null);
        verifyNoInteractions(jobs);
        when(scheduler.getTriggerState(key)).thenReturn(Trigger.TriggerState.BLOCKED);
        registrar.run(null);
        verify(scheduler).rescheduleJob(eq(key), any());
        verify(scheduler, never()).deleteJob(any());
    }
}
