package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter.*;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.*;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import cn.iocoder.yudao.module.system.api.migration.SystemDictionaryDataMigrationApi;
import cn.iocoder.yudao.module.system.api.migration.SystemDictionaryDataMigrationApi.*;
import cn.iocoder.yudao.module.system.api.migration.SystemUserMigrationApi;
import org.junit.jupiter.api.*;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static cn.iocoder.yudao.module.pms.integration.sync.DppmsDictionaryDataSyncTemplate.*;

class DppmsDictionaryDataSyncAdapterTest {
    final SystemDictionaryDataMigrationApi data=mock(SystemDictionaryDataMigrationApi.class);
    final SystemUserMigrationApi users=mock(SystemUserMigrationApi.class);final SyncTaskService tasks=mock(SyncTaskService.class);
    final SyncRunMapper runs=mock(SyncRunMapper.class);final DppmsDictionaryDataSyncAdapter adapter=new DppmsDictionaryDataSyncAdapter(data,users,tasks,runs);
    SyncRunDO run;final LocalDateTime now=LocalDateTime.parse("2026-10-12T03:00:00");
    @BeforeEach void setup(){
        TenantContextHolder.setTenantId(1L);
        var task=new SyncTaskDO().setId(12L).setVersion(1).setActiveRunId(13L).setAdapter(ADAPTER).setSourceSystem("DPPMS")
                .setDefinition(JsonUtils.toJsonString(create(18L)));task.setTenantId(1L);
        run=new SyncRunDO().setId(13L).setTaskId(12L).setConfigVersion(1).setStatus("APPLYING").setPreview(false);
        run.setTenantId(1L);run.setCreator("1");run.setCreateTime(now);
        when(tasks.required(12L)).thenReturn(task);when(runs.selectScoped(any())).thenReturn(run);
        when(data.apply(any())).thenAnswer(c->{Request request=c.getArgument(0);return request.entries().stream().map(e->{var x=e.incoming();
            return new Result(e.sourceKey(),"CREATED",null,new Item(30L,x.sort(),x.label(),x.value(),x.dictType(),x.status(),
                    x.colorType(),x.cssClass(),x.remark(),x.creator(),x.createTime(),x.updater(),x.updateTime(),false));}).toList();});
    }
    @AfterEach void clear(){TenantContextHolder.clear();}
    Row row(String id){
        var f=new LinkedHashMap<String,Object>();ORIGINAL_COLUMNS.forEach(n->f.put(n,null));f.put("id",id);f.put("dataTypeCode","01");
        f.put("basicDataId","1");f.put("basicDataName","快递");return new Row(OBJECT,id,f,null);
    }
    Batch batch(Row row,List<Binding> prior){return new Batch("integration:1:12",List.of(row),prior,true,"RETAIN",false,"UPSERT",false);}
    @Test void templateReadsFullOriginalIncludingExplicitlyExcludedRow(){
        var definition=create(18L);assertDoesNotThrow(()->new SyncDefinitionValidator(List.of(adapter)).validate(definition));
        assertDoesNotThrow(()->MysqlSyncReader.compile(definition.sources().getFirst()));
        assertEquals(12,ORIGINAL_COLUMNS.size());assertFalse(definition.sources().getFirst().sql().contains("WHERE"));
    }
    @Test void nullSortAndCreateTimeUseApprovedInitializationAndReplayKeepsFirstRunTime(){
        var first=adapter.apply(batch(row("30"),List.of())).getFirst();
        assertEquals(14,((Map<?,?>)first.after().get("item")).size());assertEquals(12,((Map<?,?>)first.after().get("originalFields")).size());
        verify(data).apply(argThat(r->{var x=r.entries().getFirst().incoming();return x.sort()==0 && x.createTime().equals(now)
                && x.updateTime().equals(now) && x.creator()==null && x.updater()==null;}));
        var proof=JsonUtils.parseMap(JsonUtils.toJsonString(first.after()));var source=row("30");run.setCreateTime(now.plusDays(1));
        clearInvocations(data);adapter.apply(batch(new Row(OBJECT,"30",source.fields(),30L),List.of(new Binding(OBJECT,"30",30L,proof))));
        verify(data).apply(argThat(r->r.entries().getFirst().incoming().createTime().equals(now)));
    }
    @Test void onlyApprovedSystemCreatorMayRemainRawAndHistoricalEndDisablesItem(){
        var source=row("542");source.fields().put("createBy","system");source.fields().put("createTime","2015-01-02 03:04:05");
        source.fields().put("effectiveTo","2020-07-07 00:00:00");source.fields().put("basicDataAttri1","旧入口");
        adapter.apply(batch(source,List.of()));
        verify(data).apply(argThat(r->{var x=r.entries().getFirst().incoming();return "system".equals(x.creator()) && x.status()==1
                && x.remark()==null && x.cssClass()==null && x.colorType()==null;}));
        clearInvocations(data);var unapproved=row("543");unapproved.fields().put("createBy","system");
        assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(unapproved,List.of())));verifyNoInteractions(data);
    }
    @Test void emptyRecord114IsExplicitlySkippedOnlyWhileAllReviewedEmptyFieldsRemainEmpty(){
        var source=row("114");source.fields().put("dataTypeCode","23");source.fields().put("basicDataId",null);source.fields().put("basicDataName",null);
        var change=adapter.apply(batch(source,List.of())).getFirst();assertEquals("SKIPPED",change.action());assertNull(change.targetId());
        verify(data).apply(argThat(r->r.entries().isEmpty()));
        clearInvocations(data);source.fields().put("basicDataAttri1","后来有值");
        assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(source,List.of())));verifyNoInteractions(data);
    }
    @Test void missingFieldChangedExcludedAttributeStaleRunAndAdoptionCannotWrite(){
        var source=row("30");var first=adapter.apply(batch(source,List.of())).getFirst();var proof=JsonUtils.parseMap(JsonUtils.toJsonString(first.after()));
        source.fields().put("basicDataAttri1","新值");clearInvocations(data);
        assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(new Row(OBJECT,"30",source.fields(),30L),List.of(new Binding(OBJECT,"30",30L,proof)))));
        var missing=row("31");missing.fields().remove("effectiveTo");assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(missing,List.of())));
        assertThrows(IllegalArgumentException.class,()->adapter.apply(new Batch("integration:1:12",List.of(row("30")),List.of(),true,"RETAIN",true,"UPSERT",false)));
        run.setConfigVersion(2);assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(row("30"),List.of())));verifyNoInteractions(data);
    }
}
