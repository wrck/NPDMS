package cn.iocoder.yudao.module.pms.integration.sync;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DppmsDepartmentSyncTemplateTest {
    @Test void originalColumnsAndSelectedParameterizedReadCompileWithoutDroppingExcludedAttributes(){
        var source=DppmsDepartmentSyncTemplate.create(9L).sources().getFirst();
        assertEquals(28,source.parameters().size());assertEquals(9,DppmsDepartmentSyncTemplate.ORIGINAL_COLUMNS.size());
        assertDoesNotThrow(()->MysqlSyncReader.compile(source));
        assertEquals(11,source.mappings().size());assertFalse(source.syncPrimaryKey());
        assertDoesNotThrow(()->MysqlSyncReader.compile(DppmsDepartmentSyncTemplate.create(9L,List.of("160300")).sources().getFirst()));
        assertThrows(IllegalArgumentException.class,()->DppmsDepartmentSyncTemplate.create(9L,List.of()));
    }
    @Test void onlyExplicitAliasesConvertAndAdjudicatedParentsAreUsed(){
        for(int suffix=2;suffix<=10;suffix++)assertEquals(String.format("1621%02d",suffix),DppmsDepartmentCodeRules.normalize(String.format("3112%02d",suffix)));
        assertEquals("310500001001",DppmsDepartmentCodeRules.normalize("310500001001"));assertEquals("160700002",DppmsDepartmentCodeRules.normalize("160700002"));
        assertEquals("160000",DppmsDepartmentCodeRules.parentForNew("163120"));assertEquals("310000",DppmsDepartmentCodeRules.parentForNew("310500001001"));
        assertEquals("280000",DppmsDepartmentCodeRules.parentForNew("280700"));assertNull(DppmsDepartmentCodeRules.parentForNew("999999"));
        assertThrows(IllegalArgumentException.class,()->DppmsDepartmentCodeRules.parentForNew("999998"));
    }
}
