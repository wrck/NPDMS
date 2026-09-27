// 中性过程定义与场景执行客户端：定义创建/发布/查询与实例/结果查看，
// 全部按服务端契约调用，不在前端写死实体或后端分支。
import request from '@/config/axios'

const baseUrl = '/api/v1/pms/process-definitions'
const scenarioUrl = '/api/v1/pms/scenario-instances'

export interface DefinitionConditionVO {
  fieldCode: string
  operator: string
  values: (string | number | boolean | null)[]
}

export interface DefinitionCreateRequest {
  definitionCode: string
  name: string
  ownerModule: string
  entityType: string
  operationCode: string
  ruleCode: string
  ruleVersion?: string
  conditions: DefinitionConditionVO[]
  resultType: string
}

export interface ProcessDefinitionVO {
  id: number
  definitionCode: string
  definitionVersion: number
  name: string
  ownerModule: string
  entityType: string
  entityStableCode: string
  entityContractVersion: number
  operationCode: string
  operationVersion: number
  ruleCode: string
  ruleVersion: string
  conditionsJson: string
  resultType: string
  status: 'DRAFT' | 'PUBLISHED'
  publishedAt?: string
}

export const createDefinition = (data: DefinitionCreateRequest) =>
  request.post<ProcessDefinitionVO>({ url: baseUrl, data, silentError: true })

export const publishDefinition = (id: number) =>
  request.post<ProcessDefinitionVO>({ url: `${baseUrl}/${id}/publish`, silentError: true })

export const getDefinitionPage = (params: {
  definitionCode?: string
  ownerModule?: string
  entityType?: string
  status?: string
  pageNo?: number
  pageSize?: number
}) => request.get<{ list: ProcessDefinitionVO[]; total: number }>({ url: `${baseUrl}/page`, params, silentError: true })

export const getDefinition = (id: number) =>
  request.get<ProcessDefinitionVO>({ url: `${baseUrl}/${id}`, silentError: true })

export interface ScenarioRunOutcomeVO {
  backendId: string
  definitionCode: string
  definitionVersion: number
  verdict: 'SATISFIED' | 'UNSATISFIED' | 'UNKNOWN'
  resultId?: string
  replay?: boolean
}

export const runScenario = (data: {
  definitionCode: string
  entityId: number
  idempotencyKey: string
  backendId?: string
}) => request.post<ScenarioRunOutcomeVO>({ url: `${scenarioUrl}/run`, data, silentError: true })

export const getScenarioInstances = (definitionCode: string) =>
  request.get<ScenarioRunOutcomeVO[]>({ url: `${scenarioUrl}/instances`, params: { definitionCode }, silentError: true })

export interface ScenarioResultVO {
  resultType: string
  objectRef: { tenantId: number; ownerModule: string; entityType: string; entityId: number }
  resultId: string
  semantics: string
  formationBasis: string
  formedAt: number
  valid: boolean
}

export const getScenarioResults = (definitionCode: string) =>
  request.get<ScenarioResultVO[]>({ url: `${scenarioUrl}/results`, params: { definitionCode }, silentError: true })
