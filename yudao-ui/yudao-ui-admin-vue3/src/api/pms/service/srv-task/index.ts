import request from '@/config/axios'

export interface SrvTaskVO {
  id?: number
  projectId: number
  equipmentId?: number
  code: string
  name: string
  inspectionMode?: string
  sourceType?: string
  sourceId?: number
  scheduledTime?: Date
  actualTime?: Date
  status?: number
  accountCheckResult?: string
  remark?: string
  version?: number
  createTime?: Date
}

const baseUrl = '/pms/srv-task'

export const getSrvTaskPage = (params: PmsProjectPageParam) =>
  request.get({ url: `${baseUrl}/page`, params })
export const getSrvTask = (id: number) =>
  request.get({ url: `${baseUrl}/get`, params: { id } })
export const createSrvTask = (data: SrvTaskVO) =>
  request.post({ url: `${baseUrl}/create`, data })
export const updateSrvTask = (data: SrvTaskVO) =>
  request.put({ url: `${baseUrl}/update`, data })
export const deleteSrvTask = (id: number) =>
  request.delete({ url: `${baseUrl}/delete`, params: { id } })
export const validateEquipmentAccount = (id: number) =>
  request.put({ url: `${baseUrl}/validate-equipment-account`, params: { id } })
export const submitSrvTask = (id: number) =>
  request.put({ url: `${baseUrl}/submit`, params: { id } })
export const startExecution = (id: number) =>
  request.put({ url: `${baseUrl}/start-execution`, params: { id } })
export const completeExecution = (id: number) =>
  request.put({ url: `${baseUrl}/complete-execution`, params: { id } })
export const confirmReport = (id: number) =>
  request.put({ url: `${baseUrl}/confirm-report`, params: { id } })
export const cancelSrvTask = (id: number) =>
  request.put({ url: `${baseUrl}/cancel`, params: { id } })

// ============================================================================
// 在线巡检执行子资源（SrvExecutionController，复用 pms:srv-task:* 权限）
// ============================================================================
export interface SrvExecutionVO {
  id?: number
  taskId: number
  code: string
  ruleId?: number
  executionTime?: Date
  executorUserId?: number
  result?: string
  exceptionRecord?: string
  evidenceUrl?: string
  status?: number
  remark?: string
  version?: number
  createTime?: Date
}

const executionUrl = '/pms/srv-execution'

export const getSrvExecutionPage = (params: PageParam) =>
  request.get({ url: `${executionUrl}/page`, params })
export const getSrvExecution = (id: number) =>
  request.get({ url: `${executionUrl}/get`, params: { id } })
export const createSrvExecution = (data: SrvExecutionVO) =>
  request.post({ url: `${executionUrl}/create`, data })
export const updateSrvExecution = (data: SrvExecutionVO) =>
  request.put({ url: `${executionUrl}/update`, data })
export const deleteSrvExecution = (id: number) =>
  request.delete({ url: `${executionUrl}/delete`, params: { id } })
export const startSrvExecution = (id: number) =>
  request.put({ url: `${executionUrl}/start-execution`, params: { id } })
export const completeSrvExecution = (id: number) =>
  request.put({ url: `${executionUrl}/complete-execution`, params: { id } })
export const markSrvExecutionAbnormal = (id: number) =>
  request.put({ url: `${executionUrl}/mark-abnormal`, params: { id } })

// ============================================================================
// 离线巡检文件子资源（SrvOfflineFileController，复用 pms:srv-task:* 权限）
// ============================================================================
export interface SrvOfflineFileVO {
  id?: number
  taskId: number
  code: string
  fileUrl?: string
  fileSize?: number
  fileChecksum?: string
  parseStatus?: number
  parseResult?: string
  errorDetail?: string
  parsedBy?: number
  parsedTime?: Date
  remark?: string
  version?: number
  createTime?: Date
}

const offlineFileUrl = '/pms/srv-offline-file'

export const getSrvOfflineFilePage = (params: PageParam) =>
  request.get({ url: `${offlineFileUrl}/page`, params })
export const getSrvOfflineFile = (id: number) =>
  request.get({ url: `${offlineFileUrl}/get`, params: { id } })
export const createSrvOfflineFile = (data: SrvOfflineFileVO) =>
  request.post({ url: `${offlineFileUrl}/create`, data })
export const updateSrvOfflineFile = (data: SrvOfflineFileVO) =>
  request.put({ url: `${offlineFileUrl}/update`, data })
export const deleteSrvOfflineFile = (id: number) =>
  request.delete({ url: `${offlineFileUrl}/delete`, params: { id } })
export const startParseSrvOfflineFile = (id: number) =>
  request.put({ url: `${offlineFileUrl}/start-parse`, params: { id } })
export const parseSuccessSrvOfflineFile = (id: number) =>
  request.put({ url: `${offlineFileUrl}/parse-success`, params: { id } })
export const parseFailedSrvOfflineFile = (id: number) =>
  request.put({ url: `${offlineFileUrl}/parse-failed`, params: { id } })
