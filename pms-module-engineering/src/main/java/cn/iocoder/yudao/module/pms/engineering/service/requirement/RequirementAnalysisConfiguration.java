package cn.iocoder.yudao.module.pms.engineering.service.requirement;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectBusinessConfigurationApi.Configuration;
import tools.jackson.databind.JsonNode;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.REQUIREMENT_ANALYSIS_WORK_BINDING_INVALID;

/** Reads the existing PRE-04 form configuration without introducing a task or another form version. */
final class RequirementAnalysisConfiguration {
    private static final Set<String> FORM_FIELDS = Set.of("schemaVersion", "dynamicFormTemplateId",
            "dynamicFormTemplateRevisionId", "dynamicFormRevisionNo", "dynamicFormRevisionFactVersion");
    private static final Set<String> PRESENTATION_FIELDS = Set.of("businessViewRevisionId", "instanceResolutionStrategy", "contextMapping");

    private RequirementAnalysisConfiguration() { }

    record ModuleForm(Long tenantId, Long projectId, Long templateId, Long revisionId,
                      Integer revisionNo, Integer factVersion) { }

    static ModuleForm require(ModuleForm value, Long tenantId, Long projectId) {
        if (value == null || !positive(tenantId) || !positive(projectId)
                || !Objects.equals(tenantId, value.tenantId()) || !Objects.equals(projectId, value.projectId())
                || !positive(value.templateId()) || !positive(value.revisionId())
                || value.revisionNo() == null || value.revisionNo() <= 0
                || value.factVersion() == null || value.factVersion() <= 0)
            throw exception(REQUIREMENT_ANALYSIS_WORK_BINDING_INVALID);
        return value;
    }

    static Configuration require(Configuration value, Long tenantId, Long projectId) {
        if (value == null || !positive(tenantId) || !positive(projectId)
                || !Objects.equals(tenantId, value.tenantId()) || !Objects.equals(projectId, value.projectId())
                || !positive(value.projectTemplateId()) || !positive(value.templateRevisionId())
                || value.templateRevisionNo() == null || value.templateRevisionNo() <= 0)
            throw exception(REQUIREMENT_ANALYSIS_WORK_BINDING_INVALID);
        var form = form(value.parameters());
        if (value.dynamicFormRevisionId() != null && !Objects.equals(value.dynamicFormRevisionId(), form.revisionId()))
            throw exception(REQUIREMENT_ANALYSIS_WORK_BINDING_INVALID);
        return value;
    }

    static Form form(String parameters) {
        try {
            JsonNode value = JsonUtils.parseTree(parameters);
            if (value == null || !value.isObject()) throw new IllegalArgumentException();
            var fields = new HashSet<>(value.propertyNames());
            fields.removeAll(PRESENTATION_FIELDS);
            if (!fields.equals(FORM_FIELDS) || number(value, "schemaVersion") != 2) throw new IllegalArgumentException();
            return new Form(number(value, "dynamicFormTemplateId"), number(value, "dynamicFormTemplateRevisionId"),
                    Math.toIntExact(number(value, "dynamicFormRevisionNo")), Math.toIntExact(number(value, "dynamicFormRevisionFactVersion")));
        } catch (RuntimeException invalid) {
            throw exception(REQUIREMENT_ANALYSIS_WORK_BINDING_INVALID);
        }
    }

    private static long number(JsonNode value, String field) {
        JsonNode token = value.get(field);
        if (token == null || !token.isIntegralNumber() || !token.canConvertToLong() || token.longValue() <= 0)
            throw new IllegalArgumentException();
        return token.longValue();
    }

    private static boolean positive(Long value) { return value != null && value > 0; }
    record Form(Long templateId, Long revisionId, int revisionNo, int factVersion) { }
}
