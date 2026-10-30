import request from '@/config/axios'
export interface ManualCommand {
  requestKey: string
  expectedVersion: number
  host: string
  port: number
  protocol: string
  username: string
  password: string
  commands: string
}
export interface Execution {
  id: number
  actorId: number
  createdAt: string
  consumedResultVersion?: number
  commandText?: string
  task: {
    platformTaskId: string
    status: string
    technicalStage: string
    externalStatus?: string
    failureCategory?: string
    host: string
    port: number
    protocol: string
    deviceName: string
    templateHash: string
    fileVersionId?: number
    resultVersion?: number
  }
}
const url = (id: number) => `/api/v1/pms/implementation/configurations/${id}/collections`
export const submit = (id: number, data: ManualCommand) =>
  request.post<Execution>({ url: url(id), data })
export const page = (id: number, pageNo: number) =>
  request.get<{ list: Execution[]; total: number }>({
    url: url(id),
    params: { pageNo, pageSize: 10 }
  })
export const consume = (id: number, executionId: number) =>
  request.post<Execution>({ url: `${url(id)}/${executionId}/consume` })
export const findByRequestKey = (id: number, requestKey: string) =>
  request.get<Execution | null>({ url: `${url(id)}/by-request-key`, params: { requestKey } })
export const cancel = (id: number, executionId: number) =>
  request.post<boolean>({ url: `${url(id)}/${executionId}/cancel` })
export const download = (id: number, executionId: number) =>
  request.post<string>({ url: `${url(id)}/${executionId}/download` })
