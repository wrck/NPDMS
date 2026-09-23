package cn.iocoder.yudao.module.pms.project.domain.template;

import cn.iocoder.yudao.module.pms.project.domain.rule.RuleFact;
import java.util.Map;

/** A creation fact snapshot: a known null is not an omitted or unavailable fact. */
public record TemplateMatchFacts(Map<String, RuleFact> values) {
    public TemplateMatchFacts { values = Map.copyOf(values); }

}
