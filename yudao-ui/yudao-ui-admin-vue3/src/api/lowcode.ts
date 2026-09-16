import axios from 'axios'
import { del, get, post, put, TOKEN_KEY } from '@/utils/request'
import { triggerBlobDownload } from '@/api/excel'

// ===================== 类型定义 =====================

/** 表单状态 */
export type FormStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'

/** 低代码表单配置（与后端 LowCodeForm 实体对应） */
export interface LowCodeFormConfig {
  id?: number
  code: string
  name: string
  description?: string
  /** 表单配置 JSON Schema 字符串（FormConfigSchema 定义的结构序列化后） */
  formConfig: string
  version?: number
  status?: FormStatus
  bizType?: string
  createTime?: string
  updateTime?: string
}

/** 表单分页查询参数（与后端 LowCodeConfigQuery + current/size 对应） */
export interface LowCodeFormQuery {
  page?: number
  size?: number
  code?: string
  name?: string
  status?: string
  bizType?: string
}

/** 分页查询结果（与后端 IPage 对应） */
export interface LowCodeFormPage {
  records: LowCodeFormConfig[]
  total: number
  current: number
  size: number
  pages?: number
}

// ===================== FormConfig Schema 前端类型 =====================

/** 字段类型常量（与后端 FormConfigSchema 保持一致） */
export const FieldType = {
  INPUT: 'input',
  TEXTAREA: 'textarea',
  NUMBER: 'number',
  PASSWORD: 'password',
  SELECT: 'select',
  RADIO: 'radio',
  CHECKBOX: 'checkbox',
  DATE: 'date',
  DATETIME: 'datetime',
  DATERANGE: 'daterange',
  SWITCH: 'switch',
  RATE: 'rate',
  SLIDER: 'slider',
  CASCADER: 'cascader',
  UPLOAD: 'upload',
  DIVIDER: 'divider',
  TITLE: 'title',
  CUSTOM: 'custom'
} as const

/** 布局类型常量 */
export const LayoutType = {
  GRID: 'grid',
  TABS: 'tabs',
  COLLAPSE: 'collapse'
} as const

/** 下拉/单选/多选的选项项 */
export interface FieldOption {
  label: string
  value: string | number | boolean
  disabled?: boolean
}

/**
 * 响应式栅格断点配置（借鉴 Element Plus el-col 断点）。
 *
 * <p>每个断点取值 1-24，对应 xs/sm/md/lg/xl 五档屏幕宽度。
 * 与数字形式的 span 保持兼容：当 span 为数字时按 :span= 渲染，
 * 为对象时按 :xs= :sm= :md= :lg= :xl= 渲染。</p>
 */
export interface ResponsiveSpan {
  xs?: number
  sm?: number
  md?: number
  lg?: number
  xl?: number
}

/** 表单字段定义（FormConfigSchema.fields[]） */
export interface FormFieldConfig {
  /** 字段唯一标识（field_1, field_2...） */
  id: string
  /** 字段类型 */
  type: string
  /** 显示标签 */
  label: string
  /** 数据字段名（绑定到 modelValue 的 key） */
  prop: string
  placeholder?: string
  defaultValue?: unknown
  required?: boolean
  disabled?: boolean
  readonly?: boolean
  hidden?: boolean
  clearable?: boolean
  /** 栅格宽度 1-24（数字）或响应式断点对象（xs/sm/md/lg/xl） */
  span?: number | ResponsiveSpan
  /** 自定义校验规则（el-form rules 格式） */
  rules?: Array<Record<string, unknown>>
  /** 类型特定属性 */
  props?: Record<string, unknown>
  /** 事件回调名（前端约定） */
  events?: Record<string, string>
  /** type=custom 时关联的注册中心组件名（LowCodeComponentRegistry meta.name） */
  componentName?: string
}

/** 布局配置 */
export interface FormLayoutConfig {
  type?: string
  gutter?: number
  tabs?: Array<{ title: string; fields: string[]; name?: string }>
  collapse?: Array<{ title: string; fields: string[]; name?: string }>
}

/** 表单配置（FormConfigSchema 顶层结构，解析后的 JSON 对象） */
export interface FormConfig {
  title?: string
  description?: string
  labelWidth?: number | string
  labelPosition?: 'left' | 'right' | 'top'
  size?: 'large' | 'default' | 'small'
  fields: FormFieldConfig[]
  layout?: FormLayoutConfig
}

// ===================== API 方法 =====================

/** 分页查询表单配置（后端参数为 current/size） */
export function listForms(query: LowCodeFormQuery): Promise<LowCodeFormPage> {
  const { page, size, ...rest } = query
  return get<LowCodeFormPage>('/api/lowcode/form', {
    current: page ?? 1,
    size: size ?? 10,
    ...rest
  })
}

/** 根据 ID 查询表单配置 */
export function getForm(id: number): Promise<LowCodeFormConfig> {
  return get<LowCodeFormConfig>(`/api/lowcode/form/${id}`)
}

/** 根据编码查询已发布（PUBLISHED）的表单配置 */
export function getFormByCode(code: string): Promise<LowCodeFormConfig> {
  return get<LowCodeFormConfig>(`/api/lowcode/form/code/${code}`)
}

/** 创建表单配置 */
export function createForm(data: LowCodeFormConfig): Promise<LowCodeFormConfig> {
  return post<LowCodeFormConfig>('/api/lowcode/form', data)
}

/** 更新表单配置 */
export function updateForm(id: number, data: LowCodeFormConfig): Promise<LowCodeFormConfig> {
  return put<LowCodeFormConfig>(`/api/lowcode/form/${id}`, data)
}

/** 删除表单配置 */
export function deleteForm(id: number): Promise<void> {
  return del<void>(`/api/lowcode/form/${id}`)
}

/** 发布表单配置：DRAFT → PUBLISHED */
export function publishForm(id: number): Promise<void> {
  return post<void>(`/api/lowcode/form/${id}/publish`)
}

/** 归档表单配置：PUBLISHED → ARCHIVED */
export function archiveForm(id: number): Promise<void> {
  return post<void>(`/api/lowcode/form/${id}/archive`)
}

/**
 * 导出指定编码的表单配置为 JSON 文件并触发浏览器下载。
 *
 * <p>此接口返回二进制流（非统一 envelope），故绕过统一的 axios 拦截器，
 * 直接使用原始 axios 注入 token 并以 blob 形式接收，然后触发下载。</p>
 *
 * @param code     表单编码
 * @param fileName 下载文件名（可选，默认 form-{code}.json）
 */
export async function exportForm(code: string, fileName?: string): Promise<void> {
  const token = localStorage.getItem(TOKEN_KEY) || ''
  const response = await axios.get(`/api/lowcode/form/${code}/export`, {
    responseType: 'blob',
    headers: { Authorization: `Bearer ${token}` }
  })
  triggerBlobDownload(response.data, fileName ?? `form-${code}.json`)
}

/**
 * 从 JSON 字符串导入表单配置。
 * 若 code 冲突，后端会自动追加数字后缀。
 *
 * <p>后端 {@code @RequestBody String json} 直接接收原始 JSON 字符串，
 * 因此将 string 转为 unknown 再到 object 以满足 axios 的类型签名，
 * 实际请求体仍是原始字符串。</p>
 */
export function importForm(json: string): Promise<LowCodeFormConfig> {
  return post<LowCodeFormConfig>(
    '/api/lowcode/form/import',
    json as unknown as object,
    {
      headers: { 'Content-Type': 'application/json' }
    }
  )
}

// ===================== LowCodeList 列表配置 =====================

/** 低代码列表配置（与后端 LowCodeList 实体对应） */
export interface LowCodeListConfig {
  id?: number
  code: string
  name: string
  description?: string
  /** 列表配置 JSON Schema 字符串（ListConfigSchema 定义的结构序列化后） */
  listConfig: string
  version?: number
  status?: FormStatus
  bizType?: string
  createTime?: string
  updateTime?: string
}

/** 列表分页查询参数（与后端 LowCodeConfigQuery + current/size 对应） */
export interface LowCodeListQuery {
  page?: number
  size?: number
  code?: string
  name?: string
  status?: string
  bizType?: string
}

/** 分页查询结果（与后端 IPage 对应） */
export interface LowCodeListPage {
  records: LowCodeListConfig[]
  total: number
  current: number
  size: number
  pages?: number
}

// ===================== ListConfig Schema 前端类型 =====================

/** 列类型常量（与后端 ListConfigSchema 保持一致） */
export const ColumnType = {
  TEXT: 'text',
  IMAGE: 'image',
  TAG: 'tag',
  DATE: 'date',
  DATETIME: 'datetime',
  CURRENCY: 'currency',
  PERCENT: 'percent',
  LINK: 'link',
  DICT: 'dict',
  CUSTOM: 'custom'
} as const

/** 筛选类型常量 */
export const FilterType = {
  INPUT: 'input',
  SELECT: 'select',
  DATE: 'date',
  DATERANGE: 'daterange',
  CASCADER: 'cascader'
} as const

/** 动作类型常量 */
export const ActionType = {
  CREATE: 'create',
  EDIT: 'edit',
  VIEW: 'view',
  DELETE: 'delete',
  CUSTOM: 'custom'
} as const

/** 按钮类型常量（与 Element Plus 一致） */
export const ButtonType = {
  PRIMARY: 'primary',
  SUCCESS: 'success',
  WARNING: 'warning',
  DANGER: 'danger',
  INFO: 'info',
  TEXT: 'text'
} as const

/** 列表布局常量 */
export const ListLayout = {
  TABLE: 'table',
  CARD: 'card'
} as const

/** 列定义（ListConfigSchema.columns[]） */
export interface ListColumnConfig {
  /** 列唯一标识（col_1, col_2...） */
  id: string
  /** 数据字段名 */
  prop: string
  /** 列标题 */
  label: string
  /** 列宽（px） */
  width?: number
  /** 最小列宽 */
  minWidth?: number
  /** 固定列：left / right / false */
  fixed?: string | boolean
  /** 是否可排序 */
  sortable?: boolean
  /** 对齐方式：left / center / right */
  align?: 'left' | 'center' | 'right'
  /** 列类型 */
  type?: string
  /** 格式化器（按 ":" 分隔，如 dateFormat:YYYY-MM-DD） */
  formatter?: string
  /** 字典编码（type=dict 时使用） */
  dictCode?: string
  /** 图片宽度（type=image 时使用） */
  imageWidth?: number
  /** 图片高度（type=image 时使用） */
  imageHeight?: number
  /** 链接跳转地址（type=link 时使用，{prop} 占位） */
  linkUrl?: string
  /** el-tag 类型（type=tag 时使用） */
  tagType?: string
  /** 是否隐藏列 */
  hidden?: boolean
  /** 是否可编辑（预留） */
  editable?: boolean
  /** type=custom 时关联的注册中心业务组件名（LowCodeComponentRegistry meta.name） */
  componentName?: string
}

/** 筛选项定义 */
export interface ListFilterConfig {
  /** 筛选项唯一标识 */
  id: string
  /** 筛选字段名（绑定到查询参数 key） */
  prop: string
  /** 标签 */
  label: string
  /** 筛选类型 */
  type: string
  /** 占位提示 */
  placeholder?: string
  /** 选项列表（type=select 时使用） */
  options?: Array<{ label: string; value: string | number }>
  /** 字典编码（type=select 且未提供 options 时使用） */
  dictCode?: string
  /** 默认值 */
  defaultValue?: unknown
  /** 栅格宽度 1-24（数字）或响应式断点对象（xs/sm/md/lg/xl） */
  span?: number | ResponsiveSpan
  /** 是否可清空 */
  clearable?: boolean
  /** 是否多选（type=select 时使用） */
  multiple?: boolean
}

/** 操作按钮定义（行操作 / 工具栏通用） */
export interface ListOperationConfig {
  /** 操作唯一标识 */
  id: string
  /** 按钮文本 */
  label: string
  /** 按钮类型（primary/success/warning/danger/info/text） */
  type?: string
  /** Element Plus 图标名 */
  icon?: string
  /** 动作类型（create/edit/view/delete/custom） */
  action: string
  /** 跳转地址（action=edit/view/create 时使用，{prop} 占位） */
  url?: string
  /** 调用接口（action=delete/custom 时使用，{prop} 占位） */
  api?: string
  /** 接口方法（action=delete/custom 时使用） */
  method?: string
  /** 二次确认提示（非空时弹出 confirm 对话框） */
  confirm?: string
  /** 权限标识 */
  permission?: string
  /** 显示条件表达式（对 row 求值，假值则隐藏） */
  visible?: string
}

/** 导出配置 */
export interface ListExportConfig {
  enabled: boolean
  api?: string
  fileName?: string
  withFilter?: boolean
}

/** 列表配置（ListConfigSchema 顶层结构，解析后的 JSON 对象） */
export interface ListConfig {
  title?: string
  description?: string
  /** 绑定的动态实体编码；未显式配置 searchApi 时自动使用动态实体分页接口 */
  entityCode?: string
  /** 绑定的表单编码；省略时按 form_{entityCode} 约定解析 */
  formCode?: string
  /** 列表数据查询 API */
  searchApi?: string
  /** 请求方法 GET / POST */
  method?: string
  /** 默认每页条数 */
  pageSize?: number
  /** 每页条数可选项 */
  pageSizes?: number[]
  /** 列表布局：table / card */
  layout?: string
  stripe?: boolean
  border?: boolean
  /** 是否显示多选列 */
  showSelection?: boolean
  /** 是否显示序号列 */
  showIndex?: boolean
  /** 是否显示分页 */
  showPagination?: boolean
  /** 列定义列表 */
  columns: ListColumnConfig[]
  /** 筛选项定义列表 */
  filters?: ListFilterConfig[]
  /** 行操作按钮列表 */
  operations?: ListOperationConfig[]
  /** 工具栏按钮列表 */
  toolbar?: ListOperationConfig[]
  /** 导出配置 */
  export?: ListExportConfig
}

// ===================== 列表 API 方法 =====================

/** 分页查询列表配置（后端参数为 current/size） */
export function listLists(query: LowCodeListQuery): Promise<LowCodeListPage> {
  const { page, size, ...rest } = query
  return get<LowCodeListPage>('/api/lowcode/list', {
    current: page ?? 1,
    size: size ?? 10,
    ...rest
  })
}

/** 根据 ID 查询列表配置 */
export function getList(id: number): Promise<LowCodeListConfig> {
  return get<LowCodeListConfig>(`/api/lowcode/list/${id}`)
}

/** 根据编码查询已发布（PUBLISHED）的列表配置 */
export function getListByCode(code: string): Promise<LowCodeListConfig> {
  return get<LowCodeListConfig>(`/api/lowcode/list/code/${code}`)
}

/** 创建列表配置 */
export function createList(data: LowCodeListConfig): Promise<LowCodeListConfig> {
  return post<LowCodeListConfig>('/api/lowcode/list', data)
}

/** 更新列表配置 */
export function updateList(id: number, data: LowCodeListConfig): Promise<LowCodeListConfig> {
  return put<LowCodeListConfig>(`/api/lowcode/list/${id}`, data)
}

/** 删除列表配置 */
export function deleteList(id: number): Promise<void> {
  return del<void>(`/api/lowcode/list/${id}`)
}

/** 发布列表配置：DRAFT → PUBLISHED */
export function publishList(id: number): Promise<void> {
  return post<void>(`/api/lowcode/list/${id}/publish`)
}

/** 归档列表配置：PUBLISHED → ARCHIVED */
export function archiveList(id: number): Promise<void> {
  return post<void>(`/api/lowcode/list/${id}/archive`)
}

/**
 * 导出指定编码的列表配置为 JSON 文件并触发浏览器下载。
 *
 * <p>此接口返回二进制流（非统一 envelope），故绕过统一的 axios 拦截器，
 * 直接使用原始 axios 注入 token 并以 blob 形式接收，然后触发下载。</p>
 *
 * @param code     列表编码
 * @param fileName 下载文件名（可选，默认 list-{code}.json）
 */
export async function exportList(code: string, fileName?: string): Promise<void> {
  const token = localStorage.getItem(TOKEN_KEY) || ''
  const response = await axios.get(`/api/lowcode/list/${code}/export`, {
    responseType: 'blob',
    headers: { Authorization: `Bearer ${token}` }
  })
  triggerBlobDownload(response.data, fileName ?? `list-${code}.json`)
}

/**
 * 从 JSON 字符串导入列表配置。
 * 若 code 冲突，后端会自动追加数字后缀。
 *
 * <p>后端 {@code @RequestBody String json} 直接接收原始 JSON 字符串，
 * 因此将 string 转为 unknown 再到 object 以满足 axios 的类型签名，
 * 实际请求体仍是原始字符串。</p>
 */
export function importList(json: string): Promise<LowCodeListConfig> {
  return post<LowCodeListConfig>(
    '/api/lowcode/list/import',
    json as unknown as object,
    {
      headers: { 'Content-Type': 'application/json' }
    }
  )
}

// ===================== LowCodeTab 标签页配置 =====================

/** 低代码标签页配置（与后端 LowCodeTab 实体对应） */
export interface LowCodeTabConfig {
  id?: number
  code: string
  name: string
  description?: string
  /** 标签页配置 JSON Schema 字符串（TabConfigSchema 定义的结构序列化后） */
  tabConfig: string
  version?: number
  status?: FormStatus
  bizType?: string
  createTime?: string
  updateTime?: string
}

/** 标签页分页查询参数（与后端 LowCodeConfigQuery + current/size 对应） */
export interface LowCodeTabQuery {
  page?: number
  size?: number
  code?: string
  name?: string
  status?: string
  bizType?: string
}

/** 分页查询结果（与后端 IPage 对应） */
export interface LowCodeTabPage {
  records: LowCodeTabConfig[]
  total: number
  current: number
  size: number
  pages?: number
}

// ===================== TabConfig Schema 前端类型 =====================

/** 页面类型常量（与后端 TabConfigSchema 保持一致） */
export const TabPageType = {
  FORM: 'form',
  LIST: 'list',
  RELATED_PAGE: 'related-page',
  CUSTOM: 'custom'
} as const

/** el-tabs type 常量 */
export const TabsType = {
  CARD: 'card',
  BORDER_CARD: 'border-card',
  PLAIN: 'plain'
} as const

/** 标签位置常量 */
export const TabPosition = {
  TOP: 'top',
  RIGHT: 'right',
  BOTTOM: 'bottom',
  LEFT: 'left'
} as const

/** 标签项定义（TabConfigSchema.tabs[]） */
export interface TabItemConfig {
  /** 标签项唯一标识（tab_1, tab_2...） */
  id: string
  /** 标签显示文本 */
  title: string
  /** 标签标识（用于 v-model 绑定，必填且唯一） */
  name: string
  /** 是否懒加载（首次激活才渲染内容） */
  lazy?: boolean
  /** 是否禁用此标签 */
  disabled?: boolean
  /** Element Plus 图标名（可选） */
  icon?: string
  /** 引用的低代码页面编码（form/list/related-page 类型时必填） */
  pageCode?: string
  /** 引用页面类型（form/list/related-page/custom） */
  pageType: string
  /** 自定义页面 URL（pageType=custom 时使用，{prop} 占位） */
  pageUrl?: string
  /** 传递给子页面的参数（值支持模板变量 ${route.params.id} 等） */
  props?: Record<string, unknown>
  /** 显示条件表达式（对 contextData 求值，假值则隐藏） */
  visible?: string
}

/** 标签页配置（TabConfigSchema 顶层结构，解析后的 JSON 对象） */
export interface TabConfig {
  title?: string
  description?: string
  /** el-tabs type：card / border-card / plain */
  type?: string
  /** 标签位置：top / right / bottom / left */
  tabPosition?: string
  /** 标签是否可关闭 */
  closable?: boolean
  /** 是否可新增标签 */
  addable?: boolean
  /** 是否可编辑标签名 */
  editable?: boolean
  /** 标签项定义列表 */
  tabs: TabItemConfig[]
}

/**
 * Normalize historical tab JSON into the current designer/runtime contract.
 * Early demo data used `type` (upper-case) and omitted `id`/`name`.
 */
export function normalizeTabConfig(config: TabConfig): TabConfig {
  const tabs = Array.isArray(config.tabs) ? config.tabs : []
  return {
    ...config,
    tabs: tabs.map((item, index) => {
      const legacy = item as TabItemConfig & { type?: string }
      const rawPageType = legacy.pageType || legacy.type || TabPageType.CUSTOM
      const pageType = String(rawPageType).toLowerCase().replaceAll('_', '-')
      const id = legacy.id || `tab_${index + 1}`
      return {
        ...legacy,
        id,
        name: legacy.name || id,
        title: legacy.title || `标签 ${index + 1}`,
        pageType,
        lazy: legacy.lazy ?? true,
        props: legacy.props || {}
      }
    })
  }
}

// ===================== 标签页 API 方法 =====================

/** 分页查询标签页配置（后端参数为 current/size） */
export function listTabs(query: LowCodeTabQuery): Promise<LowCodeTabPage> {
  const { page, size, ...rest } = query
  return get<LowCodeTabPage>('/api/lowcode/tab', {
    current: page ?? 1,
    size: size ?? 10,
    ...rest
  })
}

/** 根据 ID 查询标签页配置 */
export function getTab(id: number): Promise<LowCodeTabConfig> {
  return get<LowCodeTabConfig>(`/api/lowcode/tab/${id}`)
}

/** 根据编码查询已发布（PUBLISHED）的标签页配置 */
export function getTabByCode(code: string): Promise<LowCodeTabConfig> {
  return get<LowCodeTabConfig>(`/api/lowcode/tab/code/${code}`)
}

/** 创建标签页配置 */
export function createTab(data: LowCodeTabConfig): Promise<LowCodeTabConfig> {
  return post<LowCodeTabConfig>('/api/lowcode/tab', data)
}

/** 更新标签页配置 */
export function updateTab(id: number, data: LowCodeTabConfig): Promise<LowCodeTabConfig> {
  return put<LowCodeTabConfig>(`/api/lowcode/tab/${id}`, data)
}

/** 删除标签页配置 */
export function deleteTab(id: number): Promise<void> {
  return del<void>(`/api/lowcode/tab/${id}`)
}

/** 发布标签页配置：DRAFT → PUBLISHED */
export function publishTab(id: number): Promise<void> {
  return post<void>(`/api/lowcode/tab/${id}/publish`)
}

/** 归档标签页配置：PUBLISHED → ARCHIVED */
export function archiveTab(id: number): Promise<void> {
  return post<void>(`/api/lowcode/tab/${id}/archive`)
}

/**
 * 导出指定编码的标签页配置为 JSON 文件并触发浏览器下载。
 *
 * <p>此接口返回二进制流（非统一 envelope），故绕过统一的 axios 拦截器，
 * 直接使用原始 axios 注入 token 并以 blob 形式接收，然后触发下载。</p>
 *
 * @param code     标签页编码
 * @param fileName 下载文件名（可选，默认 tab-{code}.json）
 */
export async function exportTab(code: string, fileName?: string): Promise<void> {
  const token = localStorage.getItem(TOKEN_KEY) || ''
  const response = await axios.get(`/api/lowcode/tab/${code}/export`, {
    responseType: 'blob',
    headers: { Authorization: `Bearer ${token}` }
  })
  triggerBlobDownload(response.data, fileName ?? `tab-${code}.json`)
}

/**
 * 从 JSON 字符串导入标签页配置。
 * 若 code 冲突，后端会自动追加数字后缀。
 */
export function importTab(json: string): Promise<LowCodeTabConfig> {
  return post<LowCodeTabConfig>(
    '/api/lowcode/tab/import',
    json as unknown as object,
    {
      headers: { 'Content-Type': 'application/json' }
    }
  )
}

// ===================== LowCodeRelatedPage 关联页配置 =====================

/** 低代码关联页配置（与后端 LowCodeRelatedPage 实体对应） */
export interface LowCodeRelatedPageConfig {
  id?: number
  code: string
  name: string
  description?: string
  /** 关联页配置 JSON Schema 字符串（RelatedPageConfigSchema 定义的结构序列化后） */
  relatedConfig: string
  version?: number
  status?: FormStatus
  bizType?: string
  createTime?: string
  updateTime?: string
}

/** 关联页分页查询参数（与后端 LowCodeConfigQuery + current/size 对应） */
export interface LowCodeRelatedPageQuery {
  page?: number
  size?: number
  code?: string
  name?: string
  status?: string
  bizType?: string
}

/** 分页查询结果（与后端 IPage 对应） */
export interface LowCodeRelatedPagePage {
  records: LowCodeRelatedPageConfig[]
  total: number
  current: number
  size: number
  pages?: number
}

// ===================== RelatedPageConfig Schema 前端类型 =====================

/** 区块类型常量（与后端 RelatedPageConfigSchema 保持一致） */
export const SectionType = {
  FORM: 'form',
  LIST: 'list',
  TAB: 'tab',
  CUSTOM: 'custom'
} as const

/** 关联页布局类型常量 */
export const RelatedPageLayout = {
  GRID: 'grid',
  TABS: 'tabs',
  COLLAPSE: 'collapse'
} as const

/** 区块定义（RelatedPageConfigSchema.sections[]） */
export interface RelatedPageSectionConfig {
  /** 区块唯一标识（section_1, section_2...） */
  id: string
  /** 区块标题 */
  title: string
  /** 区块类型（form/list/tab/custom） */
  type: string
  /** 引用的低代码页面编码（form/list/tab 类型时必填） */
  pageCode?: string
  /** 自定义页面 URL（type=custom 时使用，{prop} 占位） */
  pageUrl?: string
  /** 栅格宽度 1-24（数字，grid 模式生效默认 24）或响应式断点对象（xs/sm/md/lg/xl） */
  span?: number | ResponsiveSpan
  /** 排序号（升序，相同 order 按数组顺序，默认 100） */
  order?: number
  /** 显示条件表达式（对 contextData 求值，假值则隐藏） */
  visible?: string
  /** 传递给子页面的参数（值支持模板变量 ${route.params.id} 等） */
  props?: Record<string, unknown>
}

/** 关联页配置（RelatedPageConfigSchema 顶层结构，解析后的 JSON 对象） */
export interface RelatedPageConfig {
  title?: string
  description?: string
  /** 主实体类型（如 project / asset / settlement） */
  mainEntity?: string
  /** 区块定义列表 */
  sections: RelatedPageSectionConfig[]
  /** 布局方式：grid / tabs / collapse */
  layout?: string
  /** 栅格间距（grid 模式生效，默认 16） */
  gutter?: number
}

/**
 * Normalize the legacy relation-view contract into section-based composition.
 * Legacy demo rows used sourceEntity/targetEntity/targetPageCode without
 * `sections`, which otherwise produced an empty designer and preview.
 */
export function normalizeRelatedPageConfig(config: RelatedPageConfig): RelatedPageConfig {
  const legacy = config as RelatedPageConfig & {
    sourceEntity?: string
    targetEntity?: string
    targetPageCode?: string
  }
  let sections = Array.isArray(config.sections) ? config.sections : []
  if (sections.length === 0 && legacy.targetPageCode) {
    const code = legacy.targetPageCode
    const type =
      code.startsWith('form_') ? SectionType.FORM
        : code.startsWith('list_') ? SectionType.LIST
          : code.startsWith('tab_') ? SectionType.TAB
            : SectionType.CUSTOM
    sections = [{
      id: 'section_1',
      title: legacy.targetEntity || '关联信息',
      type,
      pageCode: code,
      span: 24,
      order: 100,
      props: {}
    }]
  }
  return {
    ...config,
    mainEntity: config.mainEntity || legacy.sourceEntity || '',
    layout: config.layout || RelatedPageLayout.GRID,
    gutter: config.gutter ?? 16,
    sections: sections.map((section, index) => ({
      ...section,
      id: section.id || `section_${index + 1}`,
      title: section.title || `区块 ${index + 1}`,
      type: String(section.type || SectionType.CUSTOM).toLowerCase().replaceAll('_', '-'),
      span: section.span ?? 24,
      order: section.order ?? (index + 1) * 100,
      props: section.props || {}
    }))
  }
}

// ===================== 关联页 API 方法 =====================

/** 分页查询关联页配置（后端参数为 current/size） */
export function listRelatedPages(query: LowCodeRelatedPageQuery): Promise<LowCodeRelatedPagePage> {
  const { page, size, ...rest } = query
  return get<LowCodeRelatedPagePage>('/api/lowcode/related-page', {
    current: page ?? 1,
    size: size ?? 10,
    ...rest
  })
}

/** 根据 ID 查询关联页配置 */
export function getRelatedPage(id: number): Promise<LowCodeRelatedPageConfig> {
  return get<LowCodeRelatedPageConfig>(`/api/lowcode/related-page/${id}`)
}

/** 根据编码查询已发布（PUBLISHED）的关联页配置 */
export function getRelatedPageByCode(code: string): Promise<LowCodeRelatedPageConfig> {
  return get<LowCodeRelatedPageConfig>(`/api/lowcode/related-page/code/${code}`)
}

/** 创建关联页配置 */
export function createRelatedPage(data: LowCodeRelatedPageConfig): Promise<LowCodeRelatedPageConfig> {
  return post<LowCodeRelatedPageConfig>('/api/lowcode/related-page', data)
}

/** 更新关联页配置 */
export function updateRelatedPage(id: number, data: LowCodeRelatedPageConfig): Promise<LowCodeRelatedPageConfig> {
  return put<LowCodeRelatedPageConfig>(`/api/lowcode/related-page/${id}`, data)
}

/** 删除关联页配置 */
export function deleteRelatedPage(id: number): Promise<void> {
  return del<void>(`/api/lowcode/related-page/${id}`)
}

/** 发布关联页配置：DRAFT → PUBLISHED */
export function publishRelatedPage(id: number): Promise<void> {
  return post<void>(`/api/lowcode/related-page/${id}/publish`)
}

/** 归档关联页配置：PUBLISHED → ARCHIVED */
export function archiveRelatedPage(id: number): Promise<void> {
  return post<void>(`/api/lowcode/related-page/${id}/archive`)
}

/**
 * 导出指定编码的关联页配置为 JSON 文件并触发浏览器下载。
 *
 * <p>此接口返回二进制流（非统一 envelope），故绕过统一的 axios 拦截器，
 * 直接使用原始 axios 注入 token 并以 blob 形式接收，然后触发下载。</p>
 *
 * @param code     关联页编码
 * @param fileName 下载文件名（可选，默认 related-page-{code}.json）
 */
export async function exportRelatedPage(code: string, fileName?: string): Promise<void> {
  const token = localStorage.getItem(TOKEN_KEY) || ''
  const response = await axios.get(`/api/lowcode/related-page/${code}/export`, {
    responseType: 'blob',
    headers: { Authorization: `Bearer ${token}` }
  })
  triggerBlobDownload(response.data, fileName ?? `related-page-${code}.json`)
}

/**
 * 从 JSON 字符串导入关联页配置。
 * 若 code 冲突，后端会自动追加数字后缀。
 */
export function importRelatedPage(json: string): Promise<LowCodeRelatedPageConfig> {
  return post<LowCodeRelatedPageConfig>(
    '/api/lowcode/related-page/import',
    json as unknown as object,
    {
      headers: { 'Content-Type': 'application/json' }
    }
  )
}

// ===================== 低代码页面权限校验 =====================

/** 支持的低代码页面类型 */
export type LowCodePageType = 'form' | 'list' | 'tab' | 'related-page'

/** 低代码页面视图对象（与后端 LowCodePageVO 对应） */
export interface LowCodePageVO {
  menuId?: number
  menuName?: string
  pageType: LowCodePageType
  pageCode: string
  permission?: string
  path?: string
  icon?: string
  sortOrder?: number
}

/** 创建低代码菜单请求（与后端 CreateLowCodeMenuRequest 对应） */
export interface CreateLowCodeMenuRequest {
  menuName: string
  pageType: LowCodePageType
  pageCode: string
  /** 自定义权限标识，留空则后端按 lowcode:page:{pageType}:{pageCode} 生成 */
  permission?: string
  parentId?: number
  icon?: string
  sortOrder?: number
}

/**
 * 校验当前用户是否有权访问指定低代码页面。
 *
 * <p>渲染入口在 onMounted 中调用此接口，无权限时显示提示并阻止渲染。</p>
 *
 * @param pageType 页面类型 form/list/tab/related-page
 * @param pageCode 低代码配置编码
 */
export function checkLowCodePermission(
  pageType: LowCodePageType,
  pageCode: string
): Promise<boolean> {
  return get<boolean>('/api/lowcode/permission/check', { pageType, pageCode })
}

/**
 * 获取当前用户可访问的低代码页面列表。
 */
export function getAccessibleLowCodePages(): Promise<LowCodePageVO[]> {
  return get<LowCodePageVO[]>('/api/lowcode/permission/pages')
}

/**
 * 为低代码页面创建菜单（需 lowcode:menu:create 权限）。
 */
export function createLowCodeMenu(
  data: CreateLowCodeMenuRequest
): Promise<number> {
  return post<number>('/api/lowcode/permission/menu', data)
}

// ===================== 动态实体数据 高级查询 =====================

/** 查询操作符（与后端 DynamicQueryRequest.QueryCondition.operator 对应） */
export type QueryOperator =
  | 'EQ'
  | 'NE'
  | 'LIKE'
  | 'IN'
  | 'BETWEEN'
  | 'GT'
  | 'GE'
  | 'LT'
  | 'LE'
  | 'IS_NULL'
  | 'IS_NOT_NULL'

/** 单个查询条件（与后端 QueryCondition 对应） */
export interface QueryCondition {
  /** 字段名（必须为实体合法字段） */
  field: string
  /** 操作符 */
  operator: QueryOperator
  /** 比较值；IN 时为数组 */
  value?: unknown
  /** BETWEEN 上界 */
  value2?: unknown
  /** OR 分组名：相同分组用 OR 连接，留空则归入 default 组用 AND 连接 */
  orGroup?: string
}

/** 排序项（与后端 OrderBy 对应） */
export interface QueryOrderBy {
  field: string
  direction: 'ASC' | 'DESC'
}

/** 高级查询请求（与后端 DynamicQueryRequest 对应） */
export interface DynamicQueryRequest {
  conditions: QueryCondition[]
  orderBy: QueryOrderBy[]
  page?: number
  size?: number
}

/** 高级查询分页结果（与后端 MyBatis-Plus Page 对应） */
export interface DynamicQueryPage {
  records: Array<Record<string, unknown>>
  total: number
  current: number
  size: number
  pages?: number
}

/**
 * 高级查询动态实体数据：支持 LIKE/IN/BETWEEN/比较/IS NULL、排序、OR 分组与分页。
 *
 * @param entityCode 实体编码
 * @param request    查询请求
 */
export function queryEntityData(
  entityCode: string,
  request: DynamicQueryRequest
): Promise<DynamicQueryPage> {
  return post<DynamicQueryPage>(`/api/lowcode/data/${entityCode}/query`, request)
}
