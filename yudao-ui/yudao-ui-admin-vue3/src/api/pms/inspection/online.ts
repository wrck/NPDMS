import request, { writeHeaders } from './request'
import type { WireLong, TaskSummary, ExecutionView } from './tasks'

export interface Connection {
  host?: string
  port?: number
  protocol: 'SSH' | 'TELNET'
  username?: string
  credentialId?: WireLong
}
export interface ReserveInput {
  deviceId: WireLong
  purpose: 'PRECHECK' | 'COLLECT'
  precheckExecutionId?: WireLong
  connection: Connection
}
export interface Reserved { task: TaskSummary; execution: ExecutionView }
const root = (taskId: WireLong) => `/api/v1/pms/inspection-tasks/${taskId}/online-executions`
export const reserve = (taskId: WireLong, data: ReserveInput, version: WireLong, key: string) =>
  request.post<Reserved>({ url: root(taskId), data, headers: writeHeaders(key, version) })
export const dispatch = (taskId: WireLong, executionId: WireLong, password: string | undefined, version: WireLong, key: string) =>
  request.post<Reserved>({ url: `${root(taskId)}/${executionId}/actions/dispatch`, data: { password },
    headers: writeHeaders(key, version), timeout: 180000 })
export const refresh = (taskId: WireLong, executionId: WireLong, version: WireLong, key: string) =>
  request.post<TaskSummary>({ url: `${root(taskId)}/${executionId}/actions/refresh`, data: {},
    headers: writeHeaders(key, version), timeout: 180000 })
export const cancel = (taskId: WireLong, executionId: WireLong, reason: string, version: WireLong, key: string) =>
  request.post<TaskSummary>({ url: `${root(taskId)}/${executionId}/actions/cancel`, data: { reason },
    headers: writeHeaders(key, version), timeout: 180000 })
export const download = (taskId: WireLong, executionId: WireLong) =>
  request.get<string>({ url: `${root(taskId)}/${executionId}/log-download` })
