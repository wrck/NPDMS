package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter.*;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.*;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import cn.iocoder.yudao.module.system.api.migration.SystemDictionaryTypeMigrationApi;
import cn.iocoder.yudao.module.system.api.migration.SystemDictionaryTypeMigrationApi.*;
import cn.iocoder.yudao.module.system.api.migration.SystemUserMigrationApi;
import org.junit.jupiter.api.*;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static cn.iocoder.yudao.module.pms.integration.sync.DppmsDictionaryTypeSyncTemplate.*;

class DppmsDictionaryTypeSyncAdapterTest {
    final SystemDictionaryTypeMigrationApi types=mock(SystemDictionaryTypeMigrationApi.class);
    final SystemUserMigrationApi users=mock(SystemUserMigrationApi.class);
    final SyncTaskService tasks=mock(SyncTaskService.class);final SyncRunMapper runs=mock(SyncRunMapper.class);
    final DppmsDictionaryTypeSyncAdapter adapter=new DppmsDictionaryTypeSyncAdapter(types,users,tasks,runs);
    SyncRunDO run;SyncTaskDO task;
    @BeforeEach void setup(){
        TenantContextHolder.setTenantId(1L);
        task=new SyncTaskDO().setId(12L).setVersion(1).setActiveRunId(13L).setAdapter(ADAPTER).setSourceSystem("DPPMS")
                .setDefinition(JsonUtils.toJsonString(create(18L)));task.setTenantId(1L);
        run=new SyncRunDO().setId(13L).setTaskId(12L).setConfigVersion(1).setStatus("APPLYING").setPreview(false);
        run.setTenantId(1L);run.setCreator("7");run.setCreateTime(LocalDateTime.parse("2026-10-12T02:00:00.123"));
        when(tasks.required(12L)).thenReturn(task);when(runs.selectScoped(any())).thenReturn(run);
        when(types.apply(any())).thenAnswer(call->{Request request=call.getArgument(0);
            return request.entries().stream().map(entry->{var x=entry.incoming();var after=new Type(40L,x.name(),x.type(),x.status(),x.remark(),
                    x.creator(),x.createTime(),x.updater(),x.updateTime(),x.deleted(),x.deletedTime());
                return new Result(entry.sourceKey(),"CREATED",null,after);}).toList();});
    }
    @AfterEach void clear(){TenantContextHolder.clear();}
    Row row(boolean missing){
        var fields=new LinkedHashMap<String,Object>();ORIGINAL_COLUMNS.forEach(name->fields.put(name,null));
        fields.put("dataTypeCode","02");fields.put("dataTypeName",missing?"02":"项目状态");fields.put(MISSING,missing?1:0);
        if(!missing){fields.put("id",2);fields.put("status",1);fields.put("createTime","2015-01-02 03:04:05");fields.put("effectiveFrom","2015-01-02 00:00:00");}
        return new Row(OBJECT,"02",fields,null);
    }
    Batch batch(Row row,List<Binding> bindings){return new Batch("integration:1:12",List.of(row),bindings,true,"RETAIN",false,"UPSERT",false);}
    @Test void completeTemplateUsesRealGroupCodeAndNoInventedId(){
        var definition=create(18L);assertDoesNotThrow(()->new SyncDefinitionValidator(List.of(adapter)).validate(definition));
        assertEquals("dataTypeCode",definition.sources().getFirst().sourceKey());
        assertTrue(definition.sources().getFirst().sql().contains("SELECT DISTINCT dataTypeCode FROM fnd_basic_data"));
        assertDoesNotThrow(()->MysqlSyncReader.compile(definition.sources().getFirst()));
    }
    @Test void approvedExclusionsAreOnlyEvidenceAndNullAuditsRemainNull(){
        adapter.apply(batch(row(false),List.of()));
        verify(types).apply(argThat(request->{var x=request.entries().getFirst().incoming();
            return x.status()==0 && x.remark()==null && x.creator()==null && x.updater()==null
                    && x.createTime().equals(LocalDateTime.parse("2015-01-02T03:04:05")) && x.createTime().equals(x.updateTime());}));
    }
    @Test void missingDefinitionUsesActualRunAuditAndReplaysOriginalMigrationTime(){
        var first=adapter.apply(batch(row(true),List.of())).getFirst();
        var type=(Map<?,?>)first.after().get("type");assertEquals("02",type.get("name"));assertEquals("7",type.get("creator"));
        var saved=JsonUtils.parseObject(JsonUtils.toJsonString(type),Type.class);
        assertEquals(LocalDateTime.parse("2026-10-12T02:00:00"),saved.createTime());
        var raw=(Map<?,?>)first.after().get("originalFields");assertEquals(Set.of(MISSING,"dataTypeCode"),raw.keySet());
        run.setCreateTime(LocalDateTime.parse("2026-10-13T02:00:00"));
        var row=row(true);var bound=new Row(row.object(),row.sourceKey(),row.fields(),40L);
        var proof=JsonUtils.parseMap(JsonUtils.toJsonString(first.after()));clearInvocations(types);
        adapter.apply(batch(bound,List.of(new Binding(OBJECT,"02",40L,proof))));
        verify(types).apply(argThat(request->request.entries().getFirst().incoming().createTime().equals(LocalDateTime.parse("2026-10-12T02:00:00"))));
    }
    @Test void exactAdminReferenceWorksAndUnknownActorCannotBeInvented(){
        var row=row(false);row.fields().put("createBy","admin");
        when(users.getHistoricalIdentitiesByUsername(1L,Set.of("admin"))).thenReturn(List.of(new SystemUserMigrationApi.HistoricalIdentity(1L,"admin")));
        adapter.apply(batch(row,List.of()));verify(types).apply(argThat(request->"1".equals(request.entries().getFirst().incoming().creator())));
        row.fields().put("createBy","unknown");clearInvocations(types);
        assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(row,List.of())));verifyNoInteractions(types);
    }
    @Test void missingRawFieldsStaleRunAndDestructiveLoadingAreBlocked(){
        var row=row(false);row.fields().remove("status");
        assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(row,List.of())));
        run.setConfigVersion(2);assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(row(false),List.of())));
        run.setConfigVersion(1);var good=batch(row(false),List.of());
        assertThrows(IllegalArgumentException.class,()->adapter.apply(new Batch(good.owner(),good.rows(),good.bindings(),true,"RETAIN",true,"UPSERT",false)));
        verifyNoInteractions(types);
    }
}
