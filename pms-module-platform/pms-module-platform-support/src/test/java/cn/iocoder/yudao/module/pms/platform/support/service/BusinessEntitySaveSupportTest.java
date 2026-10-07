package cn.iocoder.yudao.module.pms.platform.support.service;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BusinessEntitySaveSupportTest {
    @Test void refusesAnExtensionOrFixedWriteWithoutAnOwnerTransaction() {
        var extensions = mock(EntityExtensionApi.class);
        var fixedWritten = new AtomicBoolean();
        var support = new BusinessEntitySaveSupport(extensions);
        var error = assertThrows(BusinessContractException.class, () -> support.save(
                EntityDataRef.current(new EntityRef(1L,"SOL","test",3L)), new EntityActor(1L,2L,null),0L,
                new BusinessEntitySaveSupport.ExtensionPatch(4L,0,Map.of("flag",false)),
                () -> { fixedWritten.set(true); return null; }));
        assertEquals("TRANSACTION_REQUIRED", error.getErrorCode());
        assertFalse(fixedWritten.get());
        verifyNoInteractions(extensions);
    }
}
