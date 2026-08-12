import request from '@/config/axios'

export interface CutExecutionVO {
  id?: number
  taskId: number
  code: string
  stepName: string
  operatorUserId?: number
  operationTime?: Date
  result?: string
  exceptionRecord?: string
  evidenceUrl?: string
  status?: number
  remark?: string
  version?: number
  createTime?: Date
}

const baseUrl = '/pms/cut-execution'

export const getCutExecutionPage = (params: PageParam) =>
  request.get({ url: `${baseUrl}/page`, params })
export const getCutExecution = (id: number) =>
  request.get({ url: `${baseUrl}/get`, params: { id } })
export const createCutExecution = (data: CutExecutionVO) =>
  request.post({ url: `${baseUrl}/create`, data })
export const updateCutExecution = (data: CutExecutionVO) =>
  request.put({ url: `${baseUrl}/update`, data })
export const deleteCutExecution = (id: number) =>
  request.delete({ url: `${baseUrl}/delete`, params: { id } })
export const startCutExecution = (id: number) =>
  request.put({ url: `${baseUrl}/start`, params: { id } })
export const passCutExecution = (id: number) =>
  request.put({ url: `${baseUrl}/pass`, params: { id } })
export const failCutExecution = (id: number) =>
  request.put({ url: `${baseUrl}/fail`, params: { id } })
export const rollbackCutExecution = (id: number) =>
  request.put({ url: `${baseUrl}/rollback`, params: { id } })
