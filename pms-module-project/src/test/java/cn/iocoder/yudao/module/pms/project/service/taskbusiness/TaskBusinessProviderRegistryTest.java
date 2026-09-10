package cn.iocoder.yudao.module.pms.project.service.taskbusiness;

import cn.iocoder.yudao.module.pms.project.api.taskbusiness.TaskBusinessObjectProvider;
import cn.iocoder.yudao.module.pms.project.service.taskbusiness.TaskBusinessProviderRegistry.CompletionFactCatalogEntry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Directory aggregation only; entries never assert facts or grant permissions. */
class TaskBusinessProviderRegistryTest {

    private static TaskBusinessObjectProvider provider(String owner, String type,
            Set<String> codes, Map<String, String> labels) {
        var provider = mock(TaskBusinessObjectProvider.class);
        when(provider.ownerContext()).thenReturn(owner);
        when(provider.objectType()).thenReturn(type);
        when(provider.completionFactCodes()).thenReturn(codes);
        when(provider.completionFactLabels()).thenReturn(labels);
        return provider;
    }

    @Test
    void catalogAggregatesFactsWithLabelsAndFallsBackToCodes() {
        var labeled = provider("SOL", "SITE_SURVEY", Set.of("SURVEY_CONFIRMED", "SURVEY_ARCHIVED"),
                Map.of("SURVEY_CONFIRMED", "工勘记录已确认（不等同实施就绪）"));
        var unlabeled = provider("ACC", "ACCEPTANCE", Set.of("REPORT_EFFECTIVE"), Map.of());
        var empty = provider("PLT", "DYNAMIC_FORM_INSTANCE", Set.of(), Map.of());

        List<CompletionFactCatalogEntry> catalog =
                new TaskBusinessProviderRegistry(List.of(labeled, unlabeled, empty)).completionFactCatalog();

        assertEquals(3, catalog.size());
        assertEquals(Set.of(
                new CompletionFactCatalogEntry("SOL", "SITE_SURVEY",
                        "SURVEY_CONFIRMED", "工勘记录已确认（不等同实施就绪）"),
                new CompletionFactCatalogEntry("SOL", "SITE_SURVEY",
                        "SURVEY_ARCHIVED", "SURVEY_ARCHIVED"),
                new CompletionFactCatalogEntry("ACC", "ACCEPTANCE",
                        "REPORT_EFFECTIVE", "REPORT_EFFECTIVE")), Set.copyOf(catalog));
    }
}
