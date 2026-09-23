package cn.iocoder.yudao.module.pms.project.service.rule;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.infra.api.config.ConfigApi;
import cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo.ProjectRespVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.domain.projectmanual.ProjectRules;
import cn.iocoder.yudao.module.pms.project.domain.rule.RuleFact;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateMatchFacts;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Public scalar entity properties are discovered; configuration controls authoring metadata. */
@Component
public final class ProjectRuleFields {
    public static final String CONFIG_KEY = "pms.project-rule.fields";
    public record Field(String code, String label, String valueType, boolean availableAtCreation) { }
    private record Entry(Field field, Function<ProjectMasterDO, Object> read) { }
    private static final JsonNode BASELINE = baseline();
    private static final Map<String, Entry> ENTRIES = discover();
    private final ConfigApi configs;

    public ProjectRuleFields(ConfigApi configs) { this.configs = configs; }

    public List<Field> catalog() {
        JsonNode settings = settings();
        var result = new ArrayList<>(ENTRIES.values().stream().filter(entry -> settings.path(entry.field().code()).path("enabled").asBoolean(true))
                .map(entry -> metadata(entry.field(), settings)).toList());
        return List.copyOf(result);
    }

    public Set<String> codes() { return catalog().stream().map(Field::code).collect(Collectors.toUnmodifiableSet()); }

    /** Immutable binding identities for already frozen operation programs. */
    public static Set<String> readableCodes() { return ENTRIES.keySet(); }

    public TemplateMatchFacts creationFacts(ProjectMasterDO project) {
        Map<String, RuleFact> facts = new LinkedHashMap<>();
        // Authoring eligibility belongs to publication validation, not mutable runtime filtering.
        ENTRIES.forEach((code, entry) -> facts.put(code, read(project, code)));
        return new TemplateMatchFacts(facts);
    }

    public TemplateMatchFacts normalizedCreationFacts(ProjectMasterDO project,
            cn.iocoder.yudao.module.pms.project.domain.projectattribute.ProjectAttributeSnapshot attributes, boolean manual) {
        var normalized = BeanUtils.toBean(project, ProjectMasterDO.class);
        normalized.setSigningMethod(attributes.signingMethod());
        normalized.setProjectCategory(attributes.projectCategory());
        normalized.setImplementationMode(attributes.implementationMode());
        normalized.setMajorProjectLevel(attributes.majorProjectLevel());
        return manual ? manualCreationFacts(normalized) : creationFacts(normalized);
    }

    public TemplateMatchFacts manualCreationFacts(ProjectMasterDO draft) {
        ProjectMasterDO normalized = BeanUtils.toBean(draft, ProjectMasterDO.class);
        normalized.setSourceType(ProjectRules.SOURCE_TYPE_MANUAL);
        if (normalized.getParentId() == null) normalized.setProjectType(ProjectRules.DEFAULT_PROJECT_TYPE);
        return creationFacts(normalized);
    }

    public TemplateMatchFacts suppliedCreationFacts(Map<String, JsonNode> supplied) {
        Map<String, Field> fields = catalog().stream().collect(Collectors.toMap(Field::code, Function.identity()));
        Map<String, RuleFact> facts = new LinkedHashMap<>();
        supplied.forEach((code, value) -> {
            Field field = fields.get(code);
            if (field == null || !field.availableAtCreation()) throw new IllegalArgumentException("匹配只能使用已开放的创建字段");
            RuleFact fact;
            if (value == null || value.isNull()) fact = RuleFact.known(null);
            else if ("BOOLEAN".equals(field.valueType()) && value.isBoolean()) fact = RuleFact.known(value.booleanValue());
            else if ("NUMBER".equals(field.valueType()) && value.isNumber()) fact = RuleFact.known(value.decimalValue());
            else if (Set.of("TEXT", "DATE", "DATETIME").contains(field.valueType()) && value.isTextual()) fact = RuleFact.known(value.asText());
            else fact = RuleFact.unknown("MATCH_FIELD_VALUE_INVALID");
            facts.put(code, fact);
        });
        return new TemplateMatchFacts(facts);
    }

    /** Frozen programs read stable property bindings, independently of mutable display settings. */
    public RuleFact read(ProjectMasterDO project, String code) {
        Entry entry = ENTRIES.get(code);
        return project == null || entry == null ? RuleFact.unknown("PROJECT_FIELD_UNAVAILABLE")
                : RuleFact.known(entry.read().apply(project));
    }

    private JsonNode settings() {
        String value = configs.getConfigValueByKey(CONFIG_KEY);
        JsonNode result = value == null || value.isBlank() ? BASELINE.path("fields") : JsonUtils.parseObject(value, JsonNode.class);
        if (!result.isObject()) throw new IllegalArgumentException("项目规则字段配置必须为对象");
        result.properties().forEach(item -> {
            if (!ENTRIES.containsKey(item.getKey()) || !item.getValue().isObject())
                throw new IllegalArgumentException("项目规则字段配置包含未知属性: " + item.getKey());
            item.getValue().properties().forEach(option -> {
                if (!Set.of("label", "enabled", "availableAtCreation").contains(option.getKey())
                        || ("label".equals(option.getKey()) ? !option.getValue().isTextual() || option.getValue().asText().isBlank()
                        : !option.getValue().isBoolean()))
                    throw new IllegalArgumentException("项目规则字段配置项无效: " + item.getKey() + "." + option.getKey());
            });
        });
        return result;
    }

    private static Field metadata(Field field, JsonNode settings) {
        JsonNode override = settings.path(field.code());
        return new Field(field.code(), override.path("label").asText(field.label()), field.valueType(),
                override.path("availableAtCreation").asBoolean(field.availableAtCreation()));
    }

    private static Map<String, Entry> discover() {
        Map<String, Entry> entries = new LinkedHashMap<>();
        for (var exposed : ProjectRespVO.class.getDeclaredFields()) {
            Schema schema = exposed.getAnnotation(Schema.class);
            if (Modifier.isStatic(exposed.getModifiers()) || schema == null || schema.hidden()) continue;
            try {
                var property = ProjectMasterDO.class.getDeclaredField(exposed.getName());
                String type = type(property.getType());
                if (type == null || Modifier.isStatic(property.getModifiers())) continue;
                property.setAccessible(true);
                String code = "project." + property.getName();
                Field field = new Field(code, schema.description().isBlank() ? property.getName() : schema.description(),
                        type, BASELINE.path("fields").path(code).path("availableAtCreation").asBoolean(false));
                entries.put(code, new Entry(field, project -> {
                    try { return property.get(project); }
                    catch (IllegalAccessException impossible) { throw new IllegalStateException(impossible); }
                }));
            } catch (NoSuchFieldException presentationOnly) {
                // Computed presentation values have no authoritative entity property.
            }
        }
        // Compatibility aliases are versioned configuration, never mutable field remappings.
        BASELINE.path("aliases").properties().forEach(alias -> {
            JsonNode binding = alias.getValue();
            Entry source = entries.get("project." + binding.path("property").asText());
            if (source == null || entries.containsKey(alias.getKey())) throw new IllegalStateException("Invalid project field alias");
            String transform = binding.path("transform").asText("VALUE");
            if (!Set.of("VALUE", "PRESENT").contains(transform)) throw new IllegalStateException("Invalid project field transform");
            Field field = new Field(alias.getKey(), binding.path("label").asText(source.field().label()),
                    "PRESENT".equals(transform) ? "BOOLEAN" : source.field().valueType(),
                    BASELINE.path("fields").path(alias.getKey()).path("availableAtCreation").asBoolean(false));
            entries.put(alias.getKey(), new Entry(field, project -> "PRESENT".equals(transform)
                    ? source.read().apply(project) != null : source.read().apply(project)));
        });
        return Collections.unmodifiableMap(entries);
    }

    private static String type(Class<?> type) {
        if (type == String.class) return "TEXT";
        if (type == Boolean.class || type == boolean.class) return "BOOLEAN";
        if (Number.class.isAssignableFrom(type)) return "NUMBER";
        if (type == LocalDate.class) return "DATE";
        if (type == LocalDateTime.class) return "DATETIME";
        return null;
    }

    private static JsonNode baseline() {
        try (var input = new ClassPathResource("rules/project-field-compatibility.json").getInputStream()) {
            return JsonUtils.parseObject(new String(input.readAllBytes(), StandardCharsets.UTF_8), JsonNode.class);
        } catch (java.io.IOException failure) { throw new IllegalStateException("Project rule field configuration unavailable", failure); }
    }
}
