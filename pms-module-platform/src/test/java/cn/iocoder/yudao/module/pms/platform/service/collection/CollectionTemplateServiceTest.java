package cn.iocoder.yudao.module.pms.platform.service.collection;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.collection.CollectionTemplateDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.CollectionTemplateMapper;
import cn.iocoder.yudao.module.pms.integration.api.deviceops.DeviceOpsResourceApi;
import cn.iocoder.yudao.module.pms.asset.api.device.ProjectDeviceSelectionApi;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.SelectedProjectDevice;
import cn.iocoder.yudao.module.pms.platform.api.collection.CollectionOperationException;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class CollectionTemplateServiceTest {
    CollectionTemplateMapper mapper=mock(CollectionTemplateMapper.class);
    CollectionAuthorization auth=mock(CollectionAuthorization.class);
    ProjectDeviceSelectionApi devices=mock(ProjectDeviceSelectionApi.class);
    DeviceOpsResourceApi provider=mock(DeviceOpsResourceApi.class);
    PlatformTransactionManager tx=mock(PlatformTransactionManager.class);
    CollectionTemplateService service;
    CollectionTemplateDO row;
    @BeforeEach void setup(){
        TenantContextHolder.setTenantId(1L);var resources=mock(ObjectProvider.class);when(resources.getIfAvailable()).thenReturn(provider);
        when(tx.getTransaction(any())).thenReturn(new SimpleTransactionStatus());service=new CollectionTemplateService(mapper,auth,resources,tx,devices);
        row=new CollectionTemplateDO();row.setId(3L);row.setTenantId(1L);row.setVersion(0L);row.setTemplateCode("template");row.setName("show run");row.setRevision(1);row.setPurpose("joint-test");row.setProtocol("SSH");row.setCommandText("show run");row.setContentHash(CollectionTemplateService.hash("show run"));row.setStatus("DRAFT");row.setPublicationStarted(false);
        when(mapper.lockById(1L,3L)).thenReturn(row);when(mapper.updateById(any(CollectionTemplateDO.class))).thenReturn(1);
    }
    @AfterEach void clear(){TenantContextHolder.clear();}
    @Test void executionChoicesKeepOnlyGenericAndExactDeviceModelTemplates(){
        var generic=new CollectionTemplateDO();generic.setId(4L);
        var matched=new CollectionTemplateDO();matched.setId(5L);matched.setDeviceModel("model-a");
        var other=new CollectionTemplateDO();other.setId(6L);other.setDeviceModel("QA-VIRTUAL-FW");
        when(mapper.list(any())).thenReturn(List.of(generic,matched,other));
        when(devices.validateSelection(21L,List.of(31L))).thenReturn(List.of(new SelectedProjectDevice(31L,"sn","device",null,"model-a",null)));
        var choices=service.list(CollectionTemplateService.ListQuery.builder().actorId(7L).purpose("cutover-checklist").publishedOnly(true).projectId(21L).deviceId(31L).build());
        assertEquals(List.of(4L,5L),choices.stream().map(CollectionTemplateService.View::id).toList());
        verify(auth).project(7L,21L,false,false);
        verify(auth).permission(7L,"pms:collection-template:use");
        assertEquals(3,service.list(CollectionTemplateService.ListQuery.builder().actorId(7L).purpose("cutover-checklist").publishedOnly(true).build()).size());
    }
    @Test void executionChoicesRejectIncompleteOrUnauthorizedDeviceScopeBeforeListing(){
        assertThrows(CollectionOperationException.class,()->service.list(CollectionTemplateService.ListQuery.builder().actorId(7L).projectId(21L).build()));
        assertThrows(CollectionOperationException.class,()->service.list(CollectionTemplateService.ListQuery.builder().actorId(7L).deviceId(31L).build()));
        doThrow(new org.springframework.security.access.AccessDeniedException("denied")).when(auth).project(7L,21L,false,false);
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->service.list(CollectionTemplateService.ListQuery.builder().actorId(7L).projectId(21L).deviceId(31L).build()));
        verify(mapper,never()).list(any());
        verifyNoInteractions(devices);
    }
    @Test void mismatchedPublishedTemplateReportsDeviceModelRatherThanUnpublished(){
        row.setStatus("PUBLISHED");row.setPurpose("cutover-checklist");row.setDeviceModel("QA-VIRTUAL-FW");
        var failure=assertThrows(CollectionOperationException.class,()->service.forExecution(CollectionTemplateService.ExecutionQuery.builder().actorId(7L).templateId(3L).entry("cutover-checklist").deviceModel("SFPP-LX10-SM1310").build()));
        assertTrue(failure.getMessage().contains("设备型号"));
        assertFalse(failure.getMessage().contains("未发布"));
    }
    @Test void cutoverStageDraftsHaveNoProtocolAndRetainExactPurposeAndCutOwner(){
        for(var purpose:java.util.List.of("cutover-checklist","cutover-pre-check","cutover-execution","cutover-test","cutover-rollback","cutover-post-collection")) {
            var saved=service.save(7L,new CollectionTemplateService.Draft(null,null,"cut-template","采集草稿",purpose,"model-a",1,"test-command"));
            assertEquals(purpose,saved.purpose());assertEquals("CUT",saved.ownerContext());assertEquals("DRAFT",saved.status());
        }
        verify(mapper,times(6)).insert(argThat((CollectionTemplateDO value)->value.getProtocol()==null));
        assertThrows(IllegalStateException.class,()->service.save(7L,new CollectionTemplateService.Draft(null,null,"cut-template","采集草稿","cutover-unknown",null,1,"test-command")));
    }
    @Test void cutoverChecklistExecutionCannotUseAnotherStageOrUnpublishedTemplate(){
        row.setPurpose("cutover-checklist");
        assertThrows(IllegalStateException.class,()->service.forExecution(CollectionTemplateService.ExecutionQuery.builder().actorId(7L).templateId(3L).entry("cutover-checklist").deviceModel(null).build()));
        row.setStatus("PUBLISHED");
        assertSame(row,service.forExecution(CollectionTemplateService.ExecutionQuery.builder().actorId(7L).templateId(3L).entry("cutover-checklist").deviceModel(null).build()));
        assertThrows(IllegalStateException.class,()->service.forExecution(CollectionTemplateService.ExecutionQuery.builder().actorId(7L).templateId(3L).entry("cutover-pre-check").deviceModel(null).build()));
    }
    @Test void uncertainPublishFreezesBeforeIoAndRetryRegistersSameImmutableContent(){
        doThrow(new IllegalStateException("lost response")).doNothing().when(provider).registerScript(anyString(),anyString(),anyString(),anyString());
        assertThrows(IllegalStateException.class,()->service.publish(7L,3L,0L));
        assertTrue(row.getPublicationStarted());assertEquals("DRAFT",row.getStatus());
        var ordered=inOrder(tx,provider);ordered.verify(tx).commit(any());ordered.verify(provider).registerScript("plt-3","1","show run",row.getContentHash());
        assertThrows(IllegalStateException.class,()->service.save(7L,new CollectionTemplateService.Draft(3L,0L,"template","new","joint-test",null,1,"show tech")));
        assertEquals("PUBLISHED",service.publish(7L,3L,0L).status());
        verify(provider,times(2)).registerScript("plt-3","1","show run",row.getContentHash());
    }
    @Test void purposeAndModelAreEnforcedAndRetirementPreservesCommands(){
        row.setStatus("PUBLISHED");row.setDeviceModel("model-a");
        assertThrows(IllegalStateException.class,()->service.forExecution(CollectionTemplateService.ExecutionQuery.builder().actorId(7L).templateId(3L).entry("configuration").deviceModel("model-a").build()));
        assertThrows(IllegalStateException.class,()->service.forExecution(CollectionTemplateService.ExecutionQuery.builder().actorId(7L).templateId(3L).entry("joint-test").deviceModel("model-b").build()));
        assertSame(row,service.forExecution(CollectionTemplateService.ExecutionQuery.builder().actorId(7L).templateId(3L).entry("joint-test").deviceModel("model-a").build()));
        assertEquals("SSH",row.getProtocol()); // Historical template metadata is preserved, not an execution constraint.
        row.setProtocol(null);
        assertSame(row,service.forExecution(CollectionTemplateService.ExecutionQuery.builder().actorId(7L).templateId(3L).entry("joint-test").deviceModel("model-a").build()));
        service.retire(7L,3L,0L);assertEquals("show run",row.getCommandText());
        assertThrows(IllegalStateException.class,()->service.forExecution(CollectionTemplateService.ExecutionQuery.builder().actorId(7L).templateId(3L).entry("joint-test").deviceModel("model-a").build()));
    }
}
