package cn.iocoder.yudao.module.pms.integration.sync;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DppmsUserSyncTemplateTest {
    @Test void everyOriginalNonCredentialColumnIsExplicitlyReadAndMapped() {
        var template = DppmsUserSyncTemplate.create(9L);
        assertEquals("DPPMS_USERS",template.adapter()); assertEquals(3,template.sources().size());
        assertEquals(58,DppmsUserSyncTemplate.COLUMNS.values().stream().mapToInt(List::size).sum());
        for (var source : template.sources()) {
            if(source.queryRef()==null){
                assertFalse(source.sql().toLowerCase(Locale.ROOT).contains("password"));
                assertFalse(source.sql().contains("u.*"));
                assertDoesNotThrow(() -> MysqlSyncReader.compile(source));
            }else{assertNull(source.sql());assertEquals("t_user",source.queryRef());}
            assertTrue(new HashSet<>(source.mappings().stream().map(SyncDefinition.Mapping::source).toList())
                    .containsAll(DppmsUserSyncTemplate.COLUMNS.get(source.object())));
        }
    }
    @Test void referencesUseExplicitEhrEmployeeForeignKeyAndTheCorrectCompanyIdentityDomain() {
        var sources = DppmsUserSyncTemplate.create(9L).sources();
        assertTrue(sources.getFirst().sql().contains("e.empID=l.empID"));
        assertTrue(sources.getFirst().sql().contains("COUNT(*) AS loginCount"));
        assertTrue(sources.getFirst().sql().contains("t_company c ON c.id=u.compID"));
        assertTrue(sources.getFirst().sql().contains("e.empID=a.userCustom4"));
        assertTrue(sources.getFirst().sql().contains("CAST(e.workNo AS BINARY)=CAST(u.workNo AS BINARY)"));
        assertFalse(sources.getFirst().sql().contains("ehr_company"));
        assertEquals(1,sources.getFirst().sql().split("FROM ehr_login",-1).length-1);
    }
    @Test void dependentAccountsReconcileAsWholeSnapshotWithoutClearingOrOldIdReuse() {
        var template = DppmsUserSyncTemplate.create(9L);
        assertEquals("ONCE",template.mode()); assertEquals("RETAIN",template.missingPolicy()); assertEquals("UPSERT",template.loadingMode());
        assertFalse(template.clearBeforeLoad()); assertFalse(template.resetMappingsBeforeLoad()); assertFalse(template.autoPaging());
        assertEquals("SNAPSHOT",template.effectiveReadStrategy());
        assertTrue(template.sources().stream().noneMatch(SyncDefinition.Source::syncPrimaryKey));
        assertEquals("user_id",template.sources().getFirst().sourceKey());
        // Old admin ID0 is a source identity; positive numeric paging would incorrectly omit it.
        assertTrue(template.sources().getFirst().sql().contains("user_id"));
    }
}
