package cn.iocoder.yudao.module.infra.service.job;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.quartz.core.handler.JobHandler;
import cn.iocoder.yudao.framework.quartz.core.scheduler.SchedulerManager;
import cn.iocoder.yudao.module.infra.dal.dataobject.job.JobDO;
import cn.iocoder.yudao.module.infra.dal.mysql.job.JobMapper;
import cn.iocoder.yudao.module.infra.enums.job.JobStatusEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobServiceSyncTest {

    @Mock JobMapper jobMapper;
    @Mock SchedulerManager schedulerManager;
    @Mock JobHandler jobHandler;

    private JobServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new JobServiceImpl();
        ReflectionTestUtils.setField(service, "jobMapper", jobMapper);
        ReflectionTestUtils.setField(service, "schedulerManager", schedulerManager);
    }

    @Test
    void synchronizesTheUniqueEnabledHandlerWithExistingQuartz() throws Exception {
        JobDO job = enabledJob();
        when(jobMapper.selectListByHandlerName("fileOutboxDeliveryJob")).thenReturn(List.of(job));
        try (MockedStatic<SpringUtil> spring = mockStatic(SpringUtil.class)) {
            spring.when(() -> SpringUtil.getBean("fileOutboxDeliveryJob")).thenReturn(jobHandler);

            service.syncEnabledJobByHandlerName("fileOutboxDeliveryJob");
        }

        verify(schedulerManager).deleteJob("fileOutboxDeliveryJob");
        verify(schedulerManager).addJob(job.getId(), job.getHandlerName(), job.getHandlerParam(),
                job.getCronExpression(), job.getRetryCount(), job.getRetryInterval());
    }

    @Test
    void rejectsMissingOrAmbiguousHandlerRecords() {
        when(jobMapper.selectListByHandlerName("fileOutboxDeliveryJob"))
                .thenReturn(List.of(), List.of(enabledJob(), enabledJob()));

        assertThrows(IllegalStateException.class,
                () -> service.syncEnabledJobByHandlerName("fileOutboxDeliveryJob"));
        assertThrows(IllegalStateException.class,
                () -> service.syncEnabledJobByHandlerName("fileOutboxDeliveryJob"));
    }

    @Test
    void preservesPausedStatusWhenRegisteringAnExistingJob() throws Exception {
        JobDO job = enabledJob();
        job.setStatus(JobStatusEnum.STOP.getStatus());
        when(jobMapper.selectListByHandlerName(job.getHandlerName())).thenReturn(List.of(job));
        try (MockedStatic<SpringUtil> spring = mockStatic(SpringUtil.class)) {
            spring.when(() -> SpringUtil.getBean(job.getHandlerName())).thenReturn(jobHandler);
            service.syncJobByHandlerName(job.getHandlerName());
        }
        var order = inOrder(schedulerManager);
        order.verify(schedulerManager).deleteJob(job.getHandlerName());
        order.verify(schedulerManager).addJob(job.getId(), job.getHandlerName(), job.getHandlerParam(),
                job.getCronExpression(), job.getRetryCount(), job.getRetryInterval());
        order.verify(schedulerManager).pauseJob(job.getHandlerName());
        verify(jobMapper, never()).updateById(job);
    }

    @Test
    void enabledOnlyContractStillRejectsPausedJobs() {
        JobDO job = enabledJob();
        job.setStatus(JobStatusEnum.STOP.getStatus());
        when(jobMapper.selectListByHandlerName(job.getHandlerName())).thenReturn(List.of(job));
        assertThrows(IllegalStateException.class, () -> service.syncEnabledJobByHandlerName(job.getHandlerName()));
        verifyNoInteractions(schedulerManager);
    }

    @Test
    void statePreservingSyncRejectsMissingDuplicateAndUninitializedJobs() {
        JobDO job = enabledJob();
        job.setStatus(JobStatusEnum.INIT.getStatus());
        when(jobMapper.selectListByHandlerName(job.getHandlerName()))
                .thenReturn(List.of(), List.of(job, job), List.of(job));
        for (int i = 0; i < 3; i++) {
            assertThrows(IllegalStateException.class, () -> service.syncJobByHandlerName(job.getHandlerName()));
        }
        verifyNoInteractions(schedulerManager);
    }

    private JobDO enabledJob() {
        return JobDO.builder()
                .id(94_000L)
                .handlerName("fileOutboxDeliveryJob")
                .handlerParam("")
                .cronExpression("0/30 * * * * ?")
                .retryCount(0)
                .retryInterval(0)
                .status(JobStatusEnum.NORMAL.getStatus())
                .build();
    }
}
