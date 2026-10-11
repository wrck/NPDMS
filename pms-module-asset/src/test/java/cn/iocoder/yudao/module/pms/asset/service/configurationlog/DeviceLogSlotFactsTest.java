package cn.iocoder.yudao.module.pms.asset.service.configurationlog;

import cn.iocoder.yudao.module.pms.asset.api.device.DeviceLogSlotFacts;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DeviceLogSlotFactsTest {
    @Test void actualReleaseSnapshotReadsEveryObservedSlotAndStillRejectsDuplicates() {
        var facts=List.of(Map.of("semanticKey","device.hardware.slotDevices","status","OBSERVED","value",row(4,"SN-4")),
                Map.of("semanticKey","device.hardware.slotDevices","status","PARTIAL","value",row(6,"SN-6")),
                Map.of("semanticKey","device.hardware.slotDevices","status","OBSERVED","value",row(5,"SN-5")));
        var actual=Map.<String,Object>of("semanticResult",Map.of("snapshot",Map.of("entities",Map.of("device",Map.of("hardware",Map.of("slotDevices",facts))))));
        assertEquals(List.of("SN-4","SN-5"),DeviceLogSlotFacts.from(actual).devices().stream().map(DeviceLogSlotFacts.SlotDevice::serialNumber).toList());
        var duplicate=Map.<String,Object>of("semanticResult",Map.of("snapshot",List.of(facts.getFirst(),facts.getFirst())));
        assertThrows(IllegalArgumentException.class,()->DeviceLogSlotFacts.from(duplicate));
    }
    @Test void allVersionFallbacksPreferIndependentValuesAndLeaveAbsentParentAbsent() {
        var own=new cn.iocoder.yudao.module.pms.asset.api.device.DeviceLogSlotFacts.SlotDevice(4,"SN",null,null,"PCB-C","CPLD-C","SOFT-C","BOOT-C");
        assertEquals("PCB-C",own.effectivePcb("PCB-P"));assertEquals("CPLD-C",own.effectiveCpld("CPLD-P"));
        assertEquals("SOFT-C",own.effectiveSoftware("SOFT-P"));assertEquals("BOOT-C",own.effectiveBoot("BOOT-P"));
        var missing=new cn.iocoder.yudao.module.pms.asset.api.device.DeviceLogSlotFacts.SlotDevice(4,"SN",null,null,null,null,null,null);
        assertEquals("PCB-P",missing.effectivePcb("PCB-P"));assertEquals("CPLD-P",missing.effectiveCpld("CPLD-P"));
        assertEquals("SOFT-P",missing.effectiveSoftware("SOFT-P"));assertEquals("BOOT-P",missing.effectiveBoot("BOOT-P"));
        assertNull(missing.effectivePcb(null));assertNull(missing.effectiveCpld(null));
        assertNull(missing.effectiveSoftware(null));assertNull(missing.effectiveBoot(null));
    }
    @Test void childSoftwareUsesOwnObservedVersionOtherwiseInheritsParentWithoutFakingObservation() {
        var first=DeviceLogSlotFacts.from(result("OBSERVED",List.of(row(4,"SN-4")))).devices().getFirst();
        assertNull(first.softVersion());assertTrue(first.softwareInherited());
        assertEquals("PARENT",first.effectiveSoftware("PARENT"));
        var own=new HashMap<String,Object>(row(4,"SN-4"));own.put("softVersion","CHILD");
        var second=DeviceLogSlotFacts.from(result("OBSERVED",List.of(own))).devices().getFirst();
        assertEquals("CHILD",second.effectiveSoftware("PARENT"));assertFalse(second.softwareInherited());
        assertEquals("A",second.pcbVersion());assertEquals("1.00",second.cpldVersion());
        assertNull(second.basicBootloaderVersion());
    }
    @Test void unobservedOrMissingSlotsDoNotCreateChildFacts() {
        assertTrue(DeviceLogSlotFacts.from(null).devices().isEmpty());
        assertTrue(DeviceLogSlotFacts.from(result("PARTIAL",List.of(row(4,"SN-4")))).devices().isEmpty());
    }
    @Test void repeatedPositionOrChildSerialCannotSilentlyReplaceDeviceAssociation() {
        assertThrows(IllegalArgumentException.class,()->DeviceLogSlotFacts.from(result("OBSERVED",List.of(row(4,"SN-4"),row(4,"SN-5")))));
        assertThrows(IllegalArgumentException.class,()->DeviceLogSlotFacts.from(result("OBSERVED",List.of(row(4,"SN-4"),row(5,"sn-4")))));
    }
    @Test void invalidSlotsMissingSerialOrCorruptedFieldsAreRejected() {
        for (Object slot:List.of(-1,1.5,"4")) {
            var row=new HashMap<>(row(4,"SN-4"));row.put("slot",slot);
            assertThrows(IllegalArgumentException.class,()->DeviceLogSlotFacts.from(result("OBSERVED",List.of(row))));
        }
        var row=new HashMap<>(row(4,"SN-4"));row.remove("serialNumber");
        assertThrows(IllegalArgumentException.class,()->DeviceLogSlotFacts.from(result("OBSERVED",List.of(row))));
        row.put("serialNumber","SN\nOTHER");
        assertThrows(IllegalArgumentException.class,()->DeviceLogSlotFacts.from(result("OBSERVED",List.of(row))));
    }
    private Map<String,Object> row(int slot,String serial) {
        return Map.of("slot",slot,"serialNumber",serial,"model","Blade","boardType","FW","pcbVersion","A","cpldVersion","1.00");
    }
    private Map<String,Object> result(String status,Object rows) {
        return Map.of("semanticResult",Map.of("snapshot",Map.of("slots",Map.of("semanticKey","device.hardware.slotDevices","status",status,"value",rows))));
    }
}
