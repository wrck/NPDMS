package cn.iocoder.yudao.module.pms.engineering.service.sitesurvey;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.sitesurvey.SiteSurveyDO;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.*;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.SITE_SURVEY_FORM_INVALID;

@Service
@RequiredArgsConstructor
public class SiteSurveyFormService {
    private final DynamicFormBusinessInstanceApi forms;

    public DynamicFormRevisionFact defaultSchema() {
        return forms.inspectCurrentRevisionForUsage(new DynamicFormCurrentRevisionQuery(
                TenantContextHolder.getRequiredTenantId(), SecurityFrameworkUtils.getLoginUserId(),
                SiteSurveyFormPolicyProvider.KEY, 993109090006L, "SITE_SURVEY"));
    }

    private DynamicFormRevisionUsageQuery query(Long revisionId, Integer version, boolean binding) {
        return new DynamicFormRevisionUsageQuery(TenantContextHolder.getRequiredTenantId(),
                SecurityFrameworkUtils.getLoginUserId(), SiteSurveyFormPolicyProvider.KEY, revisionId, "SITE_SURVEY",
                binding ? DynamicFormBusinessAction.REVISION_BINDING_PUBLISH : DynamicFormBusinessAction.REVISION_FROZEN_USE,
                version);
    }

    public DynamicFormRevisionFact schema(Long revisionId, Integer version, boolean binding) {
        return forms.inspectRevisionForUsage(query(revisionId, version, binding));
    }

    public void validate(SiteSurveyDO row, boolean binding) {
        Map<String, Object> extras = row.getFormExtraValues() == null ? Map.of() : row.getFormExtraValues();
        SiteSurveyBusinessValues.validate(extras, row.getProjectId());
        if (row.getFormRevisionId() == null) {
            if (!extras.isEmpty()) throw exception(SITE_SURVEY_FORM_INVALID);
            return;
        }
        if (row.getFormRevisionVersion() == null || extras.keySet().stream().anyMatch(k -> !k.startsWith("extra_"))) {
            throw exception(SITE_SURVEY_FORM_INVALID);
        }
        Map<String, Object> entity = JsonUtils.parseObject(JsonUtils.toJsonString(row), Map.class);
        Map<String, Object> values = new LinkedHashMap<>(extras);
        SiteSurveyFormPolicyProvider.FIELDS.forEach(field -> values.put(field, entity.get(field)));
        DynamicFormValidationFact result = forms.validateRevisionValues(new DynamicFormRevisionValuesQuery(
                query(row.getFormRevisionId(), row.getFormRevisionVersion(), binding), values));
        if (!"VALID".equals(result.result())) throw exception(SITE_SURVEY_FORM_INVALID);
    }
}
