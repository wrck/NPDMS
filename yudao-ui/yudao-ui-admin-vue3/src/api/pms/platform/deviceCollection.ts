import request from '@/config/axios'

export type Id = number | string
export type Entry = 'center' | 'configuration' | 'joint-test'
export interface Source {
  entry: Entry
  objectId: Id
  projectId: Id
  deviceId?: Id
  version: number
  manualAllowed: boolean
  canExecute: boolean
  completionMode: string
  title: string
}
export interface Command {
  requestKey: string
  expectedVersion: number
  deviceId?: Id
  host?: string
  port?: number
  protocol: string
  username?: string
  password?: string
  commands?: string
  templateId?: Id
  credentialId?: Id
  retryOfId?: Id
}
export interface Execution {
  id: Id
  actorId: Id
  createdAt: string
  consumedResultVersion?: number
  commandText?: string
  templateName?: string
  retryOfId?: Id
  task: {
    platformTaskId: string
    deviceId: string
    deviceName: string
    host: string
    port: number
    protocol: string
    status: string
    technicalStage: string
    externalStatus?: string
    failureCategory?: string
    credentialMode: string
    credentialId?: Id
    templateId: string
    templateVersion: string
    templateHash: string
    fileVersionId?: Id
    resultVersion?: number
    completionMode: string
  }
}
export interface Template {
  id?: Id
  version?: number
  code: string
  name: string
  purpose: string
  ownerContext?: string
  protocol: string
  deviceModel?: string
  revision: number
  commands: string
  status?: string
  publicationStarted?: boolean
}
export interface Connection {
  id: Id
  name: string
  projectId: Id
  deviceId: Id
  protocol: string
  host: string
  port: number
  username: string
  status: string
  version: number
  registrationKey?: string
  registrationTemplateId?: Id
  registrationExpiresAt?: string
}
export interface SaveConnection {
  requestKey: string
  name: string
  projectId: Id
  deviceId: Id
  protocol: string
  host: string
  port: number
  username: string
  secret: string
  templateId: Id
  expiresAt: string
}
export interface Grant {
  id: Id
  granteeId: string
  deviceId: string
  protocol: string
  templateId: string
  expiresAt: string
  status: string
}
const root = '/api/v1/pms/device-collection'
const source = (entry: Entry, id: Id) => `${root}/sources/${entry}/${id}`
export const context = (entry: Entry, id: Id) => request.get<Source>({ url: source(entry, id) })
export const submit = (entry: Entry, id: Id, data: Command) =>
  request.post<Execution>({ url: `${source(entry, id)}/executions`, data })
export const page = (entry: Entry, id: Id, pageNo: number) =>
  request.get<{ list: Execution[]; total: number }>({
    url: `${source(entry, id)}/executions`,
    params: { pageNo, pageSize: 10 }
  })
export const findByRequestKey = (entry: Entry, id: Id, requestKey: string) =>
  request.get<Execution | null>({
    url: `${source(entry, id)}/executions/by-request-key`,
    params: { requestKey }
  })
export const consume = (entry: Entry, id: Id, execution: Id) =>
  request.post<Execution>({ url: `${source(entry, id)}/executions/${execution}/consume` })
export const cancel = (entry: Entry, id: Id, execution: Id) =>
  request.post<boolean>({ url: `${source(entry, id)}/executions/${execution}/cancel` })
export const download = (entry: Entry, id: Id, execution: Id) =>
  request.post<string>({ url: `${source(entry, id)}/executions/${execution}/download` })
export interface BusinessLog {
  id: Id
  executionId: Id
  platformTaskId: string
  fileVersionId: Id
  resultVersion: number
  commandText?: string
  externalStatus: string
  failureCategory?: string
  templateName?: string
  receivedAt: string
}
export const businessLogs = (entry: Entry, id: Id, pageNo: number) =>
  request.get<{ list: BusinessLog[]; total: number }>({
    url: `/api/v1/pms/implementation/${entry}/${id}/collection-logs`,
    params: { pageNo, pageSize: 10 }
  })
export const templates = (
  params: { purpose?: string; protocol?: string; publishedOnly?: boolean } = {}
) => request.get<Template[]>({ url: `${root}/templates`, params })
export const saveTemplate = (data: Template) =>
  request.post<Template>({ url: `${root}/templates`, data })
export const publishTemplate = (row: Template) =>
  request.post<Template>({
    url: `${root}/templates/${row.id}/publish`,
    params: { version: row.version }
  })
export const retireTemplate = (row: Template) =>
  request.post<Template>({
    url: `${root}/templates/${row.id}/retire`,
    params: { version: row.version }
  })
export const connections = (projectId: Id) =>
  request.get<Connection[]>({ url: `${root}/connections`, params: { projectId } })
export const usableConnections = (
  projectId: Id,
  deviceId: Id | undefined,
  protocol: string,
  templateId: Id | undefined
) =>
  request.get<Connection[]>({
    url: `${root}/connections/usable`,
    params: { projectId, deviceId, protocol, templateId }
  })
export const saveConnection = (data: SaveConnection) =>
  request.post<Connection>({ url: `${root}/connections`, data, timeout: 60000 })
export const disableConnection = (id: Id) =>
  request.post<boolean>({ url: `${root}/connections/${id}/disable` })
export const grants = (id: Id) => request.get<Grant[]>({ url: `${root}/connections/${id}/grants` })
export const grantConnection = (id: Id, data: { userId: Id; templateId: Id; expiresAt: string }) =>
  request.post<Grant>({ url: `${root}/connections/${id}/grants`, data })
export const revokeGrant = (id: Id, grant: Id) =>
  request.post<boolean>({ url: `${root}/connections/${id}/grants/${grant}/revoke` })
export const purposes: Record<string, string> = {
  configuration: '配置调试',
  'joint-test': '业务联调',
  center: '独立采集',
  cutover: '割接（调用契约）',
  inspection: '巡检（调用契约）'
}
