package cn.iocoder.yudao.module.pms.integration.basesync;

import cn.iocoder.yudao.module.pms.integration.api.basesync.*;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OriginalAuditNamesTest {
    @lombok.Data static class Row implements SyncReferences.SourceAudit {
        String sourceCreator,sourceUpdater,resolvedCreator,resolvedUpdater;
        public boolean requiresResolvedAuditReferences(){return true;}
        public boolean usesAuditCatalogue(){return true;}
    }
    static class Task extends AbstractSyncTask<Row,Void> {
        public String taskCode(){return "BASE_TEST";}
        public String taskName(){return "raw audit";}
        void prepare(SyncBatch<Row,Void> batch){syncDataReferences(batch);}
    }
    @Test void rawUsernamesIncludingWhitespaceEmptyAndNullNeedNoAccountQuery() {
        var references=mock(SyncReferences.class);
        when(references.findAuditUserCatalogue(any())).thenReturn(List.of(new SyncReferences.AuditUser(99,"Admin")));
        var c=new SyncContext<Void>(1,2,3,true,500,1000,LocalDateTime.now(),mock(SyncSource.class),mock(SyncIdentityStore.class),references,"Audit",mock(SqlSession.class),false,true);
        var first=new Row().setSourceCreator("Admin").setSourceUpdater(" 未开户User ");
        var second=new Row().setSourceCreator("").setSourceUpdater(null);
        new Task().prepare(new SyncBatch<>(c,List.of(first,second)));
        assertEquals("Admin",first.getResolvedCreator());assertEquals(" 未开户User ",first.getResolvedUpdater());
        assertEquals("",second.getResolvedCreator());assertNull(second.getResolvedUpdater());verifyNoInteractions(references);
    }
}
