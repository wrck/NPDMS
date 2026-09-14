package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import cn.iocoder.yudao.module.system.api.organization.ManagedOrganizationApi;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EhrOrganizationAdapterTest {
    private DataSyncAdapter.Binding binding(){return new DataSyncAdapter.Binding("COMPANY","1",101L,Map.of("code","001","name","公司","status",0));}
    @Test void fullMissingDisablesButIncrementalMissingDoesNot() {
        var api=mock(ManagedOrganizationApi.class);when(api.preview(any())).thenReturn(List.of());
        var adapter=new EhrOrganizationAdapter(api);
        adapter.preview(new DataSyncAdapter.Batch("owner",List.of(),List.of(binding()),true,"DISABLE",false,"UPSERT",false));
        adapter.preview(new DataSyncAdapter.Batch("owner",List.of(),List.of(binding()),false,"DISABLE",false,"UPSERT",false));
        var capture=ArgumentCaptor.forClass(ManagedOrganizationApi.Command.class);
        verify(api,times(2)).preview(capture.capture());
        assertEquals(1,capture.getAllValues().get(0).entries().getFirst().entity().company().getStatus());
        assertEquals(0,capture.getAllValues().get(1).entries().getFirst().entity().company().getStatus());
    }
    @Test void reappearingSourceUsesSameTargetIdentityAndSourceStatus() {
        var api=mock(ManagedOrganizationApi.class);when(api.preview(any())).thenReturn(List.of());
        var adapter=new EhrOrganizationAdapter(api);
        var old=new DataSyncAdapter.Binding("COMPANY","1",101L,Map.of("code","001","name","公司","status",1));
        var row=new DataSyncAdapter.Row("COMPANY","1",Map.of("code","001","name","公司新版","status",0),101L);
        adapter.preview(new DataSyncAdapter.Batch("owner",List.of(row),List.of(old),true,"DISABLE",false,"UPSERT",false));
        var capture=ArgumentCaptor.forClass(ManagedOrganizationApi.Command.class);verify(api).preview(capture.capture());
        var entity=capture.getValue().entries().getFirst().entity().company();
        assertEquals(101L,entity.getId());assertEquals(0,entity.getStatus());assertEquals("公司新版",entity.getName());
    }
    @Test void unknownParentCannotBeSilentlyTurnedIntoARoot() {
        var api=mock(ManagedOrganizationApi.class);var adapter=new EhrOrganizationAdapter(api);
        var row=new DataSyncAdapter.Row("DEPARTMENT","2",Map.of("code","D","name","部门","status",0,"sort",2,"parentKey",999),null);
        assertThrows(IllegalArgumentException.class,()->adapter.preview(new DataSyncAdapter.Batch("owner",List.of(row),List.of(),true,"DISABLE",false,"UPSERT",false)));
        verifyNoInteractions(api);
    }
}
