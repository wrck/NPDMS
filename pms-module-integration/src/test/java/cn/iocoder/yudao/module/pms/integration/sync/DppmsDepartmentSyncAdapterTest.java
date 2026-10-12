package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter.*;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.SyncTaskDO;
import cn.iocoder.yudao.module.system.api.migration.SystemDepartmentMigrationApi;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DppmsDepartmentSyncAdapterTest {
    private SystemDepartmentMigrationApi owner;
    private DppmsDepartmentSyncAdapter adapter;
    private SystemDepartmentMigrationApi.Request request;
    @BeforeEach void setup(){
        TenantContextHolder.setTenantId(1L);owner=mock(SystemDepartmentMigrationApi.class);var tasks=mock(SyncTaskService.class);
        when(tasks.required(12L)).thenReturn(new SyncTaskDO().setAdapter("DPPMS_DEPARTMENTS").setSourceSystem("DPPMS"));
        when(owner.getCatalogue()).thenReturn(List.of());
        when(owner.preview(any())).thenAnswer(invocation->{request=invocation.getArgument(0);return request.entries().stream().map(entry->new SystemDepartmentMigrationApi.Result(
                entry.object(),entry.sourceKey(),null,new SystemDepartmentMigrationApi.Department(null,entry.code(),entry.name(),0L,0,null,null,null,entry.status(),0,null,entry.createTime(),null,entry.updateTime()),"CREATED")).toList();});
        adapter=new DppmsDepartmentSyncAdapter(owner,tasks);
    }
    @AfterEach void clear(){TenantContextHolder.clear();}
    private Row row(String code,Object ehrId,Object ehrDisabled){
        Map<String,Object> fields=new LinkedHashMap<>();DppmsDepartmentSyncTemplate.ORIGINAL_COLUMNS.forEach(column->fields.put(column,null));
        fields.put("id",9);fields.put("departmentNum",code);fields.put("departmentName","原部门");fields.put("isparam",1);fields.put("status",1);
        fields.put("createTime","2015-05-20 15:40:05");fields.put("effectiveFrom","2015-05-21 15:40:05");
        fields.put("migrationEhrDepartmentId",ehrId);fields.put("migrationEhrDisabled",ehrDisabled);return new Row("fnd_department","9",fields,null);
    }
    private Batch batch(Row row){return new Batch("integration:1:12",List.of(row),List.of(),true,"RETAIN",false,"UPSERT",false);}
    @Test void approvedParentsHistoryAndExcludedValidityDoNotBecomeNewAttributes(){
        adapter.preview(batch(row("163120",null,null)));var entry=request.entries().getFirst();
        assertEquals("160000",entry.parentCode());assertEquals(entry.createTime(),entry.updateTime());assertEquals(20,entry.createTime().getDayOfMonth());
        adapter.preview(batch(row("310500001001",null,null)));assertEquals("310000",request.entries().getFirst().parentCode());
        adapter.preview(batch(row("280700",null,null)));assertEquals("280000",request.entries().getFirst().parentCode());
        adapter.preview(batch(row("999999",null,null)));assertNull(request.entries().getFirst().parentCode());verify(owner,never()).apply(any());
    }
    @Test void disabledExceptionsUseExactOriginalEhrReferenceAndNeverDefaultNullToEnabled(){
        adapter.preview(batch(row("160700002",541,1)));assertEquals(1,request.entries().getFirst().status());
        adapter.preview(batch(row("310500001",391,1)));assertEquals(1,request.entries().getFirst().status());
        assertThrows(IllegalArgumentException.class,()->adapter.preview(batch(row("160700002",541,null))));
        assertThrows(IllegalArgumentException.class,()->adapter.preview(batch(row("310500001",392,1))));
    }
    @Test void unknownParentIncompleteOriginalFieldsAndUnapprovedDestructiveModesBlock(){
        assertThrows(IllegalArgumentException.class,()->adapter.preview(batch(row("999998",null,null))));
        Row original=row("163120",null,null);original.fields().remove("isparam");assertThrows(IllegalArgumentException.class,()->adapter.preview(batch(original)));
        assertThrows(IllegalArgumentException.class,()->adapter.preview(new Batch("integration:1:12",List.of(row("163120",null,null)),List.of(),true,"RETAIN",false,"UPSERT",true)));
    }
}
