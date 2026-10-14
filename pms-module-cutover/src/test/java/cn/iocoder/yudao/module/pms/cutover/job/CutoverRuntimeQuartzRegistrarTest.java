package cn.iocoder.yudao.module.pms.cutover.job;

import cn.iocoder.yudao.module.infra.api.job.JobApi;
import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;
import org.quartz.Trigger;
import org.quartz.TriggerKey;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.DefaultApplicationArguments;

import static org.mockito.Mockito.*;

class CutoverRuntimeQuartzRegistrarTest {
    @Test
    void missingRetrySchedulesAreRegisteredThroughPlatformApi() throws Exception {
        JobApi jobs = mock(JobApi.class);
        Scheduler scheduler = mock(Scheduler.class);
        new CutoverRuntimeQuartzRegistrar(jobs, provider(scheduler)).run(new DefaultApplicationArguments());
        verify(jobs).syncEnabledJobByHandlerName("cutoverRuntimeApprovalNotificationJob");
        verify(jobs).syncEnabledJobByHandlerName("cutoverResultDeliveryJob");
        verifyNoMoreInteractions(jobs);
    }

    @Test
    void existingSchedulesKeepTheirPausedOrRunningState() throws Exception {
        JobApi jobs = mock(JobApi.class);
        Scheduler scheduler = mock(Scheduler.class);
        when(scheduler.getTrigger(any(TriggerKey.class))).thenReturn(mock(Trigger.class));
        new CutoverRuntimeQuartzRegistrar(jobs, provider(scheduler)).run(new DefaultApplicationArguments());
        verifyNoInteractions(jobs);
        verify(scheduler, never()).deleteJob(any());
        verify(scheduler, never()).resumeTrigger(any());
    }

    @Test
    void disabledQuartzDoesNotStartAnotherScheduler() throws Exception {
        JobApi jobs = mock(JobApi.class);
        new CutoverRuntimeQuartzRegistrar(jobs, provider(null)).run(new DefaultApplicationArguments());
        verifyNoInteractions(jobs);
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<Scheduler> provider(Scheduler scheduler) {
        ObjectProvider<Scheduler> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(scheduler);
        return provider;
    }
}
