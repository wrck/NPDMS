package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.SyncRunDO;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.SyncTaskDO;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.SyncBindingMapper;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.SyncRunMapper;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.SyncTaskMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.batch.infrastructure.support.transaction.ResourcelessTransactionManager;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SyncRunMaintenanceTest {
    private final SyncTaskService taskService=mock(SyncTaskService.class);
    private final SyncTaskMapper tasks=mock(SyncTaskMapper.class);
    private final SyncRunMapper runs=mock(SyncRunMapper.class);
    private final SyncBindingMapper bindings=mock(SyncBindingMapper.class);
    private final SyncConnectionService connections=mock(SyncConnectionService.class);
    private final MysqlSyncReader reader=mock(MysqlSyncReader.class);
    private final SpringJdbcStreamingReader streamingReader=mock(SpringJdbcStreamingReader.class);
    private final SyncFieldMapper fieldMapper=mock(SyncFieldMapper.class);
    private final SyncDefinitionValidator validator=mock(SyncDefinitionValidator.class);
    private final SyncEvidenceService evidenceService=mock(SyncEvidenceService.class);
    private final RedissonClient redisson=mock(RedissonClient.class);
    private final RLock lock=mock(RLock.class);
    private final SyncBatchLauncher batchLauncher=mock(SyncBatchLauncher.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<SyncBatchLauncher> launcher=mock(ObjectProvider.class);
    private SyncRunService service;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        when(redisson.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock()).thenReturn(true);
        when(launcher.getObject()).thenReturn(batchLauncher);
        service=new SyncRunService(taskService,tasks,runs,bindings,connections,reader,streamingReader,fieldMapper,
                validator,evidenceService,redisson,new ResourcelessTransactionManager(),launcher);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void interruptedRootRunSchedulesAssociatedRetryAndReleasesOwner() {
        String config=config();
        var selected=new SyncRunDO().setId(10L).setStatus("READING").setConfigSnapshot(config);
        var run=new SyncRunDO().setId(10L).setTaskId(20L).setStatus("READING").setConfigSnapshot(config)
                .setPreview(false).setFullSnapshot(true);
        var task=new SyncTaskDO().setId(20L).setActiveRunId(10L).setRetryAttempt(0);
        when(runs.selectMaintenance(any())).thenReturn(List.of(selected));
        when(runs.selectScoped(any())).thenReturn(run);
        when(taskService.locked(20L)).thenReturn(task);

        service.maintain();

        assertEquals("FAILED",run.getStatus());
        assertNull(task.getActiveRunId());
        assertEquals(1,task.getRetryAttempt());
        assertEquals(10L,task.getLastFailedRunId());
        assertNotNull(task.getNextRunAt());
        assertEquals(task.getNextRunAt(),task.getNextFullAt());
        verify(tasks).updateById(task);
        verify(batchLauncher).recover(1L,10L);
    }

    @Test
    void interruptedPageRunNeverClearsRootOwner() {
        String config=config();
        var selected=new SyncRunDO().setId(11L).setStatus("APPLYING").setConfigSnapshot(config);
        var page=new SyncRunDO().setId(11L).setTaskId(20L).setParentRunId(10L).setPageNumber(2)
                .setStatus("APPLYING").setConfigSnapshot(config).setPreview(false).setFullSnapshot(true);
        var task=new SyncTaskDO().setId(20L).setActiveRunId(10L).setRetryAttempt(0);
        when(runs.selectMaintenance(any())).thenReturn(List.of(selected));
        when(runs.selectScoped(any())).thenReturn(page);
        when(taskService.locked(20L)).thenReturn(task);

        service.maintain();

        assertEquals("FAILED",page.getStatus());
        assertEquals(10L,task.getActiveRunId());
        assertEquals(0,task.getRetryAttempt());
        verify(tasks,never()).updateById(any());
        verify(batchLauncher).recover(1L,11L);
    }

    private static String config() {
        return JsonUtils.toJsonString(SyncDefinition.builder().adapter("TEST").cron("0 0 2 * * ?")
                .fullCron("0 0 2 ? * SUN").retryCount(2).retryIntervalSeconds(60).build());
    }
}
