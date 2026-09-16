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
 * {@link ListConfigSchema} 与预置列表模板的单元测试。
 *
 * <p>验证 3 个预置列表模板 JSON 格式正确、配置完整，
 * 以及 {@link ListConfigSchema} 中声明的列类型 / 筛选类型 / 动作类型常量
 * 与模板中使用的一致。</p>
 */
@DisplayName("ListConfigSchema 与预置列表模板单元测试")
class ListConfigSchemaTest {

    private static ObjectMapper objectMapper;

    /** 3 个预置列表模板文件名 */
    private static final List<String> TEMPLATE_FILES = List.of(
            "lowcode-templates/list/project-list.template.json",
            "lowcode-templates/list/asset-list.template.json",
            "lowcode-templates/list/settlement-list.template.json"
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
    @DisplayName("3 个预置列表模板均存在且可解析为 JSON")
    void templates_shouldExistAndBeParseable() throws Exception {
        for (String path : TEMPLATE_FILES) {
            JsonNode node = loadTemplate(path);
            assertNotNull(node, "模板解析结果为空: " + path);
            assertTrue(node.isObject(), "模板顶层应为对象: " + path);
        }
    }

    @Test
    @DisplayName("每个模板应包含 LowCodeList 必要字段：code、name、listConfig")
    void templates_shouldContainListMetadata() throws Exception {
        for (String path : TEMPLATE_FILES) {
            JsonNode node = loadTemplate(path);
            assertTrue(node.has("code"), "缺少 code: " + path);
            assertTrue(node.has("name"), "缺少 name: " + path);
            assertTrue(node.has("listConfig"), "缺少 listConfig: " + path);
            assertFalse(node.get("code").asText().isBlank(), "code 不能为空: " + path);
            assertFalse(node.get("name").asText().isBlank(), "name 不能为空: " + path);
            assertTrue(node.get("listConfig").isObject(), "listConfig 应为对象: " + path);
        }
    }

    @Test
    @DisplayName("listConfig 应包含 columns 数组与 searchApi")
    void listConfig_shouldContainColumnsAndSearchApi() throws Exception {
        for (String path : TEMPLATE_FILES) {
            JsonNode listConfig = loadTemplate(path).get("listConfig");
            assertTrue(listConfig.has("columns"), "缺少 columns: " + path);
            assertTrue(listConfig.get("columns").isArray(), "columns 应为数组: " + path);
            assertTrue(listConfig.get("columns").size() > 0, "columns 不能为空: " + path);
            assertTrue(listConfig.has("searchApi"), "缺少 searchApi: " + path);
            assertFalse(listConfig.get("searchApi").asText().isBlank(),
                    "searchApi 不能为空: " + path);
        }
    }

    @Test
    @DisplayName("每个列应包含必要属性：id、prop、label、type")
    void columns_shouldContainRequiredProperties() throws Exception {
        for (String path : TEMPLATE_FILES) {
            JsonNode columns = loadTemplate(path).get("listConfig").get("columns");
            for (JsonNode col : columns) {
                assertTrue(col.has("id"), "列缺少 id: " + path);
                assertTrue(col.has("prop"), "列缺少 prop: " + path);
                assertTrue(col.has("label"), "列缺少 label: " + path);
                assertTrue(col.has("type"), "列缺少 type: " + path);
                assertFalse(col.get("id").asText().isBlank(), "id 不能为空: " + path);
                assertFalse(col.get("prop").asText().isBlank(), "prop 不能为空: " + path);
                assertFalse(col.get("label").asText().isBlank(), "label 不能为空: " + path);
                assertFalse(col.get("type").asText().isBlank(), "type 不能为空: " + path);
            }
        }
    }

    @Test
    @DisplayName("模板中使用的列类型应全部在 ListConfigSchema 常量中声明")
    void columnTypes_shouldMatchSchemaConstants() throws Exception {
        List<String> declaredTypes = List.of(
                ListConfigSchema.COL_TEXT,
                ListConfigSchema.COL_IMAGE,
                ListConfigSchema.COL_TAG,
                ListConfigSchema.COL_DATE,
                ListConfigSchema.COL_DATETIME,
                ListConfigSchema.COL_CURRENCY,
                ListConfigSchema.COL_PERCENT,
                ListConfigSchema.COL_LINK,
                ListConfigSchema.COL_DICT,
                ListConfigSchema.COL_CUSTOM
        );
        for (String path : TEMPLATE_FILES) {
            JsonNode columns = loadTemplate(path).get("listConfig").get("columns");
            for (JsonNode col : columns) {
                String type = col.get("type").asText();
                assertTrue(declaredTypes.contains(type),
                        "未声明的列类型 '" + type + "' in " + path);
            }
        }
    }

    @Test
    @DisplayName("模板中使用的筛选类型应全部在 ListConfigSchema 常量中声明")
    void filterTypes_shouldMatchSchemaConstants() throws Exception {
        List<String> declaredTypes = List.of(
                ListConfigSchema.FILTER_INPUT,
                ListConfigSchema.FILTER_SELECT,
                ListConfigSchema.FILTER_DATE,
                ListConfigSchema.FILTER_DATERANGE,
                ListConfigSchema.FILTER_CASCADER
        );
        for (String path : TEMPLATE_FILES) {
            JsonNode filters = loadTemplate(path).get("listConfig").get("filters");
            if (filters == null || filters.isNull()) continue;
            assertTrue(filters.isArray(), "filters 应为数组: " + path);
            for (JsonNode f : filters) {
                String type = f.get("type").asText();
                assertTrue(declaredTypes.contains(type),
                        "未声明的筛选类型 '" + type + "' in " + path);
            }
        }
    }

    @Test
    @DisplayName("模板中使用的动作类型应全部在 ListConfigSchema 常量中声明")
    void actionTypes_shouldMatchSchemaConstants() throws Exception {
        List<String> declaredActions = List.of(
                ListConfigSchema.ACTION_CREATE,
                ListConfigSchema.ACTION_EDIT,
                ListConfigSchema.ACTION_VIEW,
                ListConfigSchema.ACTION_DELETE,
                ListConfigSchema.ACTION_CUSTOM
        );
        for (String path : TEMPLATE_FILES) {
            JsonNode listConfig = loadTemplate(path).get("listConfig");
            // 行操作
            JsonNode operations = listConfig.get("operations");
            if (operations != null && operations.isArray()) {
                for (JsonNode op : operations) {
                    String action = op.get("action").asText();
                    assertTrue(declaredActions.contains(action),
                            "未声明的动作类型 '" + action + "' in operations of " + path);
                }
            }
            // 工具栏
            JsonNode toolbar = listConfig.get("toolbar");
            if (toolbar != null && toolbar.isArray()) {
                for (JsonNode op : toolbar) {
                    String action = op.get("action").asText();
                    assertTrue(declaredActions.contains(action),
                            "未声明的动作类型 '" + action + "' in toolbar of " + path);
                }
            }
        }
    }

    @Test
    @DisplayName("每个操作按钮应包含必要属性：id、label、action")
    void operations_shouldContainRequiredProperties() throws Exception {
        for (String path : TEMPLATE_FILES) {
            JsonNode listConfig = loadTemplate(path).get("listConfig");
            JsonNode operations = listConfig.get("operations");
            if (operations != null && operations.isArray()) {
                for (JsonNode op : operations) {
                    assertTrue(op.has("id"), "操作缺少 id: " + path);
                    assertTrue(op.has("label"), "操作缺少 label: " + path);
                    assertTrue(op.has("action"), "操作缺少 action: " + path);
                }
            }
            JsonNode toolbar = listConfig.get("toolbar");
            if (toolbar != null && toolbar.isArray()) {
                for (JsonNode op : toolbar) {
                    assertTrue(op.has("id"), "工具栏按钮缺少 id: " + path);
                    assertTrue(op.has("label"), "工具栏按钮缺少 label: " + path);
                    assertTrue(op.has("action"), "工具栏按钮缺少 action: " + path);
                }
            }
        }
    }

    @Test
    @DisplayName("dict 类型列应包含 dictCode 字段")
    void dictColumns_shouldContainDictCode() throws Exception {
        for (String path : TEMPLATE_FILES) {
            JsonNode columns = loadTemplate(path).get("listConfig").get("columns");
            for (JsonNode col : columns) {
                if (ListConfigSchema.COL_DICT.equals(col.get("type").asText())) {
                    assertTrue(col.has("dictCode"),
                            "dict 类型列缺少 dictCode: " + path);
                    assertFalse(col.get("dictCode").asText().isBlank(),
                            "dictCode 不能为空: " + path);
                }
            }
        }
    }

    @Test
    @DisplayName("项目列表模板应包含 projectCode 列与新增工具栏按钮")
    void projectListTemplate_shouldContainExpectedConfig() throws Exception {
        JsonNode listConfig = loadTemplate(TEMPLATE_FILES.get(0)).get("listConfig");
        JsonNode columns = listConfig.get("columns");
        boolean hasProjectCode = false;
        for (JsonNode col : columns) {
            if ("projectCode".equals(col.get("prop").asText())) {
                hasProjectCode = true;
                break;
            }
        }
        assertTrue(hasProjectCode, "项目列表模板缺少 projectCode 列");

        JsonNode toolbar = listConfig.get("toolbar");
        assertNotNull(toolbar, "项目列表模板缺少 toolbar");
        assertTrue(toolbar.isArray() && toolbar.size() > 0, "项目列表 toolbar 不能为空");
        boolean hasCreate = false;
        for (JsonNode op : toolbar) {
            if (ListConfigSchema.ACTION_CREATE.equals(op.get("action").asText())) {
                hasCreate = true;
                break;
            }
        }
        assertTrue(hasCreate, "项目列表 toolbar 应包含 create 动作");
    }

    @Test
    @DisplayName("资产列表模板应包含 assetCode 与 photo 列")
    void assetListTemplate_shouldContainAssetCodeAndPhoto() throws Exception {
        JsonNode columns = loadTemplate(TEMPLATE_FILES.get(1)).get("listConfig").get("columns");
        boolean hasAssetCode = false;
        boolean hasPhoto = false;
        for (JsonNode col : columns) {
            String prop = col.get("prop").asText();
            if ("assetCode".equals(prop)) hasAssetCode = true;
            if ("photo".equals(prop)) hasPhoto = true;
        }
        assertTrue(hasAssetCode, "资产列表模板缺少 assetCode 列");
        assertTrue(hasPhoto, "资产列表模板缺少 photo 列");
    }

    @Test
    @DisplayName("结算列表模板应包含 settlementCode 与 amount 列")
    void settlementListTemplate_shouldContainSettlementCodeAndAmount() throws Exception {
        JsonNode columns = loadTemplate(TEMPLATE_FILES.get(2)).get("listConfig").get("columns");
        boolean hasCode = false;
        boolean hasAmount = false;
        for (JsonNode col : columns) {
            String prop = col.get("prop").asText();
            if ("settlementCode".equals(prop)) hasCode = true;
            if ("amount".equals(prop)) hasAmount = true;
        }
        assertTrue(hasCode, "结算列表模板缺少 settlementCode 列");
        assertTrue(hasAmount, "结算列表模板缺少 amount 列");
    }

    @Test
    @DisplayName("ListConfigSchema 常量值应稳定且符合规范")
    void schemaConstants_shouldBeStable() {
        // 列类型
        assertEquals("text", ListConfigSchema.COL_TEXT);
        assertEquals("image", ListConfigSchema.COL_IMAGE);
        assertEquals("tag", ListConfigSchema.COL_TAG);
        assertEquals("date", ListConfigSchema.COL_DATE);
        assertEquals("datetime", ListConfigSchema.COL_DATETIME);
        assertEquals("currency", ListConfigSchema.COL_CURRENCY);
        assertEquals("percent", ListConfigSchema.COL_PERCENT);
        assertEquals("link", ListConfigSchema.COL_LINK);
        assertEquals("dict", ListConfigSchema.COL_DICT);
        assertEquals("custom", ListConfigSchema.COL_CUSTOM);
        // 筛选类型
        assertEquals("input", ListConfigSchema.FILTER_INPUT);
        assertEquals("select", ListConfigSchema.FILTER_SELECT);
        assertEquals("date", ListConfigSchema.FILTER_DATE);
        assertEquals("daterange", ListConfigSchema.FILTER_DATERANGE);
        assertEquals("cascader", ListConfigSchema.FILTER_CASCADER);
        // 动作类型
        assertEquals("create", ListConfigSchema.ACTION_CREATE);
        assertEquals("edit", ListConfigSchema.ACTION_EDIT);
        assertEquals("view", ListConfigSchema.ACTION_VIEW);
        assertEquals("delete", ListConfigSchema.ACTION_DELETE);
        assertEquals("custom", ListConfigSchema.ACTION_CUSTOM);
        // 按钮类型
        assertEquals("primary", ListConfigSchema.BTN_PRIMARY);
        assertEquals("success", ListConfigSchema.BTN_SUCCESS);
        assertEquals("warning", ListConfigSchema.BTN_WARNING);
        assertEquals("danger", ListConfigSchema.BTN_DANGER);
        assertEquals("info", ListConfigSchema.BTN_INFO);
        assertEquals("text", ListConfigSchema.BTN_TEXT);
        // 布局
        assertEquals("table", ListConfigSchema.LAYOUT_TABLE);
        assertEquals("card", ListConfigSchema.LAYOUT_CARD);
        // 对齐方式
        assertEquals("left", ListConfigSchema.ALIGN_LEFT);
        assertEquals("center", ListConfigSchema.ALIGN_CENTER);
        assertEquals("right", ListConfigSchema.ALIGN_RIGHT);
        // 固定列
        assertEquals("left", ListConfigSchema.FIXED_LEFT);
        assertEquals("right", ListConfigSchema.FIXED_RIGHT);
        assertEquals("false", ListConfigSchema.FIXED_FALSE);
        // HTTP 方法
        assertEquals("GET", ListConfigSchema.METHOD_GET);
        assertEquals("POST", ListConfigSchema.METHOD_POST);
    }
}
