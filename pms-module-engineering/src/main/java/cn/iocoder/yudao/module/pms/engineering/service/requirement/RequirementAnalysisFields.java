package cn.iocoder.yudao.module.pms.engineering.service.requirement;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import java.util.*;

/** The form's historical names are aliases of the Owner's database properties, not extra values. */
public final class RequirementAnalysisFields {
    private RequirementAnalysisFields() { }
    static final Set<String> STRUCTURED = Set.of("transmissionCurrentOptions", "trafficNewConnections",
            "trafficConcurrency", "trafficThroughput", "businessDeviceDetails", "ipManagementResources",
            "ipPublicResources", "operationsManagementOptions");
    public static String property(String code) {
        if (code == null) return null;
        return RequirementAnalysisEntityProvider.FIELDS.fields().stream().map(EntityField::code)
                .filter(field -> field.equals(code) || formKey(field).equals(code)).findFirst().orElse(null);
    }
    static String formKey(String property) { return StrUtil.toUnderlineCase(property).toUpperCase(Locale.ROOT); }
    static Map<String, Object> extensions(Map<String, Object> values) {
        Map<String, Object> result = new LinkedHashMap<>();
        values.forEach((key, value) -> { if (property(key) == null) result.put(key, value); });
        return result;
    }
    static EntityFormApi.Layout layout(EntityFormApi.Layout original) {
        if (original == null) return null;
        Map<String, String> bindings = new LinkedHashMap<>();
        original.binding().fieldBindings().forEach((field, code) -> {
            String property = property(code);
            bindings.put(field, property == null ? code : property);
        });
        var binding = original.binding();
        return new EntityFormApi.Layout(new EntityFormApi.Binding(binding.formRevisionId(),
                binding.extensionDefinitionRevisionId(), bindings, binding.version()), original.templateId(),
                original.revisionNo(), original.formVersion(), original.engineCode(), original.designerVersion(),
                original.rendererVersion(), original.formConfJson(), original.formRulesJson(), original.fields());
    }
}
