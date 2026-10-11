package cn.iocoder.yudao.module.pms.engineering.service.sitesurvey.entity;

import cn.iocoder.yudao.module.pms.platform.api.dynamicform.DynamicFormBusinessAction;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class SiteSurveyEntityFormPolicyProviderTest {
    private final SiteSurveyEntityFormPolicyProvider policy = new SiteSurveyEntityFormPolicyProvider();

    private DynamicFormFieldDescriptor field(String key, boolean file) {
        return new DynamicFormFieldDescriptor(key, "input", file, false, "STRING", null, null, null, List.of());
    }

    private DynamicFormRevisionPolicyQuery query(String usage, List<DynamicFormFieldDescriptor> fields) {
        return new DynamicFormRevisionPolicyQuery(1L, 1L, SiteSurveyEntityFormPolicyProvider.KEY,
                993109090006L, 993110100426L, 3, 1, usage,
                DynamicFormBusinessAction.REVISION_BINDING_PUBLISH, fields);
    }

    @Test void acceptsCurrentFormWithoutRetiredSupplementsAndLegacySchemas() {
        var current = List.of(field("conclusion", false), field("remark", false), field("extra_powerTypes", false));
        assertThat(policy.inspectRevisionCompatibility(query("SITE_SURVEY", current)).allowed()).isTrue();
        var legacy = new java.util.ArrayList<>(current);
        List.of("powerSupply", "cabinet", "networkPort", "fiber", "module", "cable", "ground", "constructionResource")
                .forEach(key -> legacy.add(field(key, false)));
        assertThat(policy.inspectRevisionCompatibility(query("SITE_SURVEY", legacy)).allowed()).isTrue();
    }

    @Test void rejectsMissingCoreFieldsWrongUsageAndUnsafeFields() {
        assertThat(policy.inspectRevisionCompatibility(query("SITE_SURVEY", List.of(field("conclusion", false)))).allowed()).isFalse();
        var core = List.of(field("conclusion", false), field("remark", false));
        assertThat(policy.inspectRevisionCompatibility(query("OTHER", core)).allowed()).isFalse();
        for (var unsafe : List.of(field("status", false), field("extra_file", true))) {
            var fields = new java.util.ArrayList<>(core);
            fields.add(unsafe);
            assertThat(policy.inspectRevisionCompatibility(query("SITE_SURVEY", fields)).allowed()).isFalse();
        }
    }
}
