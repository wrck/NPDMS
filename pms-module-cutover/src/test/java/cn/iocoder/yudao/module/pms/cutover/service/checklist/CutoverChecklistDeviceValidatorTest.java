package cn.iocoder.yudao.module.pms.cutover.service.checklist;

import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.checklist.CutoverChecklistItemDO;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class CutoverChecklistDeviceValidatorTest {
    @Test void acceptsOnlyTaskSerialsInArrayAndWrappedRowsWithoutChangingFacts() {
        var item = table();
        for (String answer : new String[]{"{\"value\":[{\"serialNumber\":\"SN-A\",\"cpuUsage\":0}]}",
                "{\"value\":{\"rows\":[{\"serialNumber\":\"SN-A\"}]}}"}) {
            assertDoesNotThrow(() -> CutoverChecklistDeviceValidator.validate(
                    new CutoverChecklistDeviceValidator.Query(item, answer, Set.of("SN-A"))));
        }
        assertTrue(CutoverChecklistDeviceValidator.referencesTaskDevices(item));
        for (String value : new String[]{"\"FOREIGN\"", "123", "{}"}) {
            var failure = assertThrows(CutoverChecklistException.class, () -> CutoverChecklistDeviceValidator.validate(
                    new CutoverChecklistDeviceValidator.Query(item, "{\"value\":[{\"serialNumber\":" + value + "}]}", Set.of("SN-A"))));
            assertEquals(CutoverChecklistException.Code.INVALID_REQUEST, failure.getCode());
            assertTrue(failure.getMessage().contains("当前割接设备清单"));
        }
    }

    @Test void emptyScopeFailsClosedButIncompleteDraftAndUnrelatedFieldsKeepTheirExistingRules() {
        var item = table();
        assertThrows(CutoverChecklistException.class, () -> CutoverChecklistDeviceValidator.validate(
                new CutoverChecklistDeviceValidator.Query(item, "{\"value\":[{\"serialNumber\":\"SN-A\"}]}", Set.of())));
        assertDoesNotThrow(() -> CutoverChecklistDeviceValidator.validate(
                new CutoverChecklistDeviceValidator.Query(item, "{\"value\":[{\"serialNumber\":\"\"}]}", Set.of())));
        item.setInterfaceSchemaSnapshot("{\"columns\":[{\"code\":\"remoteInterface\"}]}");
        assertFalse(CutoverChecklistDeviceValidator.referencesTaskDevices(item));
    }

    @Test void nestedAndConditionalSerialFieldsUseTheSameScope() {
        var item = table();
        item.setInterfaceSchemaSnapshot("{\"fields\":[{\"code\":\"enabled\"},{\"code\":\"details\",\"visibleWhen\":{\"field\":\"enabled\",\"equals\":true},\"columns\":[{\"code\":\"serialNumber\"}]}]}");
        assertDoesNotThrow(() -> CutoverChecklistDeviceValidator.validate(new CutoverChecklistDeviceValidator.Query(item,
                "{\"value\":{\"enabled\":false,\"details\":[{\"serialNumber\":\"FOREIGN\"}]}}", Set.of("SN-A"))));
        assertThrows(CutoverChecklistException.class, () -> CutoverChecklistDeviceValidator.validate(new CutoverChecklistDeviceValidator.Query(item,
                "{\"value\":{\"enabled\":true,\"details\":[{\"serialNumber\":\"FOREIGN\"}]}}", Set.of("SN-A"))));
    }

    private CutoverChecklistItemDO table() {
        var item = new CutoverChecklistItemDO();
        item.setItemName("设备部署位置规划"); item.setInterfaceFormatCode("TABLE");
        item.setInterfaceSchemaSnapshot("{\"columns\":[{\"code\":\"serialNumber\"},{\"code\":\"cpuUsage\"}]}");
        return item;
    }
}
