package cn.iocoder.yudao.module.pms.platform.file;

import cn.iocoder.yudao.module.infra.api.job.JobApi;
import cn.iocoder.yudao.module.pms.platform.service.file.event.FileOutboxQuartzRegistrar;
import org.quartz.Scheduler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.DefaultApplicationArguments;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileOutboxQuartzRegistrarTest {

    @Test
    void registersTheSeededHandlerOnApplicationStartup() {
        JobApi jobApi = mock(JobApi.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<Scheduler> schedulerProvider = mock(ObjectProvider.class);
        when(schedulerProvider.getIfAvailable()).thenReturn(mock(Scheduler.class));
        new FileOutboxQuartzRegistrar(jobApi, schedulerProvider).run(new DefaultApplicationArguments());
        verify(jobApi).syncEnabledJobByHandlerName("fileOutboxDeliveryJob");
    }

    @Test
    void skipsSynchronizationWhenQuartzIsDisabled() {
        JobApi jobApi = mock(JobApi.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<Scheduler> schedulerProvider = mock(ObjectProvider.class);
        new FileOutboxQuartzRegistrar(jobApi, schedulerProvider).run(new DefaultApplicationArguments());
        verify(jobApi, never()).syncEnabledJobByHandlerName("fileOutboxDeliveryJob");
    }
}
