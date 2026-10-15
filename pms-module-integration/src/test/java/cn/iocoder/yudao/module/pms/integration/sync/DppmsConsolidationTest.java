package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import cn.iocoder.yudao.module.system.api.organization.ManagedOrganizationApi;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DppmsConsolidationTest {
    @Test void precedingOwnerResultsReachBothComponentsAndApplyKeepsItsExistingPath() {
        var ehr=mock(EhrOrganizationAdapter.class);var fnd=mock(DppmsDepartmentSyncAdapter.class);
        var completion=mock(DppmsEhrDepartmentCompletionAdapter.class);
        var descriptor=new DataSyncAdapter.Descriptor("test","test",List.of(),List.of(),List.of(),false);
        for(var component:List.<DataSyncAdapter>of(ehr,fnd,completion))when(component.descriptor()).thenReturn(descriptor);
        var ownerResults=List.of(new ManagedOrganizationApi.Result("parent",null,null,"CREATED"));
        when(ehr.previewPlan(any())).thenReturn(new EhrOrganizationAdapter.PreviewPlan(List.of(),ownerResults));
        when(fnd.previewAfterOrganization(any())).thenReturn(List.of());when(completion.previewAfterOrganization(any())).thenReturn(List.of());
        var adapter=DppmsConsolidation.bind("DPPMS_ORGANIZATION",Map.of("EHR_ORGANIZATION",ehr,"DPPMS_DEPARTMENTS",fnd,"DPPMS_EHR_DEPARTMENT_COMPLETION",completion));
        var batch=new DataSyncAdapter.Batch("owner",List.of(),List.of(),true,"RETAIN",false,"UPSERT",false);
        adapter.preview(batch);
        var captured=org.mockito.ArgumentCaptor.forClass(DppmsConsolidation.OrganizationPreviewBatch.class);
        verify(fnd).previewAfterOrganization(captured.capture());assertSame(ownerResults,captured.getValue().preceding());
        verify(completion).previewAfterOrganization(captured.capture());assertSame(ownerResults,captured.getValue().preceding());
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
