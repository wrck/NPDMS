package com.dp.plat.lowcode.schema;

/**
 * 表单配置 JSON Schema 规范（FormConfigSchema）。
 *
 * <p>本类同时作为 Java 类型定义与开发文档，定义了表单设计器产出的 {@code formConfig}
 * 字段的 JSON 结构。实际在数据库中存储为 JSON 字符串（{@link com.dp.plat.lowcode.entity.LowCodeForm#getFormConfig()}），
 * 由前端表单设计器生成、由前端 {@code LowCodeFormRenderer} 渲染。</p>
 *
 * <h2>顶层结构</h2>
 * <pre>{@code
 * {
 *   "title": "表单标题",
 *   "description": "表单描述",
 *   "labelWidth": 100,           // 标签宽度（px 或 'auto'）
 *   "labelPosition": "right",     // 标签位置：left / right / top
 *   "size": "default",            // 尺寸：large / default / small
 *   "fields": [ FieldConfig ],    // 字段定义列表
 *   "layout": LayoutConfig        // 布局配置（grid / tabs / collapse）
 * }
 * }</pre>
 *
 * <h2>FieldConfig 字段定义</h2>
 * <pre>{@code
 * {
 *   "id": "field_1",              // 字段唯一标识（前端生成，自动递增）
 *   "type": "input",              // 字段类型（见 FieldType 常量）
 *   "label": "字段标签",           // 显示标签
 *   "prop": "fieldName",          // 数据字段名（绑定到 modelValue 的 key）
 *   "placeholder": "请输入",
 *   "defaultValue": "",           // 默认值
 *   "required": false,            // 是否必填
 *   "disabled": false,            // 是否禁用
 *   "readonly": false,            // 是否只读
 *   "hidden": false,              // 是否隐藏（渲染时跳过）
 *   "clearable": true,            // 是否可清空
 *   "span": 24,                   // 栅格宽度（1-24）
 *   "rules": [                    // 自定义校验规则（el-form rules 格式）
 *     { "pattern": "^\\d+$", "message": "只能输入数字", "trigger": "blur" }
 *   ],
 *   "props": { ... },             // 类型特定属性（见下文 FieldType）
 *   "events": {                   // 事件回调名（前端约定，由业务层注入实现）
 *     "change": "onFieldChange"
 *   }
 * }
 * }</pre>
 *
 * <h2>FieldType 支持的字段类型</h2>
 * <ul>
 *   <li>{@value #TYPE_INPUT}    — 单行文本，props: { maxlength, showWordLimit, prefixIcon, suffixIcon }</li>
 *   <li>{@value #TYPE_TEXTAREA} — 多行文本，props: { rows, maxlength, showWordLimit, autosize }</li>
 *   <li>{@value #TYPE_NUMBER}   — 数字，props: { min, max, step, precision, controlsPosition }</li>
 *   <li>{@value #TYPE_PASSWORD} — 密码，props: { showPassword, maxlength }</li>
 *   <li>{@value #TYPE_SELECT}   — 下拉选择，props: { options:[{label,value}], multiple, filterable, collapseTags }</li>
 *   <li>{@value #TYPE_RADIO}    — 单选，props: { options:[{label,value}] }</li>
 *   <li>{@value #TYPE_CHECKBOX} — 多选，props: { options:[{label,value}], min, max }</li>
 *   <li>{@value #TYPE_DATE}     — 日期，props: { format, valueFormat, disabledDate }</li>
 *   <li>{@value #TYPE_DATETIME} — 日期时间，props: { format, valueFormat }</li>
 *   <li>{@value #TYPE_DATERANGE}— 日期范围，props: { format, valueFormat, startPlaceholder, endPlaceholder }</li>
 *   <li>{@value #TYPE_SWITCH}   — 开关，props: { activeText, inactiveText, activeValue, inactiveValue }</li>
 *   <li>{@value #TYPE_RATE}     — 评分，props: { max, allowHalf, showText, texts:[] }</li>
 *   <li>{@value #TYPE_SLIDER}   — 滑块，props: { min, max, step, showInput, range }</li>
 *   <li>{@value #TYPE_CASCADER} — 级联选择，props: { options:[{value,label,children}], props:{multiple,checkStrictly} }</li>
 *   <li>{@value #TYPE_UPLOAD}   — 文件上传，props: { action, limit, accept, multiple, listType, headers }</li>
 *   <li>{@value #TYPE_DIVIDER}  — 分隔线（布局），props: { direction, contentPosition, borderStyle }</li>
 *   <li>{@value #TYPE_TITLE}    — 标题（布局），props: { level }</li>
 *   <li>{@value #TYPE_CUSTOM}   — 自定义组件，props: { componentName } 由渲染器注册表解析</li>
 * </ul>
 *
 * <h2>LayoutConfig 布局配置</h2>
 * <pre>{@code
 * {
 *   "type": "grid",                // 布局类型：grid / tabs / collapse
 *   "gutter": 16,                  // 栅格间距（grid 模式）
 *   "tabs": [                      // tabs 模式
 *     { "title": "基本信息", "fields": ["field_1", "field_2"] }
 *   ],
 *   "collapse": [                  // collapse 模式
 *     { "title": "基本信息", "fields": ["field_1", "field_2"], "name": "g1" }
 *   ]
 * }
 * }</pre>
 *
 * <h2>校验规则（rules）</h2>
 * <p>遵循 Element Plus el-form rules 格式，每条规则支持：
 * required、message、trigger（blur/change）、pattern（正则）、min、max、validator（前端函数名约定）。
 * 渲染器会将 {@code field.required=true} 自动合并为 required 规则。</p>
 *
 * <h2>示例（项目创建表单片段）</h2>
 * <pre>{@code
 * {
 *   "title": "项目创建",
 *   "labelWidth": 110,
 *   "labelPosition": "right",
 *   "size": "default",
 *   "fields": [
 *     {
 *       "id": "field_1", "type": "input", "label": "项目编号", "prop": "projectCode",
 *       "required": true, "span": 12, "placeholder": "请输入项目编号",
 *       "props": { "maxlength": 32, "showWordLimit": true }
 *     },
 *     {
 *       "id": "field_2", "type": "date", "label": "计划开始日期", "prop": "planStartDate",
 *       "required": true, "span": 12,
 *       "props": { "format": "YYYY-MM-DD", "valueFormat": "YYYY-MM-DD" }
 *     }
 *   ],
 *   "layout": { "type": "grid", "gutter": 16 }
 * }
 * }</pre>
 *
 * @see com.dp.plat.lowcode.entity.LowCodeForm
 * @see com.dp.plat.lowcode.service.LowCodeFormService#importConfig(String)
 */
public final class FormConfigSchema {

    private FormConfigSchema() {
    }

    // ===================== 字段类型常量 =====================

    /** 单行文本 */
    public static final String TYPE_INPUT = "input";
    /** 多行文本 */
    public static final String TYPE_TEXTAREA = "textarea";
    /** 数字 */
    public static final String TYPE_NUMBER = "number";
    /** 密码 */
    public static final String TYPE_PASSWORD = "password";
    /** 下拉选择 */
    public static final String TYPE_SELECT = "select";
    /** 单选 */
    public static final String TYPE_RADIO = "radio";
    /** 多选 */
    public static final String TYPE_CHECKBOX = "checkbox";
    /** 日期 */
    public static final String TYPE_DATE = "date";
    /** 日期时间 */
    public static final String TYPE_DATETIME = "datetime";
    /** 日期范围 */
    public static final String TYPE_DATERANGE = "daterange";
    /** 开关 */
    public static final String TYPE_SWITCH = "switch";
    /** 评分 */
    public static final String TYPE_RATE = "rate";
    /** 滑块 */
    public static final String TYPE_SLIDER = "slider";
    /** 级联选择 */
    public static final String TYPE_CASCADER = "cascader";
    /** 文件上传 */
    public static final String TYPE_UPLOAD = "upload";
    /** 分隔线（布局组件） */
    public static final String TYPE_DIVIDER = "divider";
    /** 标题（布局组件） */
    public static final String TYPE_TITLE = "title";
    /** 自定义组件 */
    public static final String TYPE_CUSTOM = "custom";

    // ===================== 布局类型常量 =====================

    /** 布局类型：栅格 */
    public static final String LAYOUT_GRID = "grid";
    /** 布局类型：标签页 */
    public static final String LAYOUT_TABS = "tabs";
    /** 布局类型：折叠面板 */
    public static final String LAYOUT_COLLAPSE = "collapse";

    // ===================== 标签位置常量 =====================

    /** 标签位置：左 */
    public static final String LABEL_LEFT = "left";
    /** 标签位置：右 */
    public static final String LABEL_RIGHT = "right";
    /** 标签位置：上 */
    public static final String LABEL_TOP = "top";

    // ===================== 表单尺寸常量 =====================

    /** 尺寸：大 */
    public static final String SIZE_LARGE = "large";
    /** 尺寸：默认 */
    public static final String SIZE_DEFAULT = "default";
    /** 尺寸：小 */
    public static final String SIZE_SMALL = "small";
}
