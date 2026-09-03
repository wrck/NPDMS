package cn.iocoder.yudao.module.pms.platform.export;

import cn.iocoder.yudao.module.infra.api.job.JobApi;
import cn.iocoder.yudao.module.pms.platform.service.export.ExportQuartzRegistrar;
import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.DefaultApplicationArguments;

import static org.mockito.Mockito.*;

class ExportQuartzRegistrarTest {

    @Test
    void syncsBothHandlersInStableOrderWhenQuartzExists() throws Exception {
        JobApi jobApi = mock(JobApi.class);
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("scheduler", mock(Scheduler.class));
        new ExportQuartzRegistrar(jobApi, beans.getBeanProvider(Scheduler.class))
                .run(new DefaultApplicationArguments());
        var order = inOrder(jobApi);
        order.verify(jobApi).syncEnabledJobByHandlerName("exportTaskExecutionJob");
        order.verify(jobApi).syncEnabledJobByHandlerName("exportFileExpirationJob");
    }

    @Test
    void skipsSyncWhenQuartzAbsent() throws Exception {
        JobApi jobApi = mock(JobApi.class);
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        new ExportQuartzRegistrar(jobApi, beans.getBeanProvider(Scheduler.class))
                .run(new DefaultApplicationArguments());
        verifyNoInteractions(jobApi);
    }
}
