package cn.iocoder.yudao.module.pms.project.domain.template;

import cn.iocoder.yudao.module.pms.project.domain.rule.RuleFact;
import cn.iocoder.yudao.module.pms.project.domain.projectattribute.ProjectAttributeSnapshot;
import java.util.LinkedHashMap;
import java.util.Map;

/** A creation fact snapshot: a known null is not an omitted or unavailable fact. */
public record TemplateMatchFacts(Map<String, RuleFact> values) {
    public TemplateMatchFacts { values = Map.copyOf(values); }

    public TemplateMatchFacts withAttributes(ProjectAttributeSnapshot attributes) {
        var result = new LinkedHashMap<>(values);
        result.put("project.signingMethod", RuleFact.known(attributes.signingMethod()));
        result.put("project.projectCategory", RuleFact.known(attributes.projectCategory()));
        result.put("project.implementationMethod", RuleFact.known(attributes.implementationMode()));
        result.put("project.majorProjectLevel", RuleFact.known(attributes.majorProjectLevel()));
        return new TemplateMatchFacts(result);
    }
}
