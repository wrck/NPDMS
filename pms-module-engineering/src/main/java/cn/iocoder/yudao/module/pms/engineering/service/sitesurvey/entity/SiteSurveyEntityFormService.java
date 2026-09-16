package cn.iocoder.yudao.module.pms.engineering.service.sitesurvey.entity;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.sitesurvey.entity.SiteSurveyEntityDO;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.*;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.*;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.sitesurvey.entity.vo.SiteSurveyEntitySaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.sitesurvey.entity.vo.SiteSurveyEntityRespVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.SITE_SURVEY_FORM_INVALID;

@Service
@RequiredArgsConstructor
public class SiteSurveyEntityFormService {
    private final DynamicFormBusinessInstanceApi forms;
    private final EntityFormApi bindings;
    private final EntityExtensionApi extensions;
    private final SiteSurveyDetails details;

    public DynamicFormRevisionFact defaultSchema() {
        return forms.inspectCurrentRevisionForUsage(new DynamicFormCurrentRevisionQuery(
                TenantContextHolder.getRequiredTenantId(), SecurityFrameworkUtils.getLoginUserId(),
                SiteSurveyEntityFormPolicyProvider.KEY, 993109090006L, "SITE_SURVEY"));
    }

    private DynamicFormRevisionUsageQuery query(Long revisionId, Integer version, boolean binding) {
        return new DynamicFormRevisionUsageQuery(TenantContextHolder.getRequiredTenantId(),
                SecurityFrameworkUtils.getLoginUserId(), SiteSurveyEntityFormPolicyProvider.KEY, revisionId, "SITE_SURVEY",
                binding ? DynamicFormBusinessAction.REVISION_BINDING_PUBLISH : DynamicFormBusinessAction.REVISION_FROZEN_USE,
                version);
    }

    public DynamicFormRevisionFact schema(Long revisionId, Integer version, boolean binding) {
        return forms.inspectRevisionForUsage(query(revisionId, version, binding));
    }

    public void prepare(SiteSurveyEntityDO row, SiteSurveyEntitySaveReqVO request) {
        Map<String, Object> extras = request.getExtensionValues() == null ? Map.of() : request.getExtensionValues();
        Map<String, Object> fixed = new LinkedHashMap<>(request.getBusinessValues() == null ? Map.of() : request.getBusinessValues());
        SiteSurveyEntityBusinessValues.validate(fixed, row.getProjectId());
        var fields = SiteSurveyEntityProvider.FIELDS.fields();
        fields.forEach(field -> {
            if ("".equals(fixed.get(field.code())) && field.type() != EntityField.Type.TEXT) fixed.put(field.code(), null);
        });
        if (extras.keySet().stream().anyMatch(code -> fields.stream().anyMatch(field -> field.code().equals(code)))) {
            throw exception(SITE_SURVEY_FORM_INVALID);
        }
        try { SiteSurveyEntityProvider.FIELDS.write(row, fixed); }
        catch (IllegalArgumentException invalid) { throw exception(SITE_SURVEY_FORM_INVALID); }
        var previous = row.getId() == null ? null : bindings.read(target(row), actor());
        boolean bindingChanged = previous == null || !Objects.equals(previous.formRevisionId(), request.getFormRevisionId());
        validateValues(row, request.getFormRevisionId(), request.getFormRevisionVersion(), extras, bindingChanged);
    }

    public void validate(SiteSurveyEntityDO row, boolean binding) {
        details.load(row);
        var layout = bindings.layout(target(row), actor());
        var values = values(row);
        SiteSurveyEntityBusinessValues.validate(businessValues(row), row.getProjectId());
        if (layout != null) validateValues(row, layout.binding().formRevisionId(), layout.formVersion(), values, binding);
        extensions.validateComplete(target(row), actor());
    }

    private void validateValues(SiteSurveyEntityDO row, Long revisionId, Integer revisionVersion,
                                Map<String, Object> extras, boolean binding) {
        // A business save does not require a form instance, or even a form binding.
        if (revisionId == null) return;
        if (revisionVersion == null) throw exception(SITE_SURVEY_FORM_INVALID);
        Map<String, Object> values = new LinkedHashMap<>();
        var fixed = SiteSurveyEntityProvider.FIELDS.read(row);
        schema(revisionId, revisionVersion, binding).fields().forEach(field -> {
            var property = propertyCode(field.fieldKey());
            if (fixed.containsKey(property)) values.put(field.fieldKey(), fixed.get(property));
            else if (extras.containsKey(field.fieldKey())) values.put(field.fieldKey(), extras.get(field.fieldKey()));
        });
        Map<String, Object> serializedValues = JsonUtils.parseObject(JsonUtils.toJsonString(values), Map.class);
        DynamicFormValidationFact result = forms.validateRevisionValues(new DynamicFormRevisionValuesQuery(
                query(revisionId, revisionVersion, binding), serializedValues));
        if (!"VALID".equals(result.result())) throw exception(SITE_SURVEY_FORM_INVALID);
    }

    public void persist(SiteSurveyEntityDO row, SiteSurveyEntitySaveReqVO request) {
        var target = target(row);
        var actor = actor();
        var oldValues = extensions.read(target, actor);
        var oldBinding = bindings.read(target, actor);
        Map<String, Object> extras = request.getExtensionValues() == null ? Map.of() : request.getExtensionValues();
        if (oldBinding != null && request.getFormRevisionId() == null) throw exception(SITE_SURVEY_FORM_INVALID);
        var binding = oldBinding;
        if (request.getFormRevisionId() != null && (oldBinding == null || !Objects.equals(oldBinding.formRevisionId(), request.getFormRevisionId())
                || request.getExtensionDefinitionRevisionId() != null
                && !Objects.equals(oldBinding.extensionDefinitionRevisionId(), request.getExtensionDefinitionRevisionId()))) {
            var schema = schema(request.getFormRevisionId(), request.getFormRevisionVersion(), true);
            Map<String, String> fields = fieldBindings(schema);
            if (!fields.values().containsAll(oldValues.fields().keySet())) throw exception(SITE_SURVEY_FORM_INVALID);
            binding = bindings.bind(new EntityFormApi.Bind(target, actor, row.getVersion(), oldBinding == null ? 0 : oldBinding.version(),
                    request.getFormRevisionId(), request.getExtensionDefinitionRevisionId(), fields));
        }
        Long definitionId = binding != null ? binding.extensionDefinitionRevisionId()
                : request.getExtensionDefinitionRevisionId() != null ? request.getExtensionDefinitionRevisionId()
                : oldValues.definitionRevisionId();
        if (!extras.isEmpty() || definitionId != null || oldValues.definitionRevisionId() != null) {
            extensions.save(new EntityExtensionApi.Save(target, actor, row.getVersion(), oldValues.version(), definitionId, extras));
        }
        details.save(row, actor.userId());
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> values(SiteSurveyEntityDO row) {
        Map<String, Object> values = JsonUtils.parseObject(JsonUtils.toJsonString(SiteSurveyEntityProvider.FIELDS.read(row)), Map.class);
        values.putAll(extensions.read(target(row), actor()).fields());
        return values;
    }

    public SiteSurveyEntityRespVO response(SiteSurveyEntityDO row) {
        if (row == null) return null;
        details.load(row);
        var response = cn.iocoder.yudao.framework.common.util.object.BeanUtils.toBean(row, SiteSurveyEntityRespVO.class);
        var layout = bindings.layout(target(row), actor());
        if (layout != null) {
            response.setFormRevisionId(layout.binding().formRevisionId());
            response.setFormRevisionVersion(layout.formVersion());
            response.setExtensionDefinitionRevisionId(layout.binding().extensionDefinitionRevisionId());
        }
        response.setBusinessValues(businessValues(row));
        response.setExtensionValues(extensions.read(target(row), actor()).fields());
        response.setFieldCatalog(SiteSurveyEntityProvider.FIELDS.fields());
        response.setFieldBindings(layout == null ? Map.of() : layout.binding().fieldBindings());
        return response;
    }

    // Legacy templates use an extra_ prefix for some now-fixed fields. Store that alias in bindings.
    private static String propertyCode(String fieldKey) {
        return fieldKey.startsWith("extra_") ? fieldKey.substring("extra_".length()) : fieldKey;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> businessValues(SiteSurveyEntityDO row) {
        return JsonUtils.parseObject(JsonUtils.toJsonString(SiteSurveyEntityProvider.FIELDS.read(row)), Map.class);
    }

    public Map<String, String> fieldBindings(DynamicFormRevisionFact schema) {
        Set<String> fixedCodes = SiteSurveyEntityProvider.FIELDS.fields().stream().map(EntityField::code)
                .collect(java.util.stream.Collectors.toSet());
        Map<String, String> mapping = new LinkedHashMap<>();
        schema.fields().stream().filter(field -> !field.controlledFile()).forEach(field -> {
            String property = propertyCode(field.fieldKey());
            mapping.put(field.fieldKey(), fixedCodes.contains(property) ? property : field.fieldKey());
        });
        return mapping;
    }

    private EntityDataRef target(SiteSurveyEntityDO row) {
        return EntityDataRef.current(new EntityRef(row.getTenantId(), "SOL", "SITE_SURVEY", row.getId()));
    }

    private EntityActor actor() {
        return new EntityActor(TenantContextHolder.getRequiredTenantId(), SecurityFrameworkUtils.getLoginUserId(), java.util.UUID.randomUUID().toString());
    }
}
