// 结果订阅与执行证据客户端：订阅建立、存量补扫、判断结果查询；
// 全部按服务端契约调用，不在前端写死实体或后端分支。
import request from '@/config/axios'

const baseUrl = '/api/v1/pms/result-subscriptions'
const resultUrl = '/api/v1/pms/business-results'

export interface SubscriptionCreateRequest {
  subscriptionCode: string
  subscriberKind: string
  subscriberNodeKey: string
  resultType: string
  ownerModule: string
  entityType: string
  acquisition: string
  selection: string
  validity?: string
  pinnedResultId?: string
  expectedObjectIds?: number[]
  roundNo?: number
}

export interface SubscriptionVO {
  id: number
  subscriptionCode: string
  subscriberKind: string
  subscriberNodeKey: string
  resultType: string
  ownerModule: string
  entityType: string
  policy: {
    acquisition: string
    selection: string
    validity: string
    pinnedResultId?: string
    expectedObjectIds: number[]
  }
  roundNo: number
  formationBaseline: number
  status: string
}

export interface SubscriptionDecisionVO {
  status: string
  examined: number
  eligible: number
  missingObjects: number[]
  basis: string
  adoptedResultRefs: string[]
}

export interface SubscriptionRow {
  id: number
  subscriptionCode: string
  subscriberKind: string
  subscriberNodeKey: string
  resultType: string
  ownerModule: string
  entityType: string
  acquisition: string
  selection: string
  validityPolicy: string
  pinnedResultId?: string
  roundNo: number
  formationBaseline: number
  status: string
  lastExamined?: number
  lastEligible?: number
}

export const createSubscription = (data: SubscriptionCreateRequest) =>
  request.post<SubscriptionVO>({ url: baseUrl, data, silentError: true })

export const backfillSubscription = (id: number, throughSequence: number) =>
  request.post<SubscriptionDecisionVO>({ url: `${baseUrl}/${id}/backfill`, data: { throughSequence }, silentError: true })

export const getSubscription = (id: number) =>
  request.get<SubscriptionVO>({ url: `${baseUrl}/${id}`, silentError: true })

export const getSubscriptionPage = (params: {
  subscriptionCode?: string
  resultType?: string
  ownerModule?: string
  entityType?: string
  status?: string
  pageNo?: number
  pageSize?: number
}) => request.get<{ list: SubscriptionRow[]; total: number }>({ url: `${baseUrl}/page`, params, silentError: true })

export const getLastDecision = (id: number) =>
  request.get<SubscriptionDecisionVO>({ url: `${baseUrl}/${id}/last-decision`, silentError: true })

export const invalidateResult = (data: {
  resultType: string
  ownerModule: string
  entityType: string
  entityId: number
  formationBasis: string
}) => request.post<boolean>({ url: `${resultUrl}/invalidate`, data, silentError: true })
