package cn.iocoder.yudao.module.pms.platform.file;

import cn.iocoder.yudao.module.infra.api.job.JobApi;
import cn.iocoder.yudao.module.pms.platform.service.file.event.FileOutboxQuartzRegistrar;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class FileOutboxQuartzRegistrarTest {

    @Test
    void registersTheSeededHandlerOnApplicationStartup() {
        JobApi jobApi = mock(JobApi.class);
        new FileOutboxQuartzRegistrar(jobApi).run(new DefaultApplicationArguments());
        verify(jobApi).syncEnabledJobByHandlerName("fileOutboxDeliveryJob");
    }
}
