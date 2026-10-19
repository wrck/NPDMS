package cn.iocoder.yudao.module.pms.integration.basesync.task;

import cn.iocoder.yudao.module.pms.integration.api.basesync.*;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityExtensionApi;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeferredAttributesMySqlTest extends RemainingTaskMySqlSupport {
    @Test void projectPartiesFinishWithoutOpeningDeferredStateOrItsDefinition() {
        var extensions=mock(EntityExtensionApi.class);
        var reads=new ArrayList<String>();
        var task=new P19ProjectFactsSyncTask(session.getMapper(P19ProjectFactsSyncTask.TargetMapper.class),projects) {
            @Override protected void syncDataQueryByRowHandler(SyncContext<Void> c,org.apache.ibatis.session.ResultHandler<Source> handler) {
                reads.add(c.dataName());
                if(!c.dataName().equals("ProjectParty"))throw new IllegalStateException("deferred state must not be read");
            }
        };
        assertEquals(0,run(task,true).read());
        assertEquals(List.of("ProjectParty"),reads);
        verifyNoInteractions(extensions);
        assertTrue(saved.isEmpty());
    }
}
