package com.dp.plat.lowcode.schema;

/**
 * 列表配置 JSON Schema 规范（ListConfigSchema）。
 *
 * <p>本类同时作为 Java 类型定义与开发文档，定义了列表设计器产出的 {@code listConfig}
 * 字段的 JSON 结构。实际在数据库中存储为 JSON 字符串
 * （{@link com.dp.plat.lowcode.entity.LowCodeList#getListConfig()}），
 * 由前端列表设计器生成、由前端 {@code LowCodeListRenderer} 渲染。</p>
 *
 * <h2>顶层结构</h2>
 * <pre>{@code
 * {
 *   "title": "列表标题",
 *   "description": "列表描述",
 *   "searchApi": "/api/project/list",   // 列表数据查询 API
 *   "method": "GET",                    // 请求方法 GET / POST
 *   "pageSize": 20,                     // 默认每页条数
 *   "pageSizes": [10, 20, 50, 100],     // 每页条数可选项
 *   "layout": "table",                  // 列表布局：table / card
 *   "stripe": true,                     // 表格斑马纹
 *   "border": true,                     // 表格边框
 *   "showSelection": true,              // 是否显示多选列
 *   "showIndex": true,                  // 是否显示序号列
 *   "showPagination": true,             // 是否显示分页
 *   "columns":  [ ColumnConfig ],       // 列定义列表
 *   "filters":  [ FilterConfig ],       // 筛选项定义列表
 *   "operations":[ OperationConfig ],   // 行操作按钮列表
 *   "toolbar":  [ OperationConfig ],    // 工具栏按钮列表
 *   "export":   ExportConfig            // 导出配置
 * }
 * }</pre>
 *
 * <h2>ColumnConfig 列定义</h2>
 * <pre>{@code
 * {
 *   "id": "col_1",                  // 列唯一标识（前端生成，自动递增）
 *   "prop": "projectCode",          // 数据字段名
 *   "label": "项目编号",             // 列标题
 *   "width": 150,                   // 列宽（px）
 *   "minWidth": 100,                // 最小列宽
 *   "fixed": false,                 // 固定列：left / right / false
 *   "sortable": false,              // 是否可排序
 *   "align": "left",                // 对齐方式：left / center / right
 *   "type": "text",                 // 列类型（见 ColumnType 常量）
 *   "formatter": "dateFormat:YYYY-MM-DD", // 格式化器（按 ":" 分隔）
 *   "dictCode": "project_status",   // 字典翻译用字典编码（type=dict 时使用）
 *   "imageWidth": 60,               // 图片宽度（type=image 时使用）
 *   "imageHeight": 60,              // 图片高度（type=image 时使用）
 *   "linkUrl": "/project/detail/{id}",   // 链接跳转地址（type=link 时使用，{id} 占位）
 *   "tagType": "primary",           // el-tag 类型（type=tag 时使用）
 *   "hidden": false,                // 是否隐藏列（渲染时跳过）
 *   "editable": false               // 是否可编辑（预留）
 * }
 * }</pre>
 *
 * <h2>ColumnType 支持的列类型</h2>
 * <ul>
 *   <li>{@value #COL_TEXT}     — 文本：直接显示</li>
 *   <li>{@value #COL_IMAGE}    — 图片：el-image 缩略图，props: imageWidth, imageHeight</li>
 *   <li>{@value #COL_TAG}      — 标签：el-tag，props: tagType</li>
 *   <li>{@value #COL_DATE}     — 日期：按 YYYY-MM-DD 格式化</li>
 *   <li>{@value #COL_DATETIME} — 日期时间：按 YYYY-MM-DD HH:mm:ss 格式化</li>
 *   <li>{@value #COL_CURRENCY} — 货币：千分位 + 货币符号（默认 ¥）</li>
 *   <li>{@value #COL_PERCENT}  — 百分比：值 × 100 + %</li>
 *   <li>{@value #COL_LINK}     — 链接：router.push 到 linkUrl（{prop} 占位符替换）</li>
 *   <li>{@value #COL_DICT}     — 字典翻译：按 dictCode 查询字典 label</li>
 *   <li>{@value #COL_CUSTOM}   — 自定义：通过具名插槽渲染（slot 名为 prop）</li>
 * </ul>
 *
 * <h2>FilterConfig 筛选项定义</h2>
 * <pre>{@code
 * {
 *   "id": "filter_1",              // 筛选项唯一标识（前端生成）
 *   "prop": "status",              // 筛选字段名（绑定到查询参数的 key）
 *   "label": "状态",                // 筛选项标签
 *   "type": "select",              // 筛选类型（见 FilterType 常量）
 *   "placeholder": "请选择",        // 占位提示
 *   "options": [                   // 选项列表（type=select 时使用）
 *     { "label": "进行中", "value": "IN_PROGRESS" }
 *   ],
 *   "dictCode": "project_status",  // 字典编码（type=select 且未提供 options 时使用）
 *   "defaultValue": "",            // 默认值
 *   "span": 6,                     // 栅格宽度（1-24，inline 模式下用于响应式）
 *   "clearable": true,             // 是否可清空
 *   "multiple": false              // 是否多选（type=select 时使用）
 * }
 * }</pre>
 *
 * <h2>FilterType 支持的筛选类型</h2>
 * <ul>
 *   <li>{@value #FILTER_INPUT}    — 输入框：el-input</li>
 *   <li>{@value #FILTER_SELECT}   — 下拉选择：el-select</li>
 *   <li>{@value #FILTER_DATE}     — 日期：el-date-picker date</li>
 *   <li>{@value #FILTER_DATERANGE}— 日期范围：el-date-picker daterange</li>
 *   <li>{@value #FILTER_CASCADER} — 级联选择：el-cascader</li>
 * </ul>
 *
 * <h2>OperationConfig 操作按钮定义（行操作 / 工具栏通用）</h2>
 * <pre>{@code
 * {
 *   "id": "op_1",                  // 操作唯一标识（前端生成）
 *   "label": "编辑",                // 按钮文本
 *   "type": "primary",             // 按钮类型（见 OperationType 常量）
 *   "icon": "Edit",                // Element Plus 图标名
 *   "action": "edit",              // 动作类型（见 ActionType 常量）
 *   "url": "/project/edit/{id}",   // 跳转地址（action=edit/view/create 时使用，{prop} 占位）
 *   "api": "/api/project/{id}",    // 调用接口（action=delete 时使用，{prop} 占位）
 *   "method": "DELETE",            // 接口方法（action=delete/custom 时使用）
 *   "confirm": "确认删除？",        // 二次确认提示（非空时弹出 confirm 对话框）
 *   "permission": "project:update",// 权限标识（不通过 v-permission 校验则隐藏）
 *   "visible": "row.status === 'DRAFT'" // 显示条件表达式（对 row 求值，假值则隐藏）
 * }
 * }</pre>
 *
 * <h2>ActionType 支持的动作类型</h2>
 * <ul>
 *   <li>{@value #ACTION_CREATE} — 新建：跳转到 url（工具栏专用）</li>
 *   <li>{@value #ACTION_EDIT}   — 编辑：跳转到 url（带 {id}）</li>
 *   <li>{@value #ACTION_VIEW}   — 查看：跳转到 url（带 {id}）</li>
 *   <li>{@value #ACTION_DELETE} — 删除：调用 api（带 {id}），刷新列表</li>
 *   <li>{@value #ACTION_CUSTOM} — 自定义：emit operation-click 事件，由业务层处理</li>
 * </ul>
 *
 * <h2>ExportConfig 导出配置</h2>
 * <pre>{@code
 * {
 *   "enabled": true,                  // 是否启用导出
 *   "api": "/api/project/export",     // 导出接口
 *   "fileName": "项目列表",            // 下载文件名（前端可加时间戳）
 *   "withFilter": true                // 是否带当前筛选条件
 * }
 * }</pre>
 *
 * <h2>示例（项目列表片段）</h2>
 * <pre>{@code
 * {
 *   "title": "项目列表",
 *   "searchApi": "/api/project",
 *   "method": "GET",
 *   "pageSize": 20,
 *   "pageSizes": [10, 20, 50, 100],
 *   "layout": "table",
 *   "stripe": true,
 *   "border": true,
 *   "showSelection": true,
 *   "showIndex": true,
 *   "showPagination": true,
 *   "columns": [
 *     { "id": "col_1", "prop": "projectCode", "label": "项目编号", "width": 150, "type": "text" },
 *     { "id": "col_2", "prop": "projectName", "label": "项目名称", "minWidth": 200, "type": "text" },
 *     { "id": "col_3", "prop": "status", "label": "状态", "width": 100, "type": "dict", "dictCode": "project_status" }
 *   ],
 *   "filters": [
 *     { "id": "filter_1", "prop": "projectCode", "label": "项目编号", "type": "input", "placeholder": "请输入", "span": 6 }
 *   ],
 *   "operations": [
 *     { "id": "op_1", "label": "编辑", "type": "primary", "icon": "Edit", "action": "edit", "url": "/project/edit/{id}", "permission": "project:update" }
 *   ],
 *   "toolbar": [
 *     { "id": "op_t1", "label": "新增", "type": "primary", "icon": "Plus", "action": "create", "url": "/project/create", "permission": "project:create" }
 *   ],
 *   "export": { "enabled": true, "api": "/api/project/export", "fileName": "项目列表" }
 * }
 * }</pre>
 *
 * @see com.dp.plat.lowcode.entity.LowCodeList
 * @see com.dp.plat.lowcode.service.LowCodeListService#importConfig(String)
 */
public final class ListConfigSchema {

    private ListConfigSchema() {
    }

    // ===================== 列类型常量 =====================

    /** 列类型：文本（直接显示） */
    public static final String COL_TEXT = "text";
    /** 列类型：图片（el-image） */
    public static final String COL_IMAGE = "image";
    /** 列类型：标签（el-tag） */
    public static final String COL_TAG = "tag";
    /** 列类型：日期（YYYY-MM-DD） */
    public static final String COL_DATE = "date";
    /** 列类型：日期时间（YYYY-MM-DD HH:mm:ss） */
    public static final String COL_DATETIME = "datetime";
    /** 列类型：货币（千分位 + 货币符号） */
    public static final String COL_CURRENCY = "currency";
    /** 列类型：百分比（值 × 100 + %） */
    public static final String COL_PERCENT = "percent";
    /** 列类型：链接（路由跳转） */
    public static final String COL_LINK = "link";
    /** 列类型：字典翻译（按 dictCode 查 label） */
    public static final String COL_DICT = "dict";
    /** 列类型：自定义（通过具名插槽渲染） */
    public static final String COL_CUSTOM = "custom";

    // ===================== 筛选类型常量 =====================

    /** 筛选类型：输入框 */
    public static final String FILTER_INPUT = "input";
    /** 筛选类型：下拉选择 */
    public static final String FILTER_SELECT = "select";
    /** 筛选类型：日期 */
    public static final String FILTER_DATE = "date";
    /** 筛选类型：日期范围 */
    public static final String FILTER_DATERANGE = "daterange";
    /** 筛选类型：级联选择 */
    public static final String FILTER_CASCADER = "cascader";

    // ===================== 动作类型常量 =====================

    /** 动作：新建（工具栏专用） */
    public static final String ACTION_CREATE = "create";
    /** 动作：编辑（跳转到 url，{id} 占位） */
    public static final String ACTION_EDIT = "edit";
    /** 动作：查看（跳转到 url，{id} 占位） */
    public static final String ACTION_VIEW = "view";
    /** 动作：删除（调用 api，{id} 占位） */
    public static final String ACTION_DELETE = "delete";
    /** 动作：自定义（emit operation-click 事件） */
    public static final String ACTION_CUSTOM = "custom";

    // ===================== 按钮类型常量（与 Element Plus 一致） =====================

    /** 按钮类型：主要 */
    public static final String BTN_PRIMARY = "primary";
    /** 按钮类型：成功 */
    public static final String BTN_SUCCESS = "success";
    /** 按钮类型：警告 */
    public static final String BTN_WARNING = "warning";
    /** 按钮类型：危险 */
    public static final String BTN_DANGER = "danger";
    /** 按钮类型：信息 */
    public static final String BTN_INFO = "info";
    /** 按钮类型：文本 */
    public static final String BTN_TEXT = "text";

    // ===================== 列表布局常量 =====================

    /** 布局：表格 */
    public static final String LAYOUT_TABLE = "table";
    /** 布局：卡片 */
    public static final String LAYOUT_CARD = "card";

    // ===================== 对齐方式常量 =====================

    /** 对齐：左 */
    public static final String ALIGN_LEFT = "left";
    /** 对齐：居中 */
    public static final String ALIGN_CENTER = "center";
    /** 对齐：右 */
    public static final String ALIGN_RIGHT = "right";

    // ===================== 固定列常量 =====================

    /** 固定列：左 */
    public static final String FIXED_LEFT = "left";
    /** 固定列：右 */
    public static final String FIXED_RIGHT = "right";
    /** 固定列：不固定（false） */
    public static final String FIXED_FALSE = "false";

    // ===================== HTTP 方法常量 =====================

    /** HTTP GET */
    public static final String METHOD_GET = "GET";
    /** HTTP POST */
    public static final String METHOD_POST = "POST";
}
