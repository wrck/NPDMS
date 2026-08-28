package cn.iocoder.yudao.module.infra.api.job;

import cn.iocoder.yudao.module.infra.service.job.JobService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.SchedulerException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class JobApiImplTest {

    @Mock JobService jobService;

    @Test
    void delegatesTheExactHandlerRegistration() throws Exception {
        new JobApiImpl(jobService).syncEnabledJobByHandlerName("fileOutboxDeliveryJob");
        verify(jobService).syncEnabledJobByHandlerName("fileOutboxDeliveryJob");
    }

    @Test
    void propagatesSchedulerFailureAsStartupFailure() throws Exception {
        doThrow(new SchedulerException("failed")).when(jobService)
                .syncEnabledJobByHandlerName("fileOutboxDeliveryJob");
        assertThrows(IllegalStateException.class,
                () -> new JobApiImpl(jobService).syncEnabledJobByHandlerName("fileOutboxDeliveryJob"));
    }
}
