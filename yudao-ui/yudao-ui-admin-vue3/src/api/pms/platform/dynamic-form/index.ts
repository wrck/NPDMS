import request from '@/config/axios'

export type DynamicFormAction =
  | 'PATCH_TEMPLATE'
  | 'CREATE_REVISION'
  | 'PATCH_REVISION'
  | 'PUBLISH_REVISION'
  | 'ENABLE'
  | 'DISABLE'
  | 'CREATE_INSTANCE'
  | 'PATCH_INSTANCE'

export type JsonObject = Record<string, unknown>

export interface DynamicFormPage<T> {
  list: T[]
  total: number
}

export interface DynamicFormRevisionSummaryVO {
  revisionId: number
  revisionNo: number
  status: 'DRAFT' | 'PUBLISHED'
  revisionVersion: number
  sourceRevisionId?: number
  engineCode: string
  designerVersion: string
  rendererVersion: string
  publishedBy?: number
  publishedAt?: string
}

export interface DynamicFormTemplateVO {
  templateId: number
  templateCode: string
  templateName: string
  categoryCode: string
  description?: string
  availability: 'ENABLED' | 'DISABLED'
  templateVersion: number
  currentPublishedRevisionId?: number
  currentDraft?: DynamicFormRevisionSummaryVO
  currentPublished?: DynamicFormRevisionSummaryVO
  allowedActions: DynamicFormAction[]
  createTime?: string
  updateTime?: string
}

export interface DynamicFormRevisionVO extends DynamicFormRevisionSummaryVO {
  templateId: number
  formConfJson: JsonObject
  formRulesJson: JsonObject[]
  allowedActions: DynamicFormAction[]
}

export interface DynamicFormSelectionVO {
  templateId: number
  templateCode: string
  templateName: string
  categoryCode: string
  description?: string
  currentPublishedRevisionId: number
  currentPublishedRevisionNo: number
  engineCode: string
  designerVersion: string
  rendererVersion: string
  templateVersion: number
  allowedActions: DynamicFormAction[]
}

export interface FileFactVersion {
  artifactVersion: number
  referenceVersion: number
  availabilityVersion: number
}

export interface DynamicFormFileFactVO {
  artifactId: number
  versionNo: number
  referenceKey: string
  fileFactVersion: FileFactVersion
  scopeVersion: number
  status: string
}

export interface DynamicFormInstanceVO {
  instanceId: number
  instanceCode: string
  instanceName: string
  templateId: number
  templateCode: string
  templateName: string
  templateRevisionId: number
  templateRevisionNo: number
  engineCode: string
  designerVersion: string
  rendererVersion: string
  formConfJson: JsonObject
  formRulesJson: JsonObject[]
  values: JsonObject
  controlledFiles: Record<string, DynamicFormFileFactVO[]>
  instanceVersion: number
  createdBy: number
  allowedActions: DynamicFormAction[]
  createTime?: string
  updateTime?: string
}

export interface DynamicFormInstanceSummaryVO {
  instanceId: number
  instanceCode: string
  instanceName: string
  templateId: number
  templateCode: string
  templateName: string
  templateRevisionId: number
  templateRevisionNo: number
  instanceVersion: number
  createdBy: number
  allowedActions: DynamicFormAction[]
  createTime?: string
  updateTime?: string
}

export interface CreateTemplateReqVO {
  templateCode: string
  templateName: string
  categoryCode: string
  description?: string
}

export interface CreateTemplateRespVO {
  templateId: number
  templateVersion: number
  availability: 'DISABLED'
  draftRevisionId: number
  draftRevisionNo: 1
  draftVersion: number
  allowedActions: DynamicFormAction[]
}

export interface TemplateCommandRespVO {
  templateId: number
  templateVersion: number
  availability: 'ENABLED' | 'DISABLED'
  currentPublishedRevisionId?: number
  allowedActions: DynamicFormAction[]
}

export interface PublishRevisionRespVO {
  templateId: number
  templateVersion: number
  revisionId: number
  revisionNo: number
  status: 'PUBLISHED'
  publishedBy: number
  publishedAt: string
  availability: 'ENABLED' | 'DISABLED'
}

export interface CreateRevisionRespVO {
  revisionId: number
  revisionNo: number
  status: 'DRAFT'
  sourceRevisionId?: number
  revisionVersion: number
}

export interface PatchRevisionRespVO {
  revisionId: number
  revisionNo: number
  status: 'DRAFT'
  revisionVersion: number
}

export interface CreateInstanceRespVO {
  instanceId: number
  instanceCode: string
  templateId: number
  templateRevisionId: number
  templateRevisionNo: number
  instanceVersion: number
  allowedActions: DynamicFormAction[]
}

export interface PatchInstanceRespVO {
  instanceId: number
  instanceVersion: number
  changedFieldKeys: string[]
  allowedActions: DynamicFormAction[]
}

export interface PatchTemplateReqVO {
  templateName?: string
  categoryCode?: string
  description?: string | null
}

export interface PatchRevisionReqVO {
  formConfJson: JsonObject
  formRulesJson: JsonObject[]
  engineCode: 'FORM_CREATE_ELEMENT_PLUS'
  designerVersion: '3.4.0'
  rendererVersion: '3.2.38'
}

export interface CreateInstanceReqVO {
  templateRevisionId: number
  expectedTemplateVersion: number
  instanceName: string
}

export interface PatchInstanceReqVO {
  values: JsonObject
}

const baseUrl = '/api/v1/pms'

export const getTemplatePage = (params: { pageNo: number; pageSize: number }) =>
  request.get<DynamicFormPage<DynamicFormTemplateVO>>({
    url: `${baseUrl}/dynamic-form-templates`,
    params
  })

export const createTemplate = (data: CreateTemplateReqVO, idempotencyKey: string) =>
  request.post<CreateTemplateRespVO>({
    url: `${baseUrl}/dynamic-form-templates`,
    data,
    headers: { 'Idempotency-Key': idempotencyKey }
  })

export const getTemplate = (templateId: number) =>
  request.get<DynamicFormTemplateVO>({ url: `${baseUrl}/dynamic-form-templates/${templateId}` })

export const patchTemplate = (
  templateId: number,
  expectedVersion: number,
  data: PatchTemplateReqVO
) =>
  request.put<TemplateCommandRespVO>({
    method: 'PATCH',
    url: `${baseUrl}/dynamic-form-templates/${templateId}`,
    data,
    headers: { 'If-Match': String(expectedVersion) }
  })

export const createRevision = (
  templateId: number,
  expectedTemplateVersion: number,
  idempotencyKey: string
) =>
  request.post<CreateRevisionRespVO>({
    url: `${baseUrl}/dynamic-form-templates/${templateId}/revisions`,
    headers: {
      'If-Match': String(expectedTemplateVersion),
      'Idempotency-Key': idempotencyKey
    }
  })

export const getRevision = (revisionId: number) =>
  request.get<DynamicFormRevisionVO>({
    url: `${baseUrl}/dynamic-form-template-revisions/${revisionId}`
  })

export const patchRevision = (
  revisionId: number,
  expectedVersion: number,
  data: PatchRevisionReqVO
) =>
  request.put<PatchRevisionRespVO>({
    method: 'PATCH',
    url: `${baseUrl}/dynamic-form-template-revisions/${revisionId}`,
    data,
    headers: { 'If-Match': String(expectedVersion) }
  })

export const publishRevision = (
  revisionId: number,
  expectedVersion: number,
  idempotencyKey: string
) =>
  request.post<PublishRevisionRespVO>({
    url: `${baseUrl}/dynamic-form-template-revisions/${revisionId}/actions/publish`,
    headers: {
      'If-Match': String(expectedVersion),
      'Idempotency-Key': idempotencyKey
    }
  })

const setAvailability = (
  templateId: number,
  action: 'enable' | 'disable',
  expectedVersion: number,
  idempotencyKey: string
) =>
  request.post<TemplateCommandRespVO>({
    url: `${baseUrl}/dynamic-form-templates/${templateId}/actions/${action}`,
    headers: {
      'If-Match': String(expectedVersion),
      'Idempotency-Key': idempotencyKey
    }
  })

export const enableTemplate = (templateId: number, version: number, key: string) =>
  setAvailability(templateId, 'enable', version, key)
export const disableTemplate = (templateId: number, version: number, key: string) =>
  setAvailability(templateId, 'disable', version, key)

export const getTemplateSelection = (params: { pageNo: number; pageSize: number }) =>
  request.get<DynamicFormPage<DynamicFormSelectionVO>>({
    url: `${baseUrl}/dynamic-form-templates/selection`,
    params
  })

export const getInstancePage = (params: { pageNo: number; pageSize: number }) =>
  request.get<DynamicFormPage<DynamicFormInstanceSummaryVO>>({
    url: `${baseUrl}/dynamic-form-instances`,
    params
  })

export const createInstance = (data: CreateInstanceReqVO, idempotencyKey: string) =>
  request.post<CreateInstanceRespVO>({
    url: `${baseUrl}/dynamic-form-instances`,
    data,
    headers: { 'Idempotency-Key': idempotencyKey }
  })

export const getInstance = (instanceId: number) =>
  request.get<DynamicFormInstanceVO>({ url: `${baseUrl}/dynamic-form-instances/${instanceId}` })

export const patchInstance = (
  instanceId: number,
  expectedVersion: number,
  data: PatchInstanceReqVO
) =>
  request.put<PatchInstanceRespVO>({
    method: 'PATCH',
    url: `${baseUrl}/dynamic-form-instances/${instanceId}`,
    data,
    headers: { 'If-Match': String(expectedVersion) }
  })
