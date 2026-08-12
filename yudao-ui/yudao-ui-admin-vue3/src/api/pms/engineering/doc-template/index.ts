import request from '@/config/axios'

export interface DocTemplateVO {
  id?: number
  code: string
  name: string
  docCategory: string // REQUIREMENT | SOLUTION
  parentTemplateId?: number
  applicability: string // JSON字符串
  description?: string
  currentVersionId?: number
  status?: number
  version?: number
  createTime?: string
}

export interface DocTemplateVersionVO {
  id?: number
  templateId: number
  versionLabel: string
  sections: string // JSON字符串
  sectionOverrides?: string
  excludedSections?: string
  changeLog?: string
  published?: number
  createTime?: string
}

export interface DocTemplateSelectReqVO {
  docCategory: string
  projectType?: string
  networkType?: string
  productType?: string
  implementMode?: string
}

const baseUrl = '/pms/eng-doc-template'

// 模板 CRUD
export const getDocTemplatePage = (params: PageParam) =>
  request.get({ url: `${baseUrl}/page`, params })
export const getDocTemplate = (id: number) =>
  request.get({ url: `${baseUrl}/get`, params: { id } })
export const createDocTemplate = (data: DocTemplateVO) =>
  request.post({ url: `${baseUrl}/create`, data })
export const updateDocTemplate = (data: DocTemplateVO) =>
  request.put({ url: `${baseUrl}/update`, data })
export const deleteDocTemplate = (id: number) =>
  request.delete({ url: `${baseUrl}/delete`, params: { id } })

// 模板状态流转
export const publishDocTemplate = (id: number) =>
  request.put({ url: `${baseUrl}/publish`, params: { id } })
export const disableDocTemplate = (id: number) =>
  request.put({ url: `${baseUrl}/disable`, params: { id } })

// 已发布模板列表
export const getPublishedDocTemplateList = (docCategory?: string) =>
  request.get({ url: `${baseUrl}/published-list`, params: { docCategory } })

// 版本管理
export const createDocTemplateVersion = (data: DocTemplateVersionVO) =>
  request.post({ url: `${baseUrl}/version/create`, data })
export const getDocTemplateVersion = (id: number) =>
  request.get({ url: `${baseUrl}/version/get`, params: { id } })
export const getDocTemplateVersionList = (templateId: number) =>
  request.get({ url: `${baseUrl}/version/list`, params: { templateId } })
export const getPublishedDocTemplateVersion = (templateId: number) =>
  request.get({ url: `${baseUrl}/version/published`, params: { templateId } })
export const publishDocTemplateVersion = (id: number) =>
  request.put({ url: `${baseUrl}/version/publish`, params: { id } })

// 模板选择（按条件筛选）
export const selectDocTemplates = (params: DocTemplateSelectReqVO) =>
  request.get({ url: `${baseUrl}/select`, params })

// 构建模板快照
export const buildDocTemplateSnapshot = (versionId: number) =>
  request.get({ url: `${baseUrl}/snapshot`, params: { versionId } })
