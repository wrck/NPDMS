package cn.iocoder.yudao.module.pms.platform.service.collection;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.collection.CollectionTemplateDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.CollectionTemplateMapper;
import cn.iocoder.yudao.module.pms.integration.api.deviceops.DeviceOpsResourceApi;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class CollectionTemplateServiceTest {
    CollectionTemplateMapper mapper=mock(CollectionTemplateMapper.class);
    CollectionAuthorization auth=mock(CollectionAuthorization.class);
    DeviceOpsResourceApi provider=mock(DeviceOpsResourceApi.class);
    PlatformTransactionManager tx=mock(PlatformTransactionManager.class);
    CollectionTemplateService service;
    CollectionTemplateDO row;
    @BeforeEach void setup(){
        TenantContextHolder.setTenantId(1L);var resources=mock(ObjectProvider.class);when(resources.getIfAvailable()).thenReturn(provider);
        when(tx.getTransaction(any())).thenReturn(new SimpleTransactionStatus());service=new CollectionTemplateService(mapper,auth,resources,tx);
        row=new CollectionTemplateDO();row.setId(3L);row.setTenantId(1L);row.setVersion(0L);row.setTemplateCode("template");row.setName("show run");row.setRevision(1);row.setPurpose("joint-test");row.setProtocol("SSH");row.setCommandText("show run");row.setContentHash(CollectionTemplateService.hash("show run"));row.setStatus("DRAFT");row.setPublicationStarted(false);
        when(mapper.lockById(1L,3L)).thenReturn(row);when(mapper.updateById(any(CollectionTemplateDO.class))).thenReturn(1);
    }
    @AfterEach void clear(){TenantContextHolder.clear();}
    @Test void uncertainPublishFreezesBeforeIoAndRetryRegistersSameImmutableContent(){
        doThrow(new IllegalStateException("lost response")).doNothing().when(provider).registerScript(anyString(),anyString(),anyString(),anyString());
        assertThrows(IllegalStateException.class,()->service.publish(7L,3L,0L));
        assertTrue(row.getPublicationStarted());assertEquals("DRAFT",row.getStatus());
        var ordered=inOrder(tx,provider);ordered.verify(tx).commit(any());ordered.verify(provider).registerScript("plt-3","1","show run",row.getContentHash());
        assertThrows(IllegalStateException.class,()->service.save(7L,new CollectionTemplateService.Draft(3L,0L,"template","new","joint-test","SSH",null,1,"show tech")));
        assertEquals("PUBLISHED",service.publish(7L,3L,0L).status());
        verify(provider,times(2)).registerScript("plt-3","1","show run",row.getContentHash());
    }
    @Test void purposeProtocolAndModelAreEnforcedAndRetirementPreservesCommands(){
        row.setStatus("PUBLISHED");row.setDeviceModel("model-a");
        assertThrows(IllegalStateException.class,()->service.forExecution(7L,3L,"configuration","SSH","model-a"));
        assertThrows(IllegalStateException.class,()->service.forExecution(7L,3L,"joint-test","TELNET","model-a"));
        assertThrows(IllegalStateException.class,()->service.forExecution(7L,3L,"joint-test","SSH","model-b"));
        assertSame(row,service.forExecution(7L,3L,"joint-test","SSH","model-a"));
        service.retire(7L,3L,0L);assertEquals("show run",row.getCommandText());
        assertThrows(IllegalStateException.class,()->service.forExecution(7L,3L,"joint-test","SSH","model-a"));
    }
}
