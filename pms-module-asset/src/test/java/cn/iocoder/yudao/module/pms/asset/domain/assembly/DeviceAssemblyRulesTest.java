package cn.iocoder.yudao.module.pms.asset.domain.assembly;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeviceAssemblyRulesTest {

    private final DeviceAssemblyRules rules = new DeviceAssemblyRules();

    @Test
    void shouldRejectSelfReference() {
        assertFalse(rules.canAssemble("SN-A", "SN-A", List.of()));
    }

    @Test
    void shouldRejectIndirectCycle() {
        assertFalse(rules.canAssemble("SN-C", "SN-A", List.of(
                new DeviceAssemblyEdge("SN-A", "SN-B"),
                new DeviceAssemblyEdge("SN-B", "SN-C"))));
    }

    @Test
    void shouldAllowAnyDepthWithoutCycle() {
        assertTrue(rules.canAssemble("SN-D", "SN-E", List.of(
                new DeviceAssemblyEdge("SN-A", "SN-B"),
                new DeviceAssemblyEdge("SN-B", "SN-C"),
                new DeviceAssemblyEdge("SN-C", "SN-D"))));
    }
}
