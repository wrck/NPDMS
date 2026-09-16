package com.dp.plat.lowcode.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FormConfigSchema} 与预置模板的单元测试。
 *
 * <p>验证 3 个预置模板 JSON 格式正确、字段完整，
 * 以及 {@link FormConfigSchema} 中声明的字段类型常量与模板中使用的一致。</p>
 */
@DisplayName("FormConfigSchema 与预置模板单元测试")
class FormConfigSchemaTest {

    private static ObjectMapper objectMapper;

    /** 3 个预置模板文件名 */
    private static final List<String> TEMPLATE_FILES = List.of(
            "lowcode-templates/form/project-create.template.json",
            "lowcode-templates/form/asset-inbound.template.json",
            "lowcode-templates/form/settlement-create.template.json"
    );

    @BeforeAll
    static void setUp() {
        objectMapper = new ObjectMapper();
    }

    /**
     * 读取 classpath 下的模板文件并解析为 JsonNode。
     */
    private JsonNode loadTemplate(String path) throws Exception {
        Resource resource = new ClassPathResource(path);
        assertTrue(resource.exists(), "模板文件不存在: " + path);
        String json = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        return objectMapper.readTree(json);
    }

    @Test
    @DisplayName("3 个预置模板均存在且可解析为 JSON")
    void templates_shouldExistAndBeParseable() throws Exception {
        for (String path : TEMPLATE_FILES) {
            JsonNode node = loadTemplate(path);
            assertNotNull(node, "模板解析结果为空: " + path);
            assertTrue(node.isObject(), "模板顶层应为对象: " + path);
        }
    }

    @Test
    @DisplayName("每个模板应包含 LowCodeForm 必要字段：code、name、formConfig")
    void templates_shouldContainFormMetadata() throws Exception {
        for (String path : TEMPLATE_FILES) {
            JsonNode node = loadTemplate(path);
            assertTrue(node.has("code"), "缺少 code: " + path);
            assertTrue(node.has("name"), "缺少 name: " + path);
            assertTrue(node.has("formConfig"), "缺少 formConfig: " + path);
            assertFalse(node.get("code").asText().isBlank(), "code 不能为空: " + path);
            assertFalse(node.get("name").asText().isBlank(), "name 不能为空: " + path);
            assertTrue(node.get("formConfig").isObject(), "formConfig 应为对象: " + path);
        }
    }

    @Test
    @DisplayName("formConfig 应包含 fields 数组与 layout 配置")
    void formConfig_shouldContainFieldsAndLayout() throws Exception {
        for (String path : TEMPLATE_FILES) {
            JsonNode formConfig = loadTemplate(path).get("formConfig");
            assertTrue(formConfig.has("fields"), "缺少 fields: " + path);
            assertTrue(formConfig.get("fields").isArray(), "fields 应为数组: " + path);
            assertTrue(formConfig.get("fields").size() > 0, "fields 不能为空: " + path);
            assertTrue(formConfig.has("layout"), "缺少 layout: " + path);
        }
    }

    @Test
    @DisplayName("每个字段应包含必要属性：id、type、label、prop")
    void fields_shouldContainRequiredProperties() throws Exception {
        for (String path : TEMPLATE_FILES) {
            JsonNode fields = loadTemplate(path).get("formConfig").get("fields");
            for (JsonNode field : fields) {
                assertTrue(field.has("id"), "字段缺少 id: " + path);
                assertTrue(field.has("type"), "字段缺少 type: " + path);
                assertTrue(field.has("label"), "字段缺少 label: " + path);
                assertTrue(field.has("prop"), "字段缺少 prop: " + path);
                assertFalse(field.get("id").asText().isBlank(), "id 不能为空: " + path);
                assertFalse(field.get("type").asText().isBlank(), "type 不能为空: " + path);
                assertFalse(field.get("label").asText().isBlank(), "label 不能为空: " + path);
                assertFalse(field.get("prop").asText().isBlank(), "prop 不能为空: " + path);
            }
        }
    }

    @Test
    @DisplayName("模板中使用的字段类型应全部在 FormConfigSchema 常量中声明")
    void fieldTypes_shouldMatchSchemaConstants() throws Exception {
        List<String> declaredTypes = List.of(
                FormConfigSchema.TYPE_INPUT,
                FormConfigSchema.TYPE_TEXTAREA,
                FormConfigSchema.TYPE_NUMBER,
                FormConfigSchema.TYPE_PASSWORD,
                FormConfigSchema.TYPE_SELECT,
                FormConfigSchema.TYPE_RADIO,
                FormConfigSchema.TYPE_CHECKBOX,
                FormConfigSchema.TYPE_DATE,
                FormConfigSchema.TYPE_DATETIME,
                FormConfigSchema.TYPE_DATERANGE,
                FormConfigSchema.TYPE_SWITCH,
                FormConfigSchema.TYPE_RATE,
                FormConfigSchema.TYPE_SLIDER,
                FormConfigSchema.TYPE_CASCADER,
                FormConfigSchema.TYPE_UPLOAD,
                FormConfigSchema.TYPE_DIVIDER,
                FormConfigSchema.TYPE_TITLE,
                FormConfigSchema.TYPE_CUSTOM
        );
        for (String path : TEMPLATE_FILES) {
            JsonNode fields = loadTemplate(path).get("formConfig").get("fields");
            for (JsonNode field : fields) {
                String type = field.get("type").asText();
                assertTrue(declaredTypes.contains(type),
                        "未声明的字段类型 '" + type + "' in " + path);
            }
        }
    }

    @Test
    @DisplayName("layout.type 应为 grid/tabs/collapse 之一")
    void layoutType_shouldBeValid() throws Exception {
        List<String> validLayouts = List.of(
                FormConfigSchema.LAYOUT_GRID,
                FormConfigSchema.LAYOUT_TABS,
                FormConfigSchema.LAYOUT_COLLAPSE
        );
        for (String path : TEMPLATE_FILES) {
            JsonNode layout = loadTemplate(path).get("formConfig").get("layout");
            assertTrue(layout.has("type"), "layout 缺少 type: " + path);
            String type = layout.get("type").asText();
            assertTrue(validLayouts.contains(type),
                    "无效的 layout.type '" + type + "' in " + path);
        }
    }

    @Test
    @DisplayName("项目创建表单应包含 projectCode 字段")
    void projectCreateTemplate_shouldContainProjectCode() throws Exception {
        JsonNode fields = loadTemplate(TEMPLATE_FILES.get(0))
                .get("formConfig").get("fields");
        boolean hasProjectCode = false;
        for (JsonNode field : fields) {
            if ("projectCode".equals(field.get("prop").asText())) {
                hasProjectCode = true;
                break;
            }
        }
        assertTrue(hasProjectCode, "项目创建表单缺少 projectCode 字段");
    }

    @Test
    @DisplayName("资产入库表单应包含 assetCode 与 photos 字段")
    void assetInboundTemplate_shouldContainAssetCodeAndPhotos() throws Exception {
        JsonNode fields = loadTemplate(TEMPLATE_FILES.get(1))
                .get("formConfig").get("fields");
        boolean hasAssetCode = false;
        boolean hasPhotos = false;
        for (JsonNode field : fields) {
            String prop = field.get("prop").asText();
            if ("assetCode".equals(prop)) hasAssetCode = true;
            if ("photos".equals(prop)) hasPhotos = true;
        }
        assertTrue(hasAssetCode, "资产入库表单缺少 assetCode 字段");
        assertTrue(hasPhotos, "资产入库表单缺少 photos 字段");
    }

    @Test
    @DisplayName("结算创建表单应包含 settlementCode 与 amount 字段")
    void settlementCreateTemplate_shouldContainSettlementCodeAndAmount() throws Exception {
        JsonNode fields = loadTemplate(TEMPLATE_FILES.get(2))
                .get("formConfig").get("fields");
        boolean hasCode = false;
        boolean hasAmount = false;
        for (JsonNode field : fields) {
            String prop = field.get("prop").asText();
            if ("settlementCode".equals(prop)) hasCode = true;
            if ("amount".equals(prop)) hasAmount = true;
        }
        assertTrue(hasCode, "结算创建表单缺少 settlementCode 字段");
        assertTrue(hasAmount, "结算创建表单缺少 amount 字段");
    }

    @Test
    @DisplayName("FormConfigSchema 常量值应稳定且符合规范")
    void schemaConstants_shouldBeStable() {
        assertEquals("input", FormConfigSchema.TYPE_INPUT);
        assertEquals("textarea", FormConfigSchema.TYPE_TEXTAREA);
        assertEquals("number", FormConfigSchema.TYPE_NUMBER);
        assertEquals("select", FormConfigSchema.TYPE_SELECT);
        assertEquals("date", FormConfigSchema.TYPE_DATE);
        assertEquals("datetime", FormConfigSchema.TYPE_DATETIME);
        assertEquals("daterange", FormConfigSchema.TYPE_DATERANGE);
        assertEquals("switch", FormConfigSchema.TYPE_SWITCH);
        assertEquals("upload", FormConfigSchema.TYPE_UPLOAD);
        assertEquals("custom", FormConfigSchema.TYPE_CUSTOM);
        assertEquals("grid", FormConfigSchema.LAYOUT_GRID);
        assertEquals("tabs", FormConfigSchema.LAYOUT_TABS);
        assertEquals("collapse", FormConfigSchema.LAYOUT_COLLAPSE);
        assertEquals("left", FormConfigSchema.LABEL_LEFT);
        assertEquals("right", FormConfigSchema.LABEL_RIGHT);
        assertEquals("top", FormConfigSchema.LABEL_TOP);
        assertEquals("large", FormConfigSchema.SIZE_LARGE);
        assertEquals("default", FormConfigSchema.SIZE_DEFAULT);
        assertEquals("small", FormConfigSchema.SIZE_SMALL);
    }
}
