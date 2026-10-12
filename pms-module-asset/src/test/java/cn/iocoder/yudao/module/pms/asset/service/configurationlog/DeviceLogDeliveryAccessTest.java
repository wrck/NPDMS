package cn.iocoder.yudao.module.pms.asset.service.configurationlog;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.*;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class DeviceLogDeliveryAccessTest {
    final DeviceLogFilePolicyProvider logs=mock(DeviceLogFilePolicyProvider.class);
    final DeviceLogDeliveryAccess service=new DeviceLogDeliveryAccess(logs);
    FileBusinessObjectPolicyFact fact(boolean allowed,long scope) {return new FileBusinessObjectPolicyFact(allowed,scope,"IMMUTABLE","SINGLE",Set.of("DEVICE_CONFIGURATION_LOG"),Set.of("text/plain"),100L,"INTERNAL");}
    @Test void nativeReadLocksExistingOwnerScope() {
        when(logs.inspect(any())).thenReturn(fact(true,3));when(logs.lockAndRevalidate(any())).thenReturn(fact(true,3));
        assertEquals(3L,service.requireDeliveryAccess(1L,7L,"device-log-file","20",DeviceLogDocumentSources.SOURCE,false,true,null));
        verify(logs).lockAndRevalidate(argThat(q->q.expectedScopeVersion()==3L && q.objectType().equals(DeviceLogFilePolicyProvider.OBJECT) && q.requiredAction().equals("DOWNLOAD")));
    }
    @Test void deniedScopeOrChangedScopeCannotIssueMaterialRead() {
        when(logs.inspect(any())).thenReturn(fact(false,3));assertThrows(RuntimeException.class,()->read(null));verify(logs,never()).lockAndRevalidate(any());
        when(logs.inspect(any())).thenReturn(fact(true,3));assertThrows(RuntimeException.class,()->read(4L));
        when(logs.lockAndRevalidate(any())).thenReturn(fact(true,4));assertThrows(RuntimeException.class,()->read(null));
    }
    @Test void siblingEntityWrongPurposeAndGenericWritesAreRejected() {
        assertFalse(service.supportsEntityType("device"));assertFalse(service.allowsGenericDeliveryActions("device-log-file"));
        assertThrows(RuntimeException.class,()->service.requireDeliveryAccess(1L,7L,"device-log-file","20",DeviceLogDocumentSources.SOURCE,true,true,null));
        assertThrows(RuntimeException.class,()->service.validateUpload(1L,7L,"device","20",DeviceLogDocumentSources.SOURCE,"DOWNLOAD",true,null));
        assertThrows(RuntimeException.class,()->service.validateUpload(1L,7L,"device-log-file","20","OTHER","DOWNLOAD",true,null));verifyNoInteractions(logs);
    }
    Long read(Long scope) {return service.requireDeliveryAccess(1L,7L,"device-log-file","20",DeviceLogDocumentSources.SOURCE,false,true,scope);}
}
