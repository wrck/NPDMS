package cn.iocoder.yudao.module.pms.project.service.rule;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.domain.rule.RuleFact;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateMatchFacts;
import cn.iocoder.yudao.module.pms.project.domain.projectmanual.ProjectRules;
import tools.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** Explicit project-owned directory. Never reflect arbitrary DO properties into template expressions. */
public final class ProjectRuleFields {
    public record Field(String code, String label, String valueType, boolean availableAtCreation) { }
    private record Entry(Field field, Function<ProjectMasterDO, Object> read) { }
    private static final Map<String, Entry> ENTRIES = entries();

    private ProjectRuleFields() { }

    public static List<Field> catalog() { return ENTRIES.values().stream().map(Entry::field).toList(); }
    public static Set<String> codes() { return ENTRIES.keySet(); }

    /** The owner has loaded/assembled this entire project; its null columns are known empty values. */
    public static TemplateMatchFacts creationFacts(ProjectMasterDO project) {
        Map<String, RuleFact> facts = new LinkedHashMap<>();
        ENTRIES.forEach((code, entry) -> {
            if (entry.field().availableAtCreation()) facts.put(code, read(project, code));
        });
        return new TemplateMatchFacts(facts);
    }

    public static TemplateMatchFacts manualCreationFacts(ProjectMasterDO draft) {
        var facts = new LinkedHashMap<>(creationFacts(draft).values());
        facts.put("project.sourceType", RuleFact.known(ProjectRules.SOURCE_TYPE_MANUAL));
        if (draft.getParentId() == null) facts.put("project.projectType", RuleFact.known(ProjectRules.DEFAULT_PROJECT_TYPE));
        return new TemplateMatchFacts(facts);
    }

    /** Preview input is limited to the same published catalog; absent keys stay unavailable. */
    public static TemplateMatchFacts suppliedCreationFacts(Map<String, JsonNode> supplied) {
        Map<String, RuleFact> facts = new LinkedHashMap<>();
        supplied.forEach((code, value) -> {
            Entry entry = ENTRIES.get(code);
            if (entry == null || !entry.field().availableAtCreation())
                throw new IllegalArgumentException("匹配只能使用已开放的创建字段");
            RuleFact fact;
            if (value == null || value.isNull()) fact = RuleFact.known(null);
            else if ("BOOLEAN".equals(entry.field().valueType()) && value.isBoolean()) fact = RuleFact.known(value.booleanValue());
            else if ("NUMBER".equals(entry.field().valueType()) && value.isNumber()) fact = RuleFact.known(value.decimalValue());
            else if (Set.of("TEXT", "DATE", "DATETIME").contains(entry.field().valueType()) && value.isTextual())
                fact = RuleFact.known(value.asText());
            else fact = RuleFact.unknown("MATCH_FIELD_VALUE_INVALID");
            facts.put(code, fact);
        });
        return new TemplateMatchFacts(facts);
    }

    public static RuleFact read(ProjectMasterDO project, String code) {
        Entry entry = ENTRIES.get(code);
        return project == null || entry == null ? RuleFact.unknown("PROJECT_FIELD_UNAVAILABLE")
                : RuleFact.known(entry.read().apply(project));
    }

    private static Map<String, Entry> entries() {
        Map<String, Entry> result = new LinkedHashMap<>();
        add(result, "projectName", "项目名称", "TEXT", true, ProjectMasterDO::getProjectName);
        add(result, "projectType", "项目类型", "TEXT", true, ProjectMasterDO::getProjectType);
        add(result, "signingMethod", "签约方式", "TEXT", true, ProjectMasterDO::getSigningMethod);
        add(result, "projectCategory", "项目类别", "TEXT", true, ProjectMasterDO::getProjectCategory);
        add(result, "implementationMethod", "实施方式", "TEXT", true, ProjectMasterDO::getImplementationMode);
        add(result, "majorProjectLevel", "重大项目级别", "TEXT", true, ProjectMasterDO::getMajorProjectLevel);
        add(result, "businessType", "业务类型", "TEXT", true, ProjectMasterDO::getBusinessType);
        add(result, "salesType", "销售类型", "TEXT", true, ProjectMasterDO::getSalesType);
        add(result, "industryCode", "行业编码", "TEXT", true, ProjectMasterDO::getIndustryCode);
        add(result, "serviceLevelCode", "服务级别", "TEXT", true, ProjectMasterDO::getServiceLevelCode);
        add(result, "sourceType", "创建来源", "TEXT", true, ProjectMasterDO::getSourceType);
        add(result, "businessLevelCode", "业务层级", "TEXT", true, ProjectMasterDO::getBusinessLevelCode);
        add(result, "implementationLocation", "实施地点", "TEXT", true, ProjectMasterDO::getImplementationLocation);
        add(result, "companyCode", "主责公司编码", "TEXT", true, ProjectMasterDO::getCompanyCode);
        add(result, "departmentCode", "主责部门编码", "TEXT", true, ProjectMasterDO::getDepartmentCode);
        add(result, "customerCode", "客户编码", "TEXT", true, ProjectMasterDO::getCustomerCode);
        add(result, "isChild", "是否子项目", "BOOLEAN", true, project -> project.getParentId() != null);
        add(result, "lifecycleStatus", "项目生命周期状态", "TEXT", false, ProjectMasterDO::getLifecycleStatus);
        add(result, "projectEndDate", "项目结束日期", "DATE", false, ProjectMasterDO::getProjectEndDate);
        return java.util.Collections.unmodifiableMap(result);
    }

    private static void add(Map<String, Entry> entries, String code, String label, String type, boolean creation,
                            Function<ProjectMasterDO, Object> reader) {
        String key = "project." + code;
        entries.put(key, new Entry(new Field(key, label, type, creation), reader));
    }
}
