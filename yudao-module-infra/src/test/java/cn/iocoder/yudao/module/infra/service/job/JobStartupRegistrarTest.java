package cn.iocoder.yudao.module.infra.service.job;

import cn.iocoder.yudao.module.infra.dal.dataobject.job.JobDO;
import cn.iocoder.yudao.module.infra.dal.mysql.job.JobMapper;
import cn.iocoder.yudao.module.infra.enums.job.JobStatusEnum;
import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;
import org.quartz.Trigger;
import org.quartz.TriggerKey;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.BeanFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class JobStartupRegistrarTest {
    private final JobMapper mapper = mock(JobMapper.class);
    private final JobService jobs = mock(JobService.class);
    private final Scheduler scheduler = mock(Scheduler.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<Scheduler> provider = mock(ObjectProvider.class);
    private final BeanFactory beans = mock(BeanFactory.class);
    private final JobStartupRegistrar registrar = new JobStartupRegistrar(mapper, jobs, provider, beans);

    @Test void restoresEnabledAndPausedDefinitionsBeforeResumingWithoutReplacingExistingTriggers() throws Exception {
        when(provider.getIfAvailable()).thenReturn(scheduler);
        when(beans.containsBean("paused")).thenReturn(true);
        when(scheduler.isStarted()).thenReturn(true);
        when(mapper.selectList()).thenReturn(List.of(job("missing", JobStatusEnum.NORMAL),
                job("paused", JobStatusEnum.STOP), job("existing", JobStatusEnum.NORMAL), job("draft", JobStatusEnum.INIT)));
        when(scheduler.getTrigger(TriggerKey.triggerKey("existing"))).thenReturn(mock(Trigger.class));
        registrar.run(null);
        var ordered = inOrder(scheduler, jobs);
        ordered.verify(scheduler).standby();
        ordered.verify(jobs).syncJobByHandlerName("missing");
        ordered.verify(jobs).syncJobByHandlerName("paused");
        ordered.verify(scheduler).start();
        verify(jobs, never()).syncJobByHandlerName("existing");
        verify(jobs, never()).syncJobByHandlerName("draft");
    }

    @Test void invalidDefinitionFailsStartupAndDoesNotRunPartiallyRestoredJobs() throws Exception {
        when(provider.getIfAvailable()).thenReturn(scheduler);
        when(scheduler.isStarted()).thenReturn(true);
        when(mapper.selectList()).thenReturn(List.of(job("invalid", JobStatusEnum.NORMAL)));
        doThrow(new IllegalStateException("JOB_HANDLER_NOT_UNIQUE")).when(jobs).syncJobByHandlerName("invalid");
        assertThrows(IllegalStateException.class, () -> registrar.run(null));
        verify(scheduler, never()).start();
    }

    @Test void uninstalledPausedFeatureRemainsPausedWithoutBlockingOtherEnabledJobs() throws Exception {
        when(provider.getIfAvailable()).thenReturn(scheduler);
        when(scheduler.isStarted()).thenReturn(true);
        when(mapper.selectList()).thenReturn(List.of(job("optional-paused", JobStatusEnum.STOP), job("enabled", JobStatusEnum.NORMAL)));
        registrar.run(null);
        verify(jobs, never()).syncJobByHandlerName("optional-paused");
        verify(jobs).syncJobByHandlerName("enabled");
        verify(scheduler).start();
    }

    @Test void preservesDeliberatelyStoppedScheduler() throws Exception {
        when(provider.getIfAvailable()).thenReturn(scheduler);
        when(mapper.selectList()).thenReturn(List.of());
        registrar.run(null);
        verify(scheduler, never()).start();
    }

    @Test void disabledQuartzDoesNotReadOrRegisterJobs() throws Exception {
        registrar.run(null);
        verifyNoInteractions(mapper, jobs, scheduler);
    }

    private static JobDO job(String handler, JobStatusEnum status) {
        return JobDO.builder().handlerName(handler).status(status.getStatus()).build();
    }
}
