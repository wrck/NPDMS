package cn.iocoder.yudao.module.pms.project.service.acceptancereport;

import cn.iocoder.yudao.module.infra.api.job.JobApi;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.quartz.Scheduler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.DefaultApplicationArguments;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AcceptanceReportQuartzRegistrarTest {

    @Test
    void registersBothSeededHandlersInDeliveryThenArchiveOrder() {
        JobApi jobApi = mock(JobApi.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<Scheduler> schedulerProvider = mock(ObjectProvider.class);
        when(schedulerProvider.getIfAvailable()).thenReturn(mock(Scheduler.class));

        new AcceptanceReportQuartzRegistrar(jobApi, schedulerProvider)
                .run(new DefaultApplicationArguments());

        InOrder order = inOrder(jobApi);
        order.verify(jobApi).syncEnabledJobByHandlerName(
                AcceptanceReportQuartzRegistrar.OUTBOX_HANDLER_NAME);
        order.verify(jobApi).syncEnabledJobByHandlerName(
                AcceptanceReportQuartzRegistrar.ARCHIVE_HANDLER_NAME);
    }

    @Test
    void skipsSynchronizationWhenQuartzIsDisabled() {
        JobApi jobApi = mock(JobApi.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<Scheduler> schedulerProvider = mock(ObjectProvider.class);

        new AcceptanceReportQuartzRegistrar(jobApi, schedulerProvider)
                .run(new DefaultApplicationArguments());

        verify(jobApi, never()).syncEnabledJobByHandlerName(
                AcceptanceReportQuartzRegistrar.OUTBOX_HANDLER_NAME);
        verify(jobApi, never()).syncEnabledJobByHandlerName(
                AcceptanceReportQuartzRegistrar.ARCHIVE_HANDLER_NAME);
    }
}
