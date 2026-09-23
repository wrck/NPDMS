import request from '@/config/axios'
type Id = string | number
export interface ReviewDefinition { id: string; key: string; nodes: { key: string; name: string; responsibility: string }[] }
export interface ReviewRecord { id: Id; processDefinitionId: string; processInstanceId: string; status: string; sourceVersion: number; reviewsJson?: string; completedAt?: string }
const base = '/api/v1/pms/solution-reviews'
export interface ReviewPolicy { configured: boolean; reviewLevel: number | null; reason?: string; evidenceJson?: string }
export const policy = (projectId: Id): Promise<ReviewPolicy> => request.get({ url: `${base}/policy`, params: { projectId } })
export const definition = (): Promise<ReviewDefinition> => request.get({ url: `${base}/definition` })
export const read = (projectId: Id, solutionId: Id): Promise<ReviewRecord | null> => request.get({ url: base, params: { projectId, solutionId } })
export const start = (data: { projectId: Id; solutionId: Id; expectedVersion: number; processDefinitionId: string; candidates: Record<string, Id> }): Promise<ReviewRecord> => request.post({ url: base, data })
export const refresh = (projectId: Id, solutionId: Id): Promise<ReviewRecord> => request.post({ url: `${base}/refresh`, data: { projectId, solutionId } })
export const revise = (projectId: Id, solutionId: Id): Promise<Id> => request.post({ url: `${base}/revise`, data: { projectId, solutionId } })
