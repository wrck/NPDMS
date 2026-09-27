import request from '@/config/axios'

/** 公共内容历史：修订元数据与通用草稿流（发起/保存/冻结生效）。 */
export interface RevisionRefVO {
  entity: { tenantId: number; ownerModule: string; entityType: string; entityId: number }
  revisionId: number
}

export interface RevisionVO {
  ref: RevisionRefVO
  revisionNo: number
  sourceRevisionId?: number
  baseEffectiveRevisionId?: number
  baseEntityVersion?: number
  state: 'DRAFT' | 'FROZEN'
  effective: boolean
  version: number
  reason?: string
  frozenBy?: number
  frozenAt?: string
}

const baseUrl = '/api/v1/pms/entities'

export const listRevisions = (
  ownerModule: string,
  entityType: string,
  entityId: number,
  params?: { beforeId?: number; limit?: number }
) =>
  request.get<RevisionVO[]>({
    url: `${baseUrl}/${ownerModule}/${entityType}/${entityId}/revisions`,
    params,
    silentError: true
  })

export const createRevision = (
  ownerModule: string,
  entityType: string,
  entityId: number,
  data: { sourceRevisionId?: number; reason: string }
) =>
  request.post<RevisionVO>({
    url: `${baseUrl}/${ownerModule}/${entityType}/${entityId}/revisions`,
    data,
    silentError: true
  })

export const saveRevision = (
  ownerModule: string,
  entityType: string,
  entityId: number,
  revisionId: number,
  data: { expectedVersion: number; fields: Record<string, unknown> }
) =>
  request.post<RevisionVO>({
    url: `${baseUrl}/${ownerModule}/${entityType}/${entityId}/revisions/${revisionId}/save`,
    data,
    silentError: true
  })

export const completeRevision = (
  ownerModule: string,
  entityType: string,
  entityId: number,
  revisionId: number,
  data: { expectedVersion: number }
) =>
  request.post<RevisionVO>({
    url: `${baseUrl}/${ownerModule}/${entityType}/${entityId}/revisions/${revisionId}/complete`,
    data,
    silentError: true
  })

export const discardRevision = (
  ownerModule: string,
  entityType: string,
  entityId: number,
  revisionId: number
) =>
  request.post<boolean>({
    url: `${baseUrl}/${ownerModule}/${entityType}/${entityId}/revisions/${revisionId}/discard`,
    silentError: true
  })
