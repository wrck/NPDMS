package cn.iocoder.yudao.module.pms.engineering.service.collection;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.device.DeviceConfigurationLogArchiveApi;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.collection.ImplementationCollectionLogDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.configuration.ConfigurationDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.collection.ImplementationCollectionLogMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.configuration.ConfigurationCollectionOwnerMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.jointtest.JointTestCollectionOwnerMapper;
import cn.iocoder.yudao.module.pms.platform.api.collection.*;
import org.junit.jupiter.api.*;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeviceConfigurationLogArchiveBridgeTest {
    final ImplementationCollectionLogMapper logs=mock(ImplementationCollectionLogMapper.class);
    final ConfigurationCollectionOwnerMapper configurations=mock(ConfigurationCollectionOwnerMapper.class);
    final DeviceConfigurationLogArchiveApi archives=mock(DeviceConfigurationLogArchiveApi.class);
    final PlatformTransactionManager transactions=mock(PlatformTransactionManager.class);
    final SimpleTransactionStatus transaction=new SimpleTransactionStatus();
    CollectionBusinessResultReceiver receiver;
    @BeforeEach @SuppressWarnings("unchecked") void setup() {
        TenantContextHolder.setTenantId(1L);
        var owner=new ConfigurationDO(); owner.setId(30L);owner.setTenantId(1L);owner.setProjectId(10L);owner.setEquipmentId(20L);
        when(configurations.lockById(30L)).thenReturn(owner);
        when(logs.insert(any(ImplementationCollectionLogDO.class))).thenReturn(1);
        when(transactions.getTransaction(any())).thenReturn(transaction);
        ObjectProvider<DeviceConfigurationLogArchiveApi> provider=mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(archives);
        var service=new ImplementationCollectionLogService(logs,configurations,mock(JointTestCollectionOwnerMapper.class),List.of());
        var factory=new AspectJProxyFactory(service);
        factory.addAspect(new DeviceConfigurationLogArchiveBridge(provider,transactions));
        receiver=factory.getProxy();
    }
    @AfterEach void clear() {TenantContextHolder.clear();}
    @Test void successfulNativeReceiverAndArchiveShareOneTransaction() {
        receiver.receive(result("SUCCEEDED"));
        var ordered=inOrder(logs,archives,transactions);
        ordered.verify(logs).insert(any(ImplementationCollectionLogDO.class));
        ordered.verify(archives).receiveCollection(new DeviceConfigurationLogArchiveApi.CollectionLog(1L,7L,10L,20L,"task",1L,100L));
        ordered.verify(transactions).commit(transaction);
    }
    @Test void archiveFailureRollsBackNativeReception() {
        doThrow(new IllegalStateException("storage unavailable")).when(archives).receiveCollection(any());
        assertThrows(IllegalStateException.class,()->receiver.receive(result("SUCCEEDED")));
        verify(transactions).rollback(transaction);verify(transactions,never()).commit(any());
    }
    @Test void failedResultKeepsLegacyReceptionAndInvalidInputKeepsLegacyValidation() {
        receiver.receive(result("FAILED"));
        verify(logs).insert(any(ImplementationCollectionLogDO.class));verifyNoInteractions(archives,transactions);
        assertThrows(CollectionOperationException.class,()->receiver.receive(null));
    }
    private CollectionBusinessResultReceiver.Result result(String status) {
        return new CollectionBusinessResultReceiver.Result(1L,"configuration",30L,"IMP","Configuration",10L,20L,
                50L,7L,"task",1L,100L,"SSH",status,null,"show tech",null);
    }
}
