import request from '@/config/axios'

export interface ProjectSplitScopeInput {
  orderLineId: number
  quantity: number
  officeDepartmentCode?: string
  serialNumbers?: string[]
}

export interface ProjectSplitItemInput {
  clientItemKey: string
  projectName: string
  businessLevelCode?: string
  treeSort?: number
  officeDepartmentCode?: string
  scopes: ProjectSplitScopeInput[]
}

export interface ProjectSplitDraftInput {
  expectedDraftVersion?: number
  parentProjectId: number
  templateRevisionId?: number
  items: ProjectSplitItemInput[]
}

export interface ProjectSplitDraftVO {
  id: number
  parentProjectId: number
  status: string
  draftVersion: number
  parentVersion: number
  scopeVersion: number
  treeVersion: number
  templateRevisionId?: number
  previewHash?: string
  validationStatus?: string
  validatedAt?: string
  items: {
    id: number
    clientItemKey: string
    projectName: string
    businessLevelCode?: string
    treeSort?: number
    officeDepartmentCode?: string
    itemStatus?: string
    scopes: {
      id: number
      orderLineId: number
      allocatedQty: number
      officeDepartmentCode?: string
      serialNo?: string
      sourceScopeVersion: number
    }[]
  }[]
}

export interface ProjectSplitPreviewVO {
  requestId: number
  draftVersion: number
  valid: boolean
  previewHash?: string
  validatedAt?: string
  parentVersion: number
  scopeVersion: number
  treeVersion: number
  errors: string[]
  items: { clientItemKey: string; valid: boolean; errors: string[] }[]
}

const baseUrl = '/pms/project-split-requests'

export const createDraft = (data: ProjectSplitDraftInput, idempotencyKey: string) =>
  request.post<ProjectSplitDraftVO>({
    url: baseUrl,
    data,
    headers: { 'Idempotency-Key': idempotencyKey, 'If-Match': '0' }
  })

export const getDraft = (id: number) =>
  request.get<ProjectSplitDraftVO>({ url: `${baseUrl}/${id}` })

export const updateDraft = (
  id: number,
  data: ProjectSplitDraftInput,
  expectedDraftVersion: number,
  idempotencyKey: string
) =>
  request.put<ProjectSplitDraftVO>({
    url: `${baseUrl}/${id}`,
    data: { ...data, expectedDraftVersion },
    headers: { 'Idempotency-Key': idempotencyKey, 'If-Match': String(expectedDraftVersion) }
  })

export const previewDraft = (id: number, expectedDraftVersion: number, idempotencyKey: string) =>
  request.post<ProjectSplitPreviewVO>({
    url: `${baseUrl}/${id}/actions/preview`,
    params: { expectedDraftVersion },
    headers: { 'Idempotency-Key': idempotencyKey, 'If-Match': String(expectedDraftVersion) }
  })

export const validateDraft = (id: number, expectedDraftVersion: number, idempotencyKey: string) =>
  request.post<ProjectSplitPreviewVO>({
    url: `${baseUrl}/${id}/actions/validate`,
    params: { expectedDraftVersion },
    headers: { 'Idempotency-Key': idempotencyKey, 'If-Match': String(expectedDraftVersion) }
  })

export const applyDraft = (
  draft: ProjectSplitDraftVO,
  idempotencyKey: string
) =>
  request.post<{ requestId: number; projectIds: number[]; treeVersion: number }>({
    url: `${baseUrl}/${draft.id}/actions/apply`,
    data: {
      expectedParentVersion: draft.parentVersion,
      expectedScopeVersion: draft.scopeVersion,
      expectedTreeVersion: draft.treeVersion
    },
    headers: { 'Idempotency-Key': idempotencyKey, 'If-Match': String(draft.draftVersion) }
  })
