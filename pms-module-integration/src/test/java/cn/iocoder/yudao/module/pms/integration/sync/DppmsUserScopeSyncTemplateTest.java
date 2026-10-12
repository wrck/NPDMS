package cn.iocoder.yudao.module.pms.integration.sync;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DppmsUserScopeSyncTemplateTest {
    @Test void allOriginalRelationFieldsCompileWithCorrectCompanyForeignKeyDomain(){
        var definition=DppmsUserScopeSyncTemplate.create(9L);assertEquals(5,definition.sources().size());
        for(var source:definition.sources()){assertDoesNotThrow(()->MysqlSyncReader.compile(source));assertFalse(source.sql().toLowerCase().contains("password"));}
        assertEquals(10,definition.sources().getLast().mappings().size());
        assertTrue(definition.sources().get(3).sql().contains("t_company c ON c.id=r.comp_id"));
        assertFalse(definition.sources().get(3).sql().contains("ehr_company"));assertFalse(definition.clearBeforeLoad());assertEquals("RETAIN",definition.missingPolicy());
    }
}
