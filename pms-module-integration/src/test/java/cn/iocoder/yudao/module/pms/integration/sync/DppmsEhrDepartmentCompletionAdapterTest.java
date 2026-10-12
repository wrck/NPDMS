package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter.*;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.*;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import cn.iocoder.yudao.module.system.api.migration.SystemDepartmentMigrationApi;
import org.junit.jupiter.api.*;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DppmsEhrDepartmentCompletionAdapterTest {
    final SystemDepartmentMigrationApi owner=mock(SystemDepartmentMigrationApi.class);
    final SyncTaskService tasks=mock(SyncTaskService.class);
    final SyncRunMapper runs=mock(SyncRunMapper.class);
    final SyncBindingMapper bindings=mock(SyncBindingMapper.class);
    final DppmsEhrDepartmentCompletionAdapter adapter=new DppmsEhrDepartmentCompletionAdapter(owner,tasks,runs,bindings);
    final LocalDateTime started=LocalDateTime.of(2026,10,9,20,0);
    SystemDepartmentMigrationApi.Request request;SyncRunDO run;
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        when(tasks.required(12L)).thenReturn(new SyncTaskDO().setId(12L).setAdapter(DppmsEhrDepartmentCompletionTemplate.ADAPTER).setSourceSystem("DPPMS").setConnectionId(18L).setVersion(0).setActiveRunId(13L));
        run=new SyncRunDO().setId(13L).setTaskId(12L).setConfigVersion(0).setPreview(true).setStatus("VALIDATING").setStartedAt(started);run.setTenantId(1L);run.setCreator("7");
        when(runs.selectScoped(any())).thenReturn(run);when(bindings.selectIdentitySources(any())).thenReturn(List.of());
        when(owner.preview(any())).thenAnswer(call->{request=call.getArgument(0);return List.of();});
    }
    @AfterEach void clear(){TenantContextHolder.clear();}
    List<Row> rows() {
        var rows=new ArrayList<Row>();int index=0;
        for(String code:DppmsEhrDepartmentCompletionTemplate.CODES) {
            var fields=new LinkedHashMap<String,Object>();DppmsEhrDepartmentCompletionTemplate.columns().forEach(c->fields.put(c,null));
            fields.put("depID",++index);fields.put("depCode",code);fields.put("depName","原部门");fields.put("director",293);
            fields.put("effectDate","2008-05-28 00:00:00");fields.put("depCustom2",5296);fields.put("depCustom4",0);
            fields.put("isDisabled",code.startsWith("31")?1:null);fields.put("migrationParentCode",code.startsWith("31")?"310409":"162007");
            fields.put("migrationDirectorLoginCount",1);fields.put("migrationDirectorFndCount",1);fields.put("migrationDirectorFndId",23);
            if(code.startsWith("167")) {fields.put("migrationFnd_id",index);fields.put("migrationFnd_departmentName","FND名称");fields.put("migrationFnd_status",1);fields.put("migrationFnd_createTime","2024-01-16 18:45:00");}
            rows.add(new Row("ehr_department",Integer.toString(index),fields,null));
        }
        return rows;
    }
    Batch batch(List<Row> rows){return new Batch("integration:1:12",rows,List.of(),true,"RETAIN",false,"UPSERT",false);}
    @Test void fndHistoryAndEhrActualMigrationAuditUseDifferentApprovedRules() {
        adapter.preview(batch(rows()));assertEquals(8,request.entries().size());
        for(var entry:request.entries()) {
            if(entry.code().startsWith("167")) {assertEquals("160000",entry.parentCode());assertEquals("FND名称",entry.name());assertNull(entry.creator());assertEquals(2024,entry.createTime().getYear());}
            else {assertEquals(started,entry.createTime());assertEquals("7",entry.creator());assertEquals("7",entry.updater());}
            assertNull(entry.leaderUserId());assertEquals(entry.createTime(),entry.updateTime());
        }
        assertEquals(1,request.entries().getLast().status());verify(owner,never()).apply(any());
    }
    @Test void directorUsesMigratedFndIdentityAndNeverAnEhrIntegerAsFormalUserId() {
        when(bindings.selectIdentitySources(any())).thenAnswer(call->{SyncQueries.IdentitySources q=call.getArgument(0);
            assertEquals("fnd_user_info",q.sourceObject());assertEquals(List.of("23"),q.sourceKeys());assertEquals(18L,q.connectionId());
            return List.of(new SyncBindingDO().setTargetId(77L));});
        adapter.preview(batch(rows()));assertTrue(request.entries().stream().allMatch(e->e.leaderUserId().equals(77L)));
    }
    @Test void missingFieldsNewNonemptyExcludedPropertiesAndUnapprovedScopeBlockBeforeOwner() {
        var rows=rows();rows.getFirst().fields().remove("depAbbr");assertThrows(IllegalArgumentException.class,()->adapter.preview(batch(rows)));
        var values=rows();values.getFirst().fields().put("depCustom3",1);assertThrows(IllegalArgumentException.class,()->adapter.preview(batch(values)));
        assertThrows(IllegalArgumentException.class,()->adapter.preview(batch(rows().subList(0,7))));
        run.setPreview(false);assertThrows(IllegalArgumentException.class,()->adapter.preview(batch(rows())));verifyNoInteractions(owner);
    }
    @Test void templateIsReadonlyCompleteAndBoundToTheApprovedEightCodes() {
        var definition=DppmsEhrDepartmentCompletionTemplate.create(18L);var source=definition.sources().getFirst();
        assertEquals("ehr_department",source.sourceObject());assertEquals("depID",source.sourceKey());
        assertDoesNotThrow(()->MysqlSyncReader.compile(source));assertEquals(24,DppmsEhrDepartmentCompletionTemplate.ORIGINAL_COLUMNS.size());
        assertEquals(8,source.parameters().size());assertEquals("ONCE",definition.mode());
    }
}
