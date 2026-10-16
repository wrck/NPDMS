package cn.iocoder.yudao.module.pms.cutover.framework.config;

import cn.iocoder.yudao.module.pms.cutover.service.checklist.port.CutoverCollectionPort;
import cn.iocoder.yudao.module.pms.platform.api.collection.CollectionApplicationApi;
import cn.iocoder.yudao.module.pms.platform.api.collection.CollectionExecutionQueryApi;
import cn.iocoder.yudao.module.pms.platform.api.collection.CollectionOperationException;
import cn.iocoder.yudao.module.pms.platform.api.collection.dto.CollectionTaskDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CutoverChecklistCollectionAdapterTest {
    @Test void manualSelectionRequiresThisExactChecklistItemAndCannotMasqueradeAsTemplate(){
        var executions=mock(CollectionExecutionQueryApi.class);
        ObjectProvider<CollectionExecutionQueryApi> provider=mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(executions);
        var adapter=new CutoverChecklistCollectionAdapter(mock(ObjectProvider.class),provider);
        var manual=CutoverCollectionPort.Inspection.builder().tenantId(1L).actorId(7L).projectId(21L)
                .taskId(10L).checklistId(20L).checklistItemId(30L).deviceId(31L).collectionTaskId(41L).build();
        when(executions.get(any())).thenReturn(execution("manual-30"));
        assertEquals(CutoverCollectionPort.TechnicalStatus.ACCEPTED,adapter.inspect(manual).technicalStatus());
        when(executions.get(any())).thenReturn(execution("manual-32"));
        assertThrows(CollectionOperationException.class,()->adapter.inspect(manual));
        when(executions.get(any())).thenReturn(execution("50"));
        assertThrows(CollectionOperationException.class,()->adapter.inspect(manual));
        var template=CutoverCollectionPort.Inspection.builder().tenantId(1L).actorId(7L).projectId(21L)
                .taskId(10L).checklistId(20L).checklistItemId(30L).deviceId(31L).collectionTaskId(41L).commandTemplateId(50L).build();
        assertEquals(CutoverCollectionPort.TechnicalStatus.ACCEPTED,adapter.inspect(template).technicalStatus());
        when(executions.get(any())).thenReturn(execution("manual-30"));
        assertThrows(CollectionOperationException.class,()->adapter.inspect(template));
    }
    private CollectionApplicationApi.Execution execution(String template){
        var task=new CollectionTaskDTO(1L,2L,"task","CUT","CUTOVER_CHECKLIST_ITEM","30","21","31","sn",
                "fixture.example",22,"SSH",template,"v1","hash","TEMPORARY_SECRET",null,null,"request-key",
                "BUSINESS_CONSUMPTION","AUTHORIZED","AUTHORIZED",null,null,null,null,null,null,null);
        return new CollectionApplicationApi.Execution(41L,7L,null,null,task,"display version","手工命令",null);
    }
}
