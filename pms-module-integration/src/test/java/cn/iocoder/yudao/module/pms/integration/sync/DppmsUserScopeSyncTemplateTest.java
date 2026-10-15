package cn.iocoder.yudao.module.pms.integration.sync;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DppmsUserScopeSyncTemplateTest {
    @Test void allOriginalRelationFieldsCompileWithCorrectCompanyForeignKeyDomain(){
        var definition=DppmsUserScopeSyncTemplate.create(9L);assertEquals(5,definition.sources().size());
        for(var source:definition.sources()){
            if(SyncSourceEvidenceReader.reference(source)){assertDoesNotThrow(()->SyncSourceEvidenceReader.validate(definition,source));assertNull(source.sql());}
            else {assertDoesNotThrow(()->MysqlSyncReader.compile(source));assertFalse(source.sql().toLowerCase().contains("password"));}
        }
        assertEquals(10,definition.sources().getLast().mappings().size());
        assertEquals("DPPMS_USER_ROLES",definition.sources().get(3).evidenceAdapter());
        assertEquals("T_USER_ROLE",definition.sources().get(3).evidenceObject());
        assertTrue(DppmsUserRoleSyncTemplate.create(9L).sources().getFirst().sql().contains("t_company c ON c.id=r.comp_id"));
        assertFalse(definition.clearBeforeLoad());assertEquals("RETAIN",definition.missingPolicy());
    }
}
