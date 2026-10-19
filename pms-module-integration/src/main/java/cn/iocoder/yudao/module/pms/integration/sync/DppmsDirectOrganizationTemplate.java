package cn.iocoder.yudao.module.pms.integration.sync;

import java.util.*;

/** Independent template: never changes or saves the validated P01 template. */
public final class DppmsDirectOrganizationTemplate {
    public static final String ADAPTER = "DPPMS_ORGANIZATION_DIRECT";
    static final List<String> OBJECTS = List.of("COMPANY", "DEPARTMENT", "fnd_department", "ehr_department", "COMPANY_REFERENCE");
    static final List<String> FIELDS = List.of("objectName", "businessKey", "sortRank", "canonicalFields", "sourceAlias");
    static final List<String> FND_CODES = java.util.stream.Stream.concat(
            DppmsDepartmentSyncTemplate.APPROVED_MISSING_CODES.stream(),java.util.stream.Stream.of("163130")).toList();
    private DppmsDirectOrganizationTemplate() {}
    public static SyncDefinition create(Long connectionId) {
        var original = DppmsConsolidatedTemplate.create(new DppmsConsolidatedTemplate.Request("P01", connectionId));
        Map<String,Object> parameters = new LinkedHashMap<>();
        original.sources().forEach(source -> { if (source.parameters() != null) parameters.putAll(source.parameters()); });
        parameters.keySet().removeIf(key -> key.startsWith("P02_departmentCode"));
        for(int i=0;i<FND_CODES.size();i++)parameters.put("P02_departmentCode"+i,FND_CODES.get(i));
        var names = List.of("company", "base-department", "fnd-department", "completion-department");
        List<SyncDefinition.Source> sources = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            sources.add(original.sources().get(i).toBuilder()
                    .sql(DppmsMigrationSql.initial("direct-organization-" + names.get(i)))
                    .sourceKey("sourceId").updatedAt(null).queryRef(null).groupKey(null).rowProjection(null)
                    .parameters(Map.copyOf(parameters)).columns(List.of()).filters(List.of()).syncPrimaryKey(false)
                    .mappings(FIELDS.stream().map(field -> SyncDefinition.Mapping.builder()
                            .source(field).target(field).conversion("DIRECT").build()).toList()).build());
        }
        sources.add(original.sources().getLast());
        return original.toBuilder().adapter(ADAPTER).taskKey("P01_DIRECT").sources(List.copyOf(sources)).build();
    }
}
