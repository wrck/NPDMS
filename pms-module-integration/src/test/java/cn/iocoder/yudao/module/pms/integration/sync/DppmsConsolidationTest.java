package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import cn.iocoder.yudao.module.system.api.organization.ManagedOrganizationApi;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DppmsConsolidationTest {
    @Test void canonicalAliasesWriteOneOwnerRecordAndLateBindingConflictRollsBack() {
        var owner=mock(ManagedOrganizationApi.class);var ehr=new EhrOrganizationAdapter(owner);
        var fnd=mock(DppmsDepartmentSyncAdapter.class);var completion=mock(DppmsEhrDepartmentCompletionAdapter.class);
        var references=mock(DppmsCompanyReferenceSyncAdapter.class);
        var empty=new DataSyncAdapter.Descriptor("test","test",List.of(),List.of(),List.of(),false);
        for(var component:List.<DataSyncAdapter>of(fnd,completion,references))when(component.descriptor()).thenReturn(empty);
        var rows=new ArrayList<DataSyncAdapter.Row>();
        for(String key:List.of("7","8")) {
            var fields=new LinkedHashMap<String,Object>();DppmsOrganizationProjection.METADATA.forEach(field->fields.put(field,null));
            fields.putAll(Map.of("code","01","name","8".equals(key)?"选定整行":"原别名","status",0,
                    "_organizationWinnerObject","COMPANY","_organizationWinnerKey","8","_organizationBusinessCode","01"));
            rows.add(new DataSyncAdapter.Row("COMPANY",key,fields,null));
        }
        var adapter=DppmsConsolidation.bind("DPPMS_ORGANIZATION",Map.of("EHR_ORGANIZATION",ehr,"DPPMS_DEPARTMENTS",fnd,
                "DPPMS_EHR_DEPARTMENT_COMPLETION",completion,"DPPMS_COMPANY_REFERENCES",references));
        when(owner.preview(any())).thenAnswer(call->{ManagedOrganizationApi.Command command=call.getArgument(0);
            assertEquals(1,command.entries().size());assertEquals("COMPANY:8",command.entries().getFirst().key());
            assertEquals("选定整行",command.entries().getFirst().name());assertNull(command.entries().getFirst().targetId());
            var node=new ManagedOrganizationApi.Node(command.entries().getFirst().entity().company(),null,command.owner());
            return List.of(new ManagedOrganizationApi.Result("COMPANY:8",null,node,"CREATED"));});
        var batch=new DataSyncAdapter.Batch("integration:1:12",rows,List.of(),true,"RETAIN",false,"UPSERT",false);
        var preview=adapter.preview(batch);assertEquals(2,preview.size());assertTrue(preview.stream().allMatch(change->change.targetId()==null));
        verify(owner,never()).apply(any());
        var source=new org.springframework.jdbc.datasource.DriverManagerDataSource("jdbc:h2:mem:organization-alias-"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa","");
        var jdbc=new org.springframework.jdbc.core.JdbcTemplate(source);jdbc.execute("CREATE TABLE writes(id BIGINT)");
        when(owner.apply(any())).thenAnswer(call->{ManagedOrganizationApi.Command command=call.getArgument(0);
            jdbc.update("INSERT INTO writes VALUES(777)");var company=command.entries().getFirst().entity().company().setId(777L);
            return List.of(new ManagedOrganizationApi.Result("COMPANY:8",null,new ManagedOrganizationApi.Node(company,null,command.owner()),"CREATED"));});
        var conflictRows=new ArrayList<>(rows);conflictRows.set(0,new DataSyncAdapter.Row("COMPANY","7",rows.getFirst().fields(),778L));
        var conflict=new DataSyncAdapter.Batch(batch.owner(),conflictRows,List.of(),true,"RETAIN",false,"UPSERT",false);
        var tx=new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(source));
        assertThrows(IllegalArgumentException.class,()->tx.execute(status->adapter.apply(conflict)));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM writes",Integer.class));
        verify(owner).apply(any());
    }
    @Test void precedingOwnerResultsReachBothComponentsAndApplyKeepsItsExistingPath() {
        var ehr=mock(EhrOrganizationAdapter.class);var fnd=mock(DppmsDepartmentSyncAdapter.class);
        var completion=mock(DppmsEhrDepartmentCompletionAdapter.class);
        var references=mock(DppmsCompanyReferenceSyncAdapter.class);
        var descriptor=new DataSyncAdapter.Descriptor("test","test",List.of(),List.of(),List.of(),false);
        for(var component:List.<DataSyncAdapter>of(ehr,fnd,completion,references))when(component.descriptor()).thenReturn(descriptor);
        var ownerResults=List.of(new ManagedOrganizationApi.Result("parent",null,null,"CREATED"));
        when(ehr.previewPlan(any())).thenReturn(new EhrOrganizationAdapter.PreviewPlan(List.of(),ownerResults));
        when(fnd.previewAfterOrganization(any())).thenReturn(List.of());when(completion.previewAfterOrganization(any())).thenReturn(List.of());
        when(references.previewAfterOrganization(any())).thenReturn(List.of());
        var adapter=DppmsConsolidation.bind("DPPMS_ORGANIZATION",Map.of("EHR_ORGANIZATION",ehr,"DPPMS_DEPARTMENTS",fnd,"DPPMS_EHR_DEPARTMENT_COMPLETION",completion,"DPPMS_COMPANY_REFERENCES",references));
        var batch=new DataSyncAdapter.Batch("owner",List.of(),List.of(),true,"RETAIN",false,"UPSERT",false);
        adapter.preview(batch);
        var captured=org.mockito.ArgumentCaptor.forClass(DppmsConsolidation.OrganizationPreviewBatch.class);
        verify(fnd).previewAfterOrganization(captured.capture());assertSame(ownerResults,captured.getValue().preceding());
        verify(completion).previewAfterOrganization(captured.capture());assertSame(ownerResults,captured.getValue().preceding());
        verify(references).previewAfterOrganization(captured.capture());assertSame(ownerResults,captured.getValue().preceding());
        clearInvocations(ehr,fnd,completion);
        for(var component:List.<DataSyncAdapter>of(ehr,fnd,completion))when(component.apply(any())).thenReturn(List.of());
        adapter.apply(batch);verify(ehr).apply(any());verify(fnd).apply(any());verify(completion).apply(any());
        verify(ehr,never()).previewPlan(any());verify(fnd,never()).previewAfterOrganization(any());verify(completion,never()).previewAfterOrganization(any());
        clearInvocations(ehr,fnd,completion);
        var conflict=new DataSyncAdapter.Change("DEPARTMENT","p",null,"CONFLICT",Map.of(),Map.of(),"conflict");
        when(ehr.previewPlan(any())).thenReturn(new EhrOrganizationAdapter.PreviewPlan(List.of(conflict),List.of()));
        assertEquals(List.of(conflict),adapter.preview(batch));
        verify(fnd,never()).previewAfterOrganization(any());verify(completion,never()).previewAfterOrganization(any());
    }
}
