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
 * {@link TabConfigSchema} 与 {@link RelatedPageConfigSchema} 以及预置模板的单元测试。
 *
 * <p>验证 4 个预置模板（2 个标签页模板 + 2 个关联页模板）的 JSON 格式正确、配置完整，
 * 以及 Schema 中声明的 PageType / SectionType / LayoutType 常量与模板中使用的一致。</p>
 */
@DisplayName("TabConfigSchema / RelatedPageConfigSchema 与预置模板单元测试")
class TabAndRelatedPageConfigSchemaTest {

    private static ObjectMapper objectMapper;

    /** 2 个预置标签页模板文件名 */
    private static final List<String> TAB_TEMPLATE_FILES = List.of(
            "lowcode-templates/tab/project-detail-tabs.template.json",
            "lowcode-templates/tab/asset-detail-tabs.template.json"
    );

    /** 2 个预置关联页模板文件名 */
    private static final List<String> RELATED_PAGE_TEMPLATE_FILES = List.of(
            "lowcode-templates/related-page/project-overview.template.json",
            "lowcode-templates/related-page/asset-overview.template.json"
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

    // ===================== 标签页模板测试 =====================

    @Test
    @DisplayName("2 个预置标签页模板均存在且可解析为 JSON")
    void tabTemplates_shouldExistAndBeParseable() throws Exception {
        for (String path : TAB_TEMPLATE_FILES) {
            JsonNode node = loadTemplate(path);
            assertNotNull(node, "模板解析结果为空: " + path);
            assertTrue(node.isObject(), "模板顶层应为对象: " + path);
        }
    }

    @Test
    @DisplayName("每个标签页模板应包含 LowCodeTab 必要字段：code、name、tabConfig")
    void tabTemplates_shouldContainTabMetadata() throws Exception {
        for (String path : TAB_TEMPLATE_FILES) {
            JsonNode node = loadTemplate(path);
            assertTrue(node.has("code"), "缺少 code: " + path);
            assertTrue(node.has("name"), "缺少 name: " + path);
            assertTrue(node.has("tabConfig"), "缺少 tabConfig: " + path);
            assertFalse(node.get("code").asText().isBlank(), "code 不能为空: " + path);
            assertFalse(node.get("name").asText().isBlank(), "name 不能为空: " + path);
            assertTrue(node.get("tabConfig").isObject(), "tabConfig 应为对象: " + path);
        }
    }

    @Test
    @DisplayName("tabConfig 应包含 tabs 数组")
    void tabConfig_shouldContainTabsArray() throws Exception {
        for (String path : TAB_TEMPLATE_FILES) {
            JsonNode tabConfig = loadTemplate(path).get("tabConfig");
            assertTrue(tabConfig.has("tabs"), "缺少 tabs: " + path);
            assertTrue(tabConfig.get("tabs").isArray(), "tabs 应为数组: " + path);
            assertTrue(tabConfig.get("tabs").size() > 0, "tabs 不能为空: " + path);
        }
    }

    @Test
    @DisplayName("每个标签项应包含必要属性：id、title、name、pageType")
    void tabItems_shouldContainRequiredProperties() throws Exception {
        for (String path : TAB_TEMPLATE_FILES) {
            JsonNode tabs = loadTemplate(path).get("tabConfig").get("tabs");
            for (JsonNode tab : tabs) {
                assertTrue(tab.has("id"), "标签缺少 id: " + path);
                assertTrue(tab.has("title"), "标签缺少 title: " + path);
                assertTrue(tab.has("name"), "标签缺少 name: " + path);
                assertTrue(tab.has("pageType"), "标签缺少 pageType: " + path);
                assertFalse(tab.get("id").asText().isBlank(), "id 不能为空: " + path);
                assertFalse(tab.get("title").asText().isBlank(), "title 不能为空: " + path);
                assertFalse(tab.get("name").asText().isBlank(), "name 不能为空: " + path);
                assertFalse(tab.get("pageType").asText().isBlank(), "pageType 不能为空: " + path);
            }
        }
    }

    @Test
    @DisplayName("模板中使用的页面类型应全部在 TabConfigSchema 常量中声明")
    void pageTypes_shouldMatchSchemaConstants() throws Exception {
        List<String> declaredTypes = List.of(
                TabConfigSchema.PAGE_FORM,
                TabConfigSchema.PAGE_LIST,
                TabConfigSchema.PAGE_RELATED_PAGE,
                TabConfigSchema.PAGE_CUSTOM
        );
        for (String path : TAB_TEMPLATE_FILES) {
            JsonNode tabs = loadTemplate(path).get("tabConfig").get("tabs");
            for (JsonNode tab : tabs) {
                String type = tab.get("pageType").asText();
                assertTrue(declaredTypes.contains(type),
                        "未声明的页面类型 '" + type + "' in " + path);
            }
        }
    }

    @Test
    @DisplayName("非 custom 类型的标签应包含 pageCode")
    void nonCustomTabs_shouldContainPageCode() throws Exception {
        for (String path : TAB_TEMPLATE_FILES) {
            JsonNode tabs = loadTemplate(path).get("tabConfig").get("tabs");
            for (JsonNode tab : tabs) {
                String type = tab.get("pageType").asText();
                if (!TabConfigSchema.PAGE_CUSTOM.equals(type)) {
                    assertTrue(tab.has("pageCode"),
                            "非 custom 类型标签缺少 pageCode: " + path);
                    assertFalse(tab.get("pageCode").asText().isBlank(),
                            "pageCode 不能为空: " + path);
                }
            }
        }
    }

    @Test
    @DisplayName("标签 name 在同一标签页内应唯一")
    void tabNames_shouldBeUniqueWithinConfig() throws Exception {
        for (String path : TAB_TEMPLATE_FILES) {
            JsonNode tabs = loadTemplate(path).get("tabConfig").get("tabs");
            java.util.Set<String> names = new java.util.HashSet<>();
            for (JsonNode tab : tabs) {
                String name = tab.get("name").asText();
                assertTrue(names.add(name),
                        "标签 name 重复: " + name + " in " + path);
            }
        }
    }

    @Test
    @DisplayName("项目详情标签页模板应包含 8 个标签（基本信息/资产/里程碑/任务/结算/变更/风险/问题）")
    void projectDetailTabsTemplate_shouldContain8Tabs() throws Exception {
        JsonNode tabConfig = loadTemplate(TAB_TEMPLATE_FILES.get(0)).get("tabConfig");
        JsonNode tabs = tabConfig.get("tabs");
        assertEquals(8, tabs.size(), "项目详情标签页应有 8 个 Tab");
        // 验证存在关键标签
        java.util.Set<String> names = new java.util.HashSet<>();
        for (JsonNode tab : tabs) {
            names.add(tab.get("name").asText());
        }
        assertTrue(names.contains("basic"), "应包含基本信息标签 basic");
        assertTrue(names.contains("assets"), "应包含资产标签 assets");
        assertTrue(names.contains("milestone"), "应包含里程碑标签 milestone");
        assertTrue(names.contains("settlement"), "应包含结算标签 settlement");
    }

    @Test
    @DisplayName("资产详情标签页模板应包含 4 个标签（基本信息/维修记录/RMA/质保）")
    void assetDetailTabsTemplate_shouldContain4Tabs() throws Exception {
        JsonNode tabConfig = loadTemplate(TAB_TEMPLATE_FILES.get(1)).get("tabConfig");
        JsonNode tabs = tabConfig.get("tabs");
        assertEquals(4, tabs.size(), "资产详情标签页应有 4 个 Tab");
        java.util.Set<String> names = new java.util.HashSet<>();
        for (JsonNode tab : tabs) {
            names.add(tab.get("name").asText());
        }
        assertTrue(names.contains("basic"), "应包含基本信息标签 basic");
        assertTrue(names.contains("repair"), "应包含维修记录标签 repair");
        assertTrue(names.contains("warranty"), "应包含质保信息标签 warranty");
    }

    // ===================== 关联页模板测试 =====================

    @Test
    @DisplayName("2 个预置关联页模板均存在且可解析为 JSON")
    void relatedPageTemplates_shouldExistAndBeParseable() throws Exception {
        for (String path : RELATED_PAGE_TEMPLATE_FILES) {
            JsonNode node = loadTemplate(path);
            assertNotNull(node, "模板解析结果为空: " + path);
            assertTrue(node.isObject(), "模板顶层应为对象: " + path);
        }
    }

    @Test
    @DisplayName("每个关联页模板应包含 LowCodeRelatedPage 必要字段：code、name、relatedConfig")
    void relatedPageTemplates_shouldContainMetadata() throws Exception {
        for (String path : RELATED_PAGE_TEMPLATE_FILES) {
            JsonNode node = loadTemplate(path);
            assertTrue(node.has("code"), "缺少 code: " + path);
            assertTrue(node.has("name"), "缺少 name: " + path);
            assertTrue(node.has("relatedConfig"), "缺少 relatedConfig: " + path);
            assertFalse(node.get("code").asText().isBlank(), "code 不能为空: " + path);
            assertFalse(node.get("name").asText().isBlank(), "name 不能为空: " + path);
            assertTrue(node.get("relatedConfig").isObject(), "relatedConfig 应为对象: " + path);
        }
    }

    @Test
    @DisplayName("relatedConfig 应包含 sections 数组与 mainEntity")
    void relatedConfig_shouldContainSectionsAndMainEntity() throws Exception {
        for (String path : RELATED_PAGE_TEMPLATE_FILES) {
            JsonNode relatedConfig = loadTemplate(path).get("relatedConfig");
            assertTrue(relatedConfig.has("sections"), "缺少 sections: " + path);
            assertTrue(relatedConfig.get("sections").isArray(), "sections 应为数组: " + path);
            assertTrue(relatedConfig.get("sections").size() > 0, "sections 不能为空: " + path);
            assertTrue(relatedConfig.has("mainEntity"), "缺少 mainEntity: " + path);
            assertFalse(relatedConfig.get("mainEntity").asText().isBlank(),
                    "mainEntity 不能为空: " + path);
        }
    }

    @Test
    @DisplayName("每个区块应包含必要属性：id、title、type")
    void sections_shouldContainRequiredProperties() throws Exception {
        for (String path : RELATED_PAGE_TEMPLATE_FILES) {
            JsonNode sections = loadTemplate(path).get("relatedConfig").get("sections");
            for (JsonNode section : sections) {
                assertTrue(section.has("id"), "区块缺少 id: " + path);
                assertTrue(section.has("title"), "区块缺少 title: " + path);
                assertTrue(section.has("type"), "区块缺少 type: " + path);
                assertFalse(section.get("id").asText().isBlank(), "id 不能为空: " + path);
                assertFalse(section.get("title").asText().isBlank(), "title 不能为空: " + path);
                assertFalse(section.get("type").asText().isBlank(), "type 不能为空: " + path);
            }
        }
    }

    @Test
    @DisplayName("模板中使用的区块类型应全部在 RelatedPageConfigSchema 常量中声明")
    void sectionTypes_shouldMatchSchemaConstants() throws Exception {
        List<String> declaredTypes = List.of(
                RelatedPageConfigSchema.SECTION_FORM,
                RelatedPageConfigSchema.SECTION_LIST,
                RelatedPageConfigSchema.SECTION_TAB,
                RelatedPageConfigSchema.SECTION_CUSTOM
        );
        for (String path : RELATED_PAGE_TEMPLATE_FILES) {
            JsonNode sections = loadTemplate(path).get("relatedConfig").get("sections");
            for (JsonNode section : sections) {
                String type = section.get("type").asText();
                assertTrue(declaredTypes.contains(type),
                        "未声明的区块类型 '" + type + "' in " + path);
            }
        }
    }

    @Test
    @DisplayName("模板中使用的布局类型应全部在 RelatedPageConfigSchema 常量中声明")
    void layoutTypes_shouldMatchSchemaConstants() throws Exception {
        List<String> declaredLayouts = List.of(
                RelatedPageConfigSchema.LAYOUT_GRID,
                RelatedPageConfigSchema.LAYOUT_TABS,
                RelatedPageConfigSchema.LAYOUT_COLLAPSE
        );
        for (String path : RELATED_PAGE_TEMPLATE_FILES) {
            JsonNode relatedConfig = loadTemplate(path).get("relatedConfig");
            if (relatedConfig.has("layout")) {
                String layout = relatedConfig.get("layout").asText();
                assertTrue(declaredLayouts.contains(layout),
                        "未声明的布局类型 '" + layout + "' in " + path);
            }
        }
    }

    @Test
    @DisplayName("非 custom 类型的区块应包含 pageCode")
    void nonCustomSections_shouldContainPageCode() throws Exception {
        for (String path : RELATED_PAGE_TEMPLATE_FILES) {
            JsonNode sections = loadTemplate(path).get("relatedConfig").get("sections");
            for (JsonNode section : sections) {
                String type = section.get("type").asText();
                if (!RelatedPageConfigSchema.SECTION_CUSTOM.equals(type)) {
                    assertTrue(section.has("pageCode"),
                            "非 custom 类型区块缺少 pageCode: " + path);
                    assertFalse(section.get("pageCode").asText().isBlank(),
                            "pageCode 不能为空: " + path);
                }
            }
        }
    }

    @Test
    @DisplayName("区块 id 在同一关联页内应唯一")
    void sectionIds_shouldBeUniqueWithinConfig() throws Exception {
        for (String path : RELATED_PAGE_TEMPLATE_FILES) {
            JsonNode sections = loadTemplate(path).get("relatedConfig").get("sections");
            java.util.Set<String> ids = new java.util.HashSet<>();
            for (JsonNode section : sections) {
                String id = section.get("id").asText();
                assertTrue(ids.add(id),
                        "区块 id 重复: " + id + " in " + path);
            }
        }
    }

    @Test
    @DisplayName("项目概览关联页模板应包含 4 个区块（项目信息/关键指标/里程碑/团队信息）")
    void projectOverviewTemplate_shouldContain4Sections() throws Exception {
        JsonNode relatedConfig = loadTemplate(RELATED_PAGE_TEMPLATE_FILES.get(0)).get("relatedConfig");
        JsonNode sections = relatedConfig.get("sections");
        assertEquals(4, sections.size(), "项目概览关联页应有 4 个区块");
        // 验证存在关键区块
        java.util.Set<String> titles = new java.util.HashSet<>();
        for (JsonNode section : sections) {
            titles.add(section.get("title").asText());
        }
        assertTrue(titles.contains("项目信息"), "应包含项目信息区块");
        assertTrue(titles.contains("关键指标"), "应包含关键指标区块");
        assertTrue(titles.contains("里程碑时间线"), "应包含里程碑时间线区块");
        assertTrue(titles.contains("团队信息"), "应包含团队信息区块");
    }

    @Test
    @DisplayName("资产概览关联页模板应包含 4 个区块（资产信息/项目关联/维保记录/操作日志）")
    void assetOverviewTemplate_shouldContain4Sections() throws Exception {
        JsonNode relatedConfig = loadTemplate(RELATED_PAGE_TEMPLATE_FILES.get(1)).get("relatedConfig");
        JsonNode sections = relatedConfig.get("sections");
        assertEquals(4, sections.size(), "资产概览关联页应有 4 个区块");
        java.util.Set<String> titles = new java.util.HashSet<>();
        for (JsonNode section : sections) {
            titles.add(section.get("title").asText());
        }
        assertTrue(titles.contains("资产信息"), "应包含资产信息区块");
        assertTrue(titles.contains("项目关联"), "应包含项目关联区块");
        assertTrue(titles.contains("维保记录"), "应包含维保记录区块");
        assertTrue(titles.contains("操作日志"), "应包含操作日志区块");
    }

    // ===================== Schema 常量稳定性测试 =====================

    @Test
    @DisplayName("TabConfigSchema 常量值应稳定且符合规范")
    void tabSchemaConstants_shouldBeStable() {
        // 页面类型
        assertEquals("form", TabConfigSchema.PAGE_FORM);
        assertEquals("list", TabConfigSchema.PAGE_LIST);
        assertEquals("related-page", TabConfigSchema.PAGE_RELATED_PAGE);
        assertEquals("custom", TabConfigSchema.PAGE_CUSTOM);
        // el-tabs type
        assertEquals("card", TabConfigSchema.TYPE_CARD);
        assertEquals("border-card", TabConfigSchema.TYPE_BORDER_CARD);
        assertEquals("plain", TabConfigSchema.TYPE_PLAIN);
        // 标签位置
        assertEquals("top", TabConfigSchema.POSITION_TOP);
        assertEquals("right", TabConfigSchema.POSITION_RIGHT);
        assertEquals("bottom", TabConfigSchema.POSITION_BOTTOM);
        assertEquals("left", TabConfigSchema.POSITION_LEFT);
    }

    @Test
    @DisplayName("RelatedPageConfigSchema 常量值应稳定且符合规范")
    void relatedPageSchemaConstants_shouldBeStable() {
        // 区块类型
        assertEquals("form", RelatedPageConfigSchema.SECTION_FORM);
        assertEquals("list", RelatedPageConfigSchema.SECTION_LIST);
        assertEquals("tab", RelatedPageConfigSchema.SECTION_TAB);
        assertEquals("custom", RelatedPageConfigSchema.SECTION_CUSTOM);
        // 布局类型
        assertEquals("grid", RelatedPageConfigSchema.LAYOUT_GRID);
        assertEquals("tabs", RelatedPageConfigSchema.LAYOUT_TABS);
        assertEquals("collapse", RelatedPageConfigSchema.LAYOUT_COLLAPSE);
    }
}
