package cn.iocoder.yudao.module.pms.integration.sync;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GenericDirectTaskLineTest {
    private SyncDefinition definition(String system,String key) {
        return SyncDefinition.builder().adapter("TABLE_MAPPING").sourceSystem(system).taskKey(key).build();
    }
    @Test void independentIdentitiesKeepReviewedBusinessContracts() {
        for (String[] pair : new String[][] {{"CRM","P11_DIRECT","crm-product"},{"DPPMS","P14_DIRECT","sales-orders-full-sync"},{"DPPMS","P15_DIRECT","delivery-full-sync"}}) {
            var direct=definition(pair[0],pair[1]);var original=definition(pair[0],pair[2]);
            assertEquals(pair[2],GenericDirectTaskLine.businessKey(direct));
            assertNotEquals(SyncDefinitionValidator.taskIdentity(original),SyncDefinitionValidator.taskIdentity(direct));
            assertEquals(pair[1],direct.taskKey());
            assertEquals(pair[2],GenericDirectTaskLine.businessKey(original));
        }
    }
    @Test void aliasesCannotCrossSourceOrAdapterBoundary() {
        assertEquals("P15_DIRECT",GenericDirectTaskLine.businessKey(definition("CRM","P15_DIRECT")));
        assertEquals("P11_DIRECT",GenericDirectTaskLine.businessKey(definition("DPPMS","P11_DIRECT")));
        assertEquals("P15_DIRECT",GenericDirectTaskLine.businessKey(definition("DPPMS","P15_DIRECT").toBuilder().adapter("OTHER").build()));
        assertEquals("arbitrary",GenericDirectTaskLine.businessKey(definition("DPPMS","arbitrary")));
    }
}
