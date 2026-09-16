/**
 * 组件属性定义（schema 驱动的属性面板渲染依据，借鉴 NocoBase JSONSchema）。
 *
 * <p>属性面板根据每个 propDef 的 type 动态渲染对应的 Element Plus 控件：
 * <ul>
 *   <li>boolean → el-switch</li>
 *   <li>number → el-input-number</li>
 *   <li>string → el-input</li>
 *   <li>select → el-select + el-option（从 options 渲染）</li>
 *   <li>color → el-color-picker</li>
 *   <li>date → el-date-picker</li>
 *   <li>array → 动态列表（按 itemProp 递归渲染，带新增/删除）</li>
 *   <li>object → 折叠面板（按 properties 递归渲染子属性）</li>
 *   <li>code → ExpressionEditor（批次1表达式编辑器）</li>
 *   <li>expression → ExpressionEditor + language 切换</li>
 * </ul>
 * </p>
 */
export interface ComponentPropDef {
  /** 属性 key（对应 modelValue 中的字段名） */
  key: string
  type:
    | 'string'
    | 'number'
    | 'boolean'
    | 'array'
    | 'object'
    | 'select'
    | 'color'
    | 'date'
    | 'code'
    | 'expression'
  /** 显示标签（缺省时回退到 key） */
  label?: string
  /** 描述说明（以 tooltip 形式展示） */
  description?: string
  default?: any
  required?: boolean
  /** type=select 时的可选项 */
  options?: Array<{ label: string; value: string | number | boolean }>
  /** type=object 时的子属性 schema（递归） */
  properties?: ComponentPropDef[]
  /** type=array 时的单项 schema（递归；缺省则按 string 渲染） */
  itemProp?: ComponentPropDef
  /** type=number 时的最小/最大/步长 */
  min?: number
  max?: number
  step?: number
  /** type=date 时的选择类型（date/datetime/daterange） */
  dateType?: 'date' | 'datetime' | 'daterange'
  /** type=code/expression 时的 textarea 行数 */
  rows?: number
  /** type=code/expression 时的表达式语言（缺省 aviator） */
  language?: 'groovy' | 'aviator' | 'javascript'
  /** type=string/input 的占位提示 */
  placeholder?: string
}

export interface ComponentMeta {
  name: string
  displayName: string
  category: string
  propsSchema: ComponentPropDef[]
}

export interface RegisteredComponent {
  component: any
  meta: ComponentMeta
}
