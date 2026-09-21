package cn.iocoder.yudao.module.pms.engineering.service.collection;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.collection.ImplementationCollectionLogDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.configuration.ConfigurationDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.jointtest.JointTestDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.collection.ImplementationCollectionLogMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.configuration.ConfigurationCollectionOwnerMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.jointtest.JointTestCollectionOwnerMapper;
import cn.iocoder.yudao.module.pms.platform.api.collection.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ImplementationCollectionLogServiceTest {
    ImplementationCollectionLogMapper logs = mock(ImplementationCollectionLogMapper.class);
    ConfigurationCollectionOwnerMapper configurations = mock(ConfigurationCollectionOwnerMapper.class);
    JointTestCollectionOwnerMapper jointTests = mock(JointTestCollectionOwnerMapper.class);
    CollectionSourceAdapter source = mock(CollectionSourceAdapter.class);
    ImplementationCollectionLogService service;
    ConfigurationDO configuration;
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        configuration = new ConfigurationDO(); configuration.setId(11L); configuration.setTenantId(1L); configuration.setProjectId(21L); configuration.setEquipmentId(31L); configuration.setStatus(0);
        when(configurations.lockById(11L)).thenReturn(configuration);
        when(logs.insert(any(ImplementationCollectionLogDO.class))).thenReturn(1);
        when(source.entry()).thenReturn("configuration");
        service = new ImplementationCollectionLogService(logs, configurations, jointTests, List.of(source));
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }
    @Test void duplicateReturnKeepsOneImmutableReceiptAndNeverChangesBusinessState() {
        service.receive(result("configuration", "Configuration", "SUCCEEDED"));
        var saved = ArgumentCaptor.forClass(ImplementationCollectionLogDO.class); verify(logs).insert(saved.capture());
        when(logs.findResult(any())).thenReturn(saved.getValue());
        service.receive(result("configuration", "Configuration", "SUCCEEDED"));
        verify(logs, times(1)).insert(any(ImplementationCollectionLogDO.class));
        assertEquals(0, configuration.getStatus()); verify(configurations, never()).updateById(any(ConfigurationDO.class));
        saved.getValue().setFileVersionId(999L);
        assertThrows(CollectionOperationException.class, () -> service.receive(result("configuration", "Configuration", "SUCCEEDED")));
    }
    @Test void movedProjectOrDeviceAndCrossTenantFailClosed() {
        configuration.setProjectId(22L);
        assertThrows(CollectionOperationException.class, () -> service.receive(result("configuration", "Configuration", "SUCCEEDED")));
        configuration.setProjectId(21L); configuration.setEquipmentId(32L);
        assertThrows(CollectionOperationException.class, () -> service.receive(result("configuration", "Configuration", "SUCCEEDED")));
        configuration.setEquipmentId(31L); configuration.setTenantId(2L);
        assertThrows(CollectionOperationException.class, () -> service.receive(result("configuration", "Configuration", "SUCCEEDED")));
        verify(logs, never()).insert(any(ImplementationCollectionLogDO.class));
    }
    @Test void jointFailureAndCancelledLogsAreRetainedWithoutPassingJointTest() {
        var joint = new JointTestDO(); joint.setId(11L); joint.setTenantId(1L); joint.setProjectId(21L); joint.setEquipmentId(31L); joint.setStatus(1);
        when(jointTests.lockById(11L)).thenReturn(joint);
        service.receive(result("joint-test", "JointTest", "FAILED")); service.receive(result("joint-test", "JointTest", "CANCELLED"));
        verify(logs, times(2)).insert(any(ImplementationCollectionLogDO.class)); assertEquals(1, joint.getStatus());
        verify(jointTests, never()).updateById(any(JointTestDO.class));
    }
    @Test void currentBusinessPermissionIsRequiredForReadingReceipts() {
        when(source.authorize(any(), any(), any(), any(), any(), any())).thenThrow(new AccessDeniedException("denied"));
        assertThrows(AccessDeniedException.class, () -> service.page("configuration", 11L, 8L, 1, 10));
        verifyNoInteractions(logs);
    }
    private CollectionBusinessResultReceiver.Result result(String entry, String type, String status) {
        return new CollectionBusinessResultReceiver.Result(1L, entry, 11L, "IMP", type, 21L, 31L, 41L, 7L, "task", 1L, 51L, "SSH", status, null, "show run", "template");
    }
}
