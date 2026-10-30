package cn.iocoder.yudao.module.pms.project.domain.template;

import tools.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Frozen PAGE contract only; runtime directory and Owner authorization are deliberately not consulted. */
public final class TemplatePresentationContract {
    private TemplatePresentationContract() { }
    private static final Map<String, String> PARAMETERS = Map.of("projectId", "$project.id", "objectId", "$object.id");

    public static void validate(TemplateExecutionConfiguration.Presentation presentation,
            String owner, String entityType, String componentKey, JsonNode view) {
        Objects.requireNonNull(presentation, "presentation");
        TemplatePresentationUrl.path(presentation.pageUrl());
        if (view == null || !view.isObject() || !"PAGE".equals(view.path("viewSource").asText())
                || !text(owner) || !text(entityType) || !text(componentKey)
                || !owner.equals(view.path("ownerContext").asText())
                || !entityType.equals(view.path("entityType").asText())
                || !componentKey.equals(view.path("componentKey").asText())
                || !view.path("componentVersion").isTextual() || !text(view.path("componentVersion").asText())
                || !positiveId(view.path("id"))
                || !view.path("dynamicFormRevisionId").isMissingNode() && !view.path("dynamicFormRevisionId").isNull()) {
            throw new IllegalArgumentException("PRESENTATION_BINDING_MISMATCH");
        }
        for (var parameter : presentation.query().entrySet()) {
            if (!Objects.equals(PARAMETERS.get(parameter.getKey()), parameter.getValue()))
                throw new IllegalArgumentException("PRESENTATION_PARAMETER_NOT_SUPPORTED");
        }
    }

    public static void validate(TemplateExecutionConfiguration.Presentation presentation,
            TemplateExecutionSnapshot.BindingContract binding) {
        if (binding == null || binding.getDynamicFormRevisionId() != null)
            throw new IllegalArgumentException("PRESENTATION_BINDING_REQUIRED");
        validate(presentation, binding.getTargetContextCode(), binding.getTargetObjectType(),
                binding.getComponentKey(), binding.getBusinessViewSnapshot());
    }

    /** Only the already selected target is repeated here; query parameters cannot supply an execution identity. */
    public static Map<String, String> resolve(TemplateExecutionConfiguration.Presentation presentation,
            Long projectId, String objectId) {
        if (projectId == null || projectId <= 0) throw new IllegalArgumentException("PRESENTATION_PROJECT_REQUIRED");
        Map<String, String> result = new LinkedHashMap<>();
        for (var parameter : presentation.query().entrySet()) {
            if (!Objects.equals(PARAMETERS.get(parameter.getKey()), parameter.getValue()))
                throw new IllegalArgumentException("PRESENTATION_PARAMETER_NOT_SUPPORTED");
            String value = "projectId".equals(parameter.getKey()) ? projectId.toString() : objectId;
            if (value != null) result.put(parameter.getKey(), value);
        }
        return TemplatePresentationUrl.query(result);
    }

    private static boolean positiveId(JsonNode value) {
        if (!value.isIntegralNumber() && !value.isTextual()) return false;
        String id = value.asText();
        if (!id.matches("[1-9][0-9]*")) return false;
        try { return Long.parseLong(id) > 0; }
        catch (NumberFormatException invalid) { return false; }
    }

    private static boolean text(String value) { return value != null && !value.isBlank(); }
}
