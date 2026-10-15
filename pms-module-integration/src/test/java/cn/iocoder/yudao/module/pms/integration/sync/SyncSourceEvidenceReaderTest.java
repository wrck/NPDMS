package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.*;
import cn.iocoder.yudao.module.pms.platform.api.migration.PlatformMigrationEvidenceApi;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.*;
import org.junit.jupiter.api.*;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SyncSourceEvidenceReaderTest {
    final SyncTaskMapper tasks=mock(SyncTaskMapper.class);
    final SyncRunMapper runs=mock(SyncRunMapper.class);
    final PlatformMigrationEvidenceApi evidence=mock(PlatformMigrationEvidenceApi.class);
    final SyncSourceEvidenceReader reader=new SyncSourceEvidenceReader(tasks,runs,evidence);
    SyncDefinition d;SyncTaskDO task;SyncRunDO run;
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);d=DppmsUserScopeSyncTemplate.create(18L);
        task=new SyncTaskDO().setId(10L).setAdapter("DPPMS_USERS").setSourceSystem("DPPMS").setConnectionId(18L).setVersion(2);
        task.setTenantId(1L);
        run=new SyncRunDO().setId(20L).setTaskId(10L).setConfigVersion(2).setPreview(false).setFullSnapshot(true).setStatus("SUCCESS")
                .setConfigSnapshot(JsonUtils.toJsonString(DppmsUserSyncTemplate.create(18L)))
                .setEvidenceJson(JsonUtils.toJsonString(List.of(new SyncEvidenceService.Evidence("t_user","t_user",30L,Map.of("7",40L)))));
        run.setTenantId(1L);
        when(tasks.selectIdentity(any())).thenReturn(task);when(runs.selectSuccessfulSnapshot(any())).thenReturn(run);
        when(evidence.pageSourceRecords(any())).thenReturn(new MigrationSourceRecordPage(List.of(new MigrationSourceRecordFact(
                40L,1L,30L,"DPPMS","t_user","7",null,"{\"user_id\":7,\"user_name\":\"c00007\"}","0".repeat(64),LocalDateTime.of(2026,1,1,0,0),"MAPPED")),null));
    }
    @AfterEach void clear(){TenantContextHolder.clear();}
    @Test void sharedReadUsesSuccessfulCurrentVersionAndPreservesOriginalKeyAndReceipt() {
        var cache=new HashMap<String,SyncRunDO>();
        var input=new SyncSourceEvidenceReader.Input(d,d.sources().getFirst(),cache);
        var row=reader.read(input).rows().getFirst();
        assertEquals(7,row.get("user_id"));assertEquals(20L,row.get("_sharedSourceRunId"));assertEquals(40L,row.get("_sharedSourceRecordId"));
        reader.read(input);verify(tasks,times(1)).selectIdentity(any());verify(runs,times(1)).selectSuccessfulSnapshot(any());
    }
    @Test void missingOrPreviewReceiptAndDifferentConnectionCannotBroadenToALiveQuery() {
        task.setConnectionId(19L);assertThrows(IllegalArgumentException.class,()->read());verifyNoInteractions(evidence);
        task.setConnectionId(18L);run.setPreview(true);assertThrows(IllegalArgumentException.class,()->read());verifyNoInteractions(evidence);
        run.setPreview(false);when(runs.selectSuccessfulSnapshot(any())).thenReturn(null);
        assertThrows(IllegalArgumentException.class,()->read());verifyNoInteractions(evidence);
    }
    @Test void incompleteSourceEvidenceIsRejected() {
        when(evidence.pageSourceRecords(any())).thenReturn(new MigrationSourceRecordPage(List.of(),null));
        assertThrows(IllegalArgumentException.class,()->read());
    }
    @Test void resolvedDatasourceIdCannotReplaceTheConfiguredConnectionIdentity() {
        d=d.toBuilder().configuredConnectionId(18L).connectionId(900L).build();
        assertEquals(1,read().rows().size());
        assertFalse(JsonUtils.toJsonString(d).contains("configuredConnectionId"));
    }
    private MysqlSyncReader.SourceRows read(){return reader.read(new SyncSourceEvidenceReader.Input(d,d.sources().getFirst(),new HashMap<>()));}
}
