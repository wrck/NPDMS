package cn.iocoder.yudao.module.pms.engineering.service.requirement;

import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RequirementAnalysisFieldsTest {
    @Test void oldBindingIsProjectedWithoutChangingFrozenSourceOrRetainingSecondBusinessValues() {
        var binding = new EntityFormApi.Binding(1L, 2L, Map.of("TRAFFIC_CONCURRENCY", "TRAFFIC_CONCURRENCY", "CUSTOM", "CUSTOM"), 3);
        var original = new EntityFormApi.Layout(binding, 1L, 1, 1, "engine", "designer", "renderer", "{}", "[]", List.of());
        var view = RequirementAnalysisFields.layout(original);
        assertEquals("trafficConcurrency", view.binding().fieldBindings().get("TRAFFIC_CONCURRENCY"));
        assertEquals("TRAFFIC_CONCURRENCY", original.binding().fieldBindings().get("TRAFFIC_CONCURRENCY"));
        assertEquals(Map.of("CUSTOM", false), RequirementAnalysisFields.extensions(
                Map.of("TRAFFIC_CONCURRENCY", "old", "trafficConcurrency", "also-old", "CUSTOM", false)));
        assertEquals(3, view.binding().version());
    }
}
