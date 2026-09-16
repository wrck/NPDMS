package com.dp.plat.lowcode.schema;

/**
 * 标签页配置 JSON Schema 规范（TabConfigSchema）。
 *
 * <p>本类同时作为 Java 类型定义与开发文档，定义了标签页设计器产出的 {@code tabConfig}
 * 字段的 JSON 结构。实际在数据库中存储为 JSON 字符串
 * （{@link com.dp.plat.lowcode.entity.LowCodeTab#getTabConfig()}），
 * 由前端标签页设计器生成、由前端 {@code LowCodeTabRenderer} 渲染。</p>
 *
 * <h2>顶层结构</h2>
 * <pre>{@code
 * {
 *   "title": "标签页标题",                  // 标签页整体标题（可选，用于设计器展示）
 *   "description": "标签页描述",             // 描述信息（可选）
 *   "type": "card",                          // el-tabs type：card / border-card / plain（默认 border-card）
 *   "tabPosition": "top",                    // 标签位置：top / right / bottom / left
 *   "closable": false,                       // 标签是否可关闭（el-tabs closable）
 *   "addable": false,                        // 是否可新增标签（el-tabs addable）
 *   "editable": false,                       // 是否可编辑标签名（el-tabs editable）
 *   "tabs": [ TabItemConfig ]                // 标签项定义列表（按数组顺序渲染）
 * }
 * }</pre>
 *
 * <h2>TabItemConfig 标签项定义</h2>
 * <pre>{@code
 * {
 *   "id": "tab_1",                           // 标签项唯一标识（前端生成，自动递增）
 *   "title": "基本信息",                      // 标签显示文本
 *   "name": "basic",                         // 标签标识（用于 v-model 绑定，必填且唯一）
 *   "lazy": true,                            // 是否懒加载（el-tab-pane lazy，首次激活才渲染内容）
 *   "disabled": false,                       // 是否禁用此标签
 *   "icon": "Document",                      // Element Plus 图标名（可选，显示在标签标题前）
 *   "pageCode": "project_basic_form",        // 引用的低代码页面编码（form/list/related-page 类型时必填）
 *   "pageType": "form",                      // 引用页面类型（见 PageType 常量）
 *   "pageUrl": "/project/{id}/basic",        // 自定义页面 URL（pageType=custom 时使用，{prop} 占位）
 *   "props": {                               // 传递给子页面的参数（值支持模板变量）
 *     "projectId": "${route.params.id}",
 *     "mode": "view"
 *   },
 *   "visible": "row.status === 'IN_PROGRESS'" // 显示条件表达式（对 contextData 求值，假值则隐藏）
 * }
 * }</pre>
 *
 * <h2>PageType 支持的页面类型</h2>
 * <ul>
 *   <li>{@value #PAGE_FORM}         — 表单：渲染 {@code LowCodeFormRenderer}，需提供 pageCode（form 配置编码）</li>
 *   <li>{@value #PAGE_LIST}         — 列表：渲染 {@code LowCodeListRenderer}，需提供 pageCode（list 配置编码）</li>
 *   <li>{@value #PAGE_RELATED_PAGE} — 关联页：渲染 {@code LowCodeRelatedPageRenderer}，需提供 pageCode（related-page 配置编码）</li>
 *   <li>{@value #PAGE_CUSTOM}       — 自定义：iframe 嵌入 pageUrl 或 router-view 跳转，由业务层决定</li>
 * </ul>
 *
 * <h2>props 模板变量解析</h2>
 * <p>{@code tab.props} 中字符串值若包含 {@code ${...}} 会被渲染器解析：</p>
 * <ul>
 *   <li>{@code ${route.params.id}} — 当前路由参数（来自 vue-router useRoute）</li>
 *   <li>{@code ${route.query.code}} — 当前路由 query 参数</li>
 *   <li>{@code ${row.fieldName}} — 上下文数据 row 对象的字段（contextData.row）</li>
 *   <li>{@code ${context.fieldName}} — 上下文数据 context 对象的字段（contextData.context）</li>
 *   <li>{@code ${user.userId}} — 当前登录用户 ID（来自 user store）</li>
 * </ul>
 * <p>非字符串值（number/boolean/object/array）原样透传。</p>
 *
 * <h2>visible 显示条件表达式</h2>
 * <p>表达式在渲染器中以 {@code new Function('row', 'context', 'route', 'user', 'return (' + expr + ')')}
 * 形式编译执行，其中：</p>
 * <ul>
 *   <li>{@code row} — contextData.row（如当前实体行数据）</li>
 *   <li>{@code context} — contextData.context（业务自定义上下文）</li>
 *   <li>{@code route} — 当前路由对象</li>
 *   <li>{@code user} — 当前登录用户对象</li>
 * </ul>
 * <p>表达式返回假值（false / 0 / '' / null / undefined）时该标签项不渲染。
 * 留空表示始终显示。出于安全考虑，禁用 {@code eval} 与访问 {@code window}。</p>
 *
 * <h2>el-tabs type 取值说明</h2>
 * <ul>
 *   <li>{@value #TYPE_CARD}        — 卡片样式（el-tabs type="card"）</li>
 *   <li>{@value #TYPE_BORDER_CARD} — 带边框卡片样式（el-tabs type="border-card"，默认）</li>
 *   <li>{@value #TYPE_PLAIN}       — 无样式（el-tabs type="" / plain）</li>
 * </ul>
 *
 * <h2>tabPosition 取值说明</h2>
 * <ul>
 *   <li>{@value #POSITION_TOP}    — 标签位于顶部（默认）</li>
 *   <li>{@value #POSITION_RIGHT}  — 标签位于右侧</li>
 *   <li>{@value #POSITION_BOTTOM} — 标签位于底部</li>
 *   <li>{@value #POSITION_LEFT}   — 标签位于左侧</li>
 * </ul>
 *
 * <h2>示例（项目详情标签页片段）</h2>
 * <pre>{@code
 * {
 *   "title": "项目详情",
 *   "type": "border-card",
 *   "tabPosition": "top",
 *   "tabs": [
 *     {
 *       "id": "tab_1", "title": "基本信息", "name": "basic",
 *       "pageType": "form", "pageCode": "project_basic_form",
 *       "lazy": true, "props": { "projectId": "${route.params.id}" }
 *     },
 *     {
 *       "id": "tab_2", "title": "资产清单", "name": "assets",
 *       "pageType": "list", "pageCode": "project_asset_list",
 *       "lazy": true
 *     },
 *     {
 *       "id": "tab_3", "title": "结算", "name": "settlement",
 *       "pageType": "related-page", "pageCode": "project_settlement_related",
 *       "lazy": true, "visible": "row.status === 'COMPLETED' || row.status === 'IN_PROGRESS'"
 *     }
 *   ]
 * }
 * }</pre>
 *
 * @see com.dp.plat.lowcode.entity.LowCodeTab
 * @see com.dp.plat.lowcode.service.LowCodeTabService#importConfig(String)
 */
public final class TabConfigSchema {

    private TabConfigSchema() {
    }

    // ===================== 页面类型常量 =====================

    /** 页面类型：表单（LowCodeFormRenderer） */
    public static final String PAGE_FORM = "form";
    /** 页面类型：列表（LowCodeListRenderer） */
    public static final String PAGE_LIST = "list";
    /** 页面类型：关联页（LowCodeRelatedPageRenderer） */
    public static final String PAGE_RELATED_PAGE = "related-page";
    /** 页面类型：自定义（iframe / router-view） */
    public static final String PAGE_CUSTOM = "custom";

    // ===================== el-tabs type 常量 =====================

    /** el-tabs type：卡片样式 */
    public static final String TYPE_CARD = "card";
    /** el-tabs type：带边框卡片样式（默认） */
    public static final String TYPE_BORDER_CARD = "border-card";
    /** el-tabs type：无样式 */
    public static final String TYPE_PLAIN = "plain";

    // ===================== 标签位置常量 =====================

    /** 标签位置：顶部 */
    public static final String POSITION_TOP = "top";
    /** 标签位置：右 */
    public static final String POSITION_RIGHT = "right";
    /** 标签位置：底 */
    public static final String POSITION_BOTTOM = "bottom";
    /** 标签位置：左 */
    public static final String POSITION_LEFT = "left";
}
