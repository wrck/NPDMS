package com.dp.plat.lowcode.schema;

/**
 * 关联页配置 JSON Schema 规范（RelatedPageConfigSchema）。
 *
 * <p>本类同时作为 Java 类型定义与开发文档，定义了关联页设计器产出的 {@code relatedConfig}
 * 字段的 JSON 结构。实际在数据库中存储为 JSON 字符串
 * （{@link com.dp.plat.lowcode.entity.LowCodeRelatedPage#getRelatedConfig()}），
 * 由前端关联页设计器生成、由前端 {@code LowCodeRelatedPageRenderer} 渲染。</p>
 *
 * <p>关联页用于在主实体详情页中聚合展示多个关联内容区块（Section），
 * 例如项目详情页同时展示「项目信息表单 + 关键指标列表 + 里程碑时间线 + 团队信息」。
 * 与标签页（{@link TabConfigSchema}）的差异在于：关联页强调多区块聚合视图，
 * 标签页强调通过 Tab 切换不同视角。</p>
 *
 * <h2>顶层结构</h2>
 * <pre>{@code
 * {
 *   "title": "关联页标题",                  // 关联页整体标题（可选）
 *   "description": "关联页描述",             // 描述信息（可选）
 *   "mainEntity": "project",                // 主实体类型（如 project / asset / settlement）
 *   "sections": [ SectionConfig ],          // 区块定义列表
 *   "layout": "grid",                       // 布局方式：grid / tabs / collapse（见 LayoutType 常量）
 *   "gutter": 16                            // 栅格间距（grid 模式生效，默认 16）
 * }
 * }</pre>
 *
 * <h2>SectionConfig 区块定义</h2>
 * <pre>{@code
 * {
 *   "id": "section_1",                      // 区块唯一标识（前端生成，自动递增）
 *   "title": "项目信息",                     // 区块标题（grid 模式下显示为 card header，tabs/collapse 模式下显示为 tab/collapse 标题）
 *   "type": "form",                         // 区块类型（见 SectionType 常量）
 *   "pageCode": "project_basic_form",       // 引用的低代码页面编码（form/list/related-page 类型时必填）
 *   "pageUrl": "/project/{id}",             // 自定义页面 URL（type=custom 时使用，{prop} 占位）
 *   "span": 24,                             // 栅格宽度 1-24（grid 模式生效，默认 24，可设置 12 实现一行两区块）
 *   "order": 1,                             // 排序号（升序，相同 order 按数组顺序，默认 100）
 *   "visible": "row.status !== 'DRAFT'",    // 显示条件表达式（对 contextData 求值，假值则隐藏）
 *   "props": {                              // 传递给子页面的参数（值支持模板变量，规则同 TabConfigSchema）
 *     "projectId": "${route.params.id}"
 *   }
 * }
 * }</pre>
 *
 * <h2>SectionType 支持的区块类型</h2>
 * <ul>
 *   <li>{@value #SECTION_FORM}         — 表单：渲染 {@code LowCodeFormRenderer}，需提供 pageCode（form 配置编码）</li>
 *   <li>{@value #SECTION_LIST}         — 列表：渲染 {@code LowCodeListRenderer}，需提供 pageCode（list 配置编码）</li>
 *   <li>{@value #SECTION_TAB}          — 标签页：渲染 {@code LowCodeTabRenderer}，需提供 pageCode（tab 配置编码）</li>
 *   <li>{@value #SECTION_CUSTOM}       — 自定义：iframe 嵌入 pageUrl 或 router-view 跳转</li>
 * </ul>
 *
 * <h2>LayoutType 支持的布局类型</h2>
 * <ul>
 *   <li>{@value #LAYOUT_GRID}     — 栅格布局：{@code el-row + el-col}，按 section.span 切分宽度</li>
 *   <li>{@value #LAYOUT_TABS}     — 标签页布局：{@code el-tabs}，每个 section 为一个 tab-pane</li>
 *   <li>{@value #LAYOUT_COLLAPSE} — 折叠面板布局：{@code el-collapse}，每个 section 为一个 collapse-item</li>
 * </ul>
 *
 * <h2>props 模板变量解析</h2>
 * <p>与 {@link TabConfigSchema} 规则一致：{@code section.props} 中字符串值若包含 {@code ${...}}
 * 会被渲染器解析为 route/row/context/user 上下文值。详见 {@link TabConfigSchema} 文档。</p>
 *
 * <h2>visible 显示条件表达式</h2>
 * <p>与 {@link TabConfigSchema} 规则一致：表达式在渲染器中以 {@code new Function('row', 'context', 'route', 'user', 'return (' + expr + ')')}
 * 形式编译执行，返回假值则该区块不渲染。</p>
 *
 * <h2>示例（项目概览关联页片段）</h2>
 * <pre>{@code
 * {
 *   "title": "项目概览",
 *   "mainEntity": "project",
 *   "layout": "grid",
 *   "gutter": 16,
 *   "sections": [
 *     {
 *       "id": "section_1", "title": "项目信息", "type": "form",
 *       "pageCode": "project_basic_form", "span": 24, "order": 1,
 *       "props": { "projectId": "${route.params.id}" }
 *     },
 *     {
 *       "id": "section_2", "title": "关键指标", "type": "list",
 *       "pageCode": "project_kpi_list", "span": 24, "order": 2
 *     },
 *     {
 *       "id": "section_3", "title": "里程碑", "type": "list",
 *       "pageCode": "project_milestone_list", "span": 12, "order": 3
 *     },
 *     {
 *       "id": "section_4", "title": "团队信息", "type": "list",
 *       "pageCode": "project_team_list", "span": 12, "order": 4
 *     }
 *   ]
 * }
 * }</pre>
 *
 * @see com.dp.plat.lowcode.entity.LowCodeRelatedPage
 * @see com.dp.plat.lowcode.service.LowCodeRelatedPageService#importConfig(String)
 * @see TabConfigSchema
 */
public final class RelatedPageConfigSchema {

    private RelatedPageConfigSchema() {
    }

    // ===================== 区块类型常量 =====================

    /** 区块类型：表单（LowCodeFormRenderer） */
    public static final String SECTION_FORM = "form";
    /** 区块类型：列表（LowCodeListRenderer） */
    public static final String SECTION_LIST = "list";
    /** 区块类型：标签页（LowCodeTabRenderer） */
    public static final String SECTION_TAB = "tab";
    /** 区块类型：自定义（iframe / router-view） */
    public static final String SECTION_CUSTOM = "custom";

    // ===================== 布局类型常量 =====================

    /** 布局：栅格（el-row + el-col） */
    public static final String LAYOUT_GRID = "grid";
    /** 布局：标签页（el-tabs） */
    public static final String LAYOUT_TABS = "tabs";
    /** 布局：折叠面板（el-collapse） */
    public static final String LAYOUT_COLLAPSE = "collapse";
}
