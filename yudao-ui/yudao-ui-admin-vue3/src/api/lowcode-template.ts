import { get, post } from '@/utils/request'

/**
 * 低代码配置模板市场 API（批次5-T8 前端）。
 *
 * <p>对应后端 {@code LowCodeConfigTemplateController}：模板 CRUD + 上架/下架/归档
 * + 市场浏览/搜索 + 下载（参数化）+ 评分 + 版本查询。借鉴 Zoho 模板市场 /
 * Appsmith 模板 / Mendix App Store。</p>
 */

/** 模板状态：上架 / 草稿 / 归档 */
export type ConfigTemplateStatus = 'PUBLISHED' | 'DRAFT' | 'ARCHIVED'

/** 参数化定义中的单个参数项（parameters JSON 数组元素） */
export interface ConfigTemplateParameter {
  /** 参数键（下载时作为替换键） */
  key: string
  /** 参数标签（展示给用户） */
  label?: string
  /** 参数类型：string / number / boolean / select 等 */
  type?: string
  /** 是否必填 */
  required?: boolean
  /** select 类型时的候选值 */
  options?: string[]
  /** 默认值 */
  defaultValue?: string
}

/** 低代码配置模板（与后端 LowCodeConfigTemplate 实体对齐） */
export interface LowCodeConfigTemplate {
  id?: number
  /** 模板编码（唯一，如 "project-management-form"） */
  code: string
  /** 模板名称 */
  name: string
  /** 配置类型: FORM / LIST / ENTITY / MICROFLOW / CONNECTOR / RULE / TAB / RELATED_PAGE */
  configType: string
  /** 分类（如 "通用业务" "资产管理" "工作流"） */
  category?: string
  /** 完整配置 JSON 快照 */
  configJson?: string
  /** 缩略图 URL */
  thumbnail?: string
  /** 模板描述 */
  description?: string
  /** 作者 */
  author?: string
  /** 标签（逗号分隔） */
  tags?: string
  /** 状态: PUBLISHED / DRAFT / ARCHIVED */
  status?: ConfigTemplateStatus
  /** 下载量 */
  downloadCount?: number
  /** 评分 0-5 */
  rating?: number
  /** 评分数 */
  ratingCount?: number
  /** 模板版本（如 "1.0.0"） */
  version?: string
  /** 参数化定义 JSON 字符串 */
  parameters?: string
  createTime?: string
  updateTime?: string
}

/** 市场浏览（仅已发布，支持关键词/类型/分类过滤） */
export function marketplace(
  keyword?: string,
  configType?: string,
  category?: string
): Promise<LowCodeConfigTemplate[]> {
  return get<LowCodeConfigTemplate[]>('/api/lowcode/config-template/marketplace', {
    keyword,
    configType,
    category
  })
}

/** 查询所有模板（含 DRAFT/ARCHIVED，管理用） */
export function listAll(): Promise<LowCodeConfigTemplate[]> {
  return get<LowCodeConfigTemplate[]>('/api/lowcode/config-template')
}

/** 模板详情（按 ID） */
export function getById(id: number): Promise<LowCodeConfigTemplate> {
  return get<LowCodeConfigTemplate>(`/api/lowcode/config-template/${id}`)
}

/** 保存模板（新增/更新，按 code 去重） */
export function save(template: LowCodeConfigTemplate): Promise<LowCodeConfigTemplate> {
  return post<LowCodeConfigTemplate>('/api/lowcode/config-template', template)
}

/** 上架模板 */
export function publish(id: number): Promise<LowCodeConfigTemplate> {
  return post<LowCodeConfigTemplate>(`/api/lowcode/config-template/${id}/publish`)
}

/** 下架模板 */
export function unpublish(id: number): Promise<LowCodeConfigTemplate> {
  return post<LowCodeConfigTemplate>(`/api/lowcode/config-template/${id}/unpublish`)
}

/** 归档模板 */
export function archive(id: number): Promise<LowCodeConfigTemplate> {
  return post<LowCodeConfigTemplate>(`/api/lowcode/config-template/${id}/archive`)
}

/**
 * 下载模板（增加下载计数，应用参数化替换后返回配置）。
 *
 * @param id   模板 ID
 * @param params 参数化替换键值（对应 parameters 定义），为空时直接返回原始配置
 */
export function download(
  id: number,
  params?: Record<string, unknown>
): Promise<LowCodeConfigTemplate> {
  return post<LowCodeConfigTemplate>(`/api/lowcode/config-template/${id}/download`, params ?? {})
}

/** 评分（更新平均评分与评分数，rating 范围 0-5） */
export function rate(id: number, rating: number): Promise<LowCodeConfigTemplate> {
  return post<LowCodeConfigTemplate>(`/api/lowcode/config-template/${id}/rate`, { rating })
}

/** 查询某 code 的所有版本（按 version desc） */
export function listVersions(code: string): Promise<LowCodeConfigTemplate[]> {
  return get<LowCodeConfigTemplate[]>(`/api/lowcode/config-template/versions/${code}`)
}

// ===================== 工具方法 =====================

/** 安全解析 parameters JSON 字符串为参数数组（容错：空串/非法 JSON 返回空数组） */
export function parseParameters(parametersJson?: string): ConfigTemplateParameter[] {
  if (!parametersJson) return []
  try {
    const parsed = JSON.parse(parametersJson) as ConfigTemplateParameter[]
    if (!Array.isArray(parsed)) return []
    return parsed
  } catch {
    return []
  }
}

/** 安全解析 configJson 为格式化字符串（用于 `<pre>` 展示） */
export function formatConfigJson(configJson?: string): string {
  if (!configJson) return ''
  try {
    return JSON.stringify(JSON.parse(configJson), null, 2)
  } catch {
    return configJson
  }
}

/** 将 tags 字符串拆分为数组 */
export function splitTags(tags?: string): string[] {
  if (!tags) return []
  return tags
    .split(/[,，]/)
    .map((t) => t.trim())
    .filter(Boolean)
}
