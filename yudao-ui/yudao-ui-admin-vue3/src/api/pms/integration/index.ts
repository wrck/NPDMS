import request from '@/config/axios'
export type Id = string | number
export interface Page<T> {
  list: T[]
  total: number
}
export interface Connection {
  id: Id
  name: string
  url: string
  username: string
}
export interface Mapping {
  target: string
  source: string
  conversion: string
  constant?: unknown
  defaultValue?: unknown
  values?: Record<string, unknown>
}
export interface Source {
  object: string
  sourceObject: string
  readMode: string
  table: string
  sql?: string
  parameters: Record<string, unknown>
  sourceKey: string
  syncPrimaryKey?: boolean
  updatedAt?: string
  columns: string[]
  filters: { column: string; operator: string; value?: unknown }[]
  mappings: Mapping[]
}
export interface Definition {
  adapter: string
  sourceSystem: string
  connectionId: Id
  mode: string
  loadingMode?: string
  clearBeforeLoad?: boolean
  resetMappingsBeforeLoad?: boolean
  missingPolicy: string
  cron: string
  fullCron: string
  overlapSeconds: number
  retryCount: number
  retryIntervalSeconds: number
  maxRows: number
  autoPaging?: boolean
  maxBytes: number
  sources: Source[]
}
export interface Task {
  id?: Id
  name: string
  version: number
  validatedVersion?: number
  definition: Definition
  enabled?: boolean
  activeRunId?: Id
  checkpoint?: string
  nextRunAt?: string
}
export interface Adapter {
  key: string
  label: string
  missingPolicies: string[]
  loadingModes: string[]
  supportsTargetClear: boolean
  objects: {
    name: string
    label: string
    supportsSourcePrimaryKey: boolean
    fields: { name: string; label: string; type: string; required: boolean }[]
  }[]
}
export interface Run {
  readCount?: number
  summaryJson?: string
  id: Id
  taskId: Id
  status: string
  preview: boolean
  fullSnapshot: boolean
  adoptExisting: boolean
  parentRunId?: Id
  pageNumber?: number
  pagingJson?: string
  startedAt: string
  finishedAt?: string
  errorMessage?: string
  cachePending?: boolean
}
export interface Change {
  object: string
  sourceKey: string
  targetId?: Id
  action: string
  before: Record<string, unknown>
  after: Record<string, unknown>
  message?: string
}
export interface Binding {
  id: Id
  objectKey: string
  sourceObject: string
  sourceKey: string
  targetId: Id
  fieldsJson: string
}
const base = '/api/v1/pms/integration'
export const getConnections = (params = { pageNo: 1, pageSize: 100 }) =>
  request.get<Page<Connection>>({ url: base + '/connections', params })
export const saveConnection = (
  data: Partial<Connection> & { password?: string; dataSourceId?: Id }
) => request.post<Id>({ url: base + '/connections', data })
export const getDataSources = () => request.get<Connection[]>({ url: base + '/data-sources' })
export const testConnection = (id: Id) =>
  request.post<boolean>({ url: base + '/connections/' + id + '/test' })
export const getTables = (id: Id) =>
  request.get<{ name: string; type: string }[]>({ url: base + '/connections/' + id + '/tables' })
export const getColumns = (id: Id, table: string) =>
  request.get<{ name: string; type: string }[]>({
    url: base + '/connections/' + id + '/columns',
    params: { table }
  })
export const getAdapters = () => request.get<Adapter[]>({ url: base + '/adapters' })
export const getEhrTemplate = (connectionId: Id) =>
  request.get<Definition>({ url: base + '/templates/ehr', params: { connectionId } })
export const getDppmsOrderTemplate = (connectionId: Id) =>
  request.get<Definition>({ url: base + '/templates/dppms-orders', params: { connectionId } })
export const getTasks = (params = { pageNo: 1, pageSize: 20 }) =>
  request.get<Page<Task>>({ url: base + '/tasks', params })
export const getTask = (id: Id) => request.get<Task>({ url: base + '/tasks/' + id })
export const saveTask = (task: Task) =>
  request.post<Id>({
    url: base + '/tasks',
    data: {
      id: task.id,
      expectedVersion: task.version,
      name: task.name,
      definition: task.definition
    }
  })
export interface ConfigurationCheck {
  allowed: boolean
  existingTaskId?: Id
  existingTaskName?: string
  message?: string
}
export const checkTaskConfiguration = (task: Task) =>
  request.post<ConfigurationCheck>({
    url: base + '/tasks/configuration-check',
    data: {
      id: task.id,
      expectedVersion: task.version,
      name: task.name,
      definition: task.definition
    }
  })
export const schedule = (task: Task, enabled: boolean) =>
  request.put({
    url: base + '/tasks/' + task.id + '/schedule',
    data: { version: task.version, enabled }
  })
export const startRun = (
  task: Task,
  preview: boolean,
  adoptExisting = false,
  retryOf?: Id,
  full = true,
  confirmPreparation = false
) =>
  request.post<Id>({
    url: base + '/runs',
    data: {
      taskId: task.id,
      expectedVersion: task.version,
      requestKey: crypto.randomUUID(),
      preview,
      full,
      adoptExisting,
      retryOf,
      confirmPreparation
    }
  })
export const getRuns = (params: {
  pageNo: number
  pageSize: number
  taskId?: Id
  parentRunId?: Id
}) => request.get<Page<Run>>({ url: base + '/runs', params })
export const getRun = (id: Id) => request.get<Run>({ url: base + '/runs/' + id })
export const getChanges = (id: Id, params = { pageNo: 1, pageSize: 20 }) =>
  request.get<Page<Change>>({ url: base + '/runs/' + id + '/changes', params })
export const getBindings = (params: { pageNo: number; pageSize: number; taskId: Id }) =>
  request.get<Page<Binding>>({ url: base + '/mappings', params })
export const runLabels: Record<string, string> = {
  QUEUED: '排队中',
  READING: '读取来源',
  VALIDATING: '校验中',
  APPLYING: '写入中',
  PREVIEW_READY: '预览就绪（未写入）',
  SUCCESS: '同步成功',
  FAILED: '失败（未提交）'
}
export const changeLabels: Record<string, string> = {
  CLEARED: '加载前清空',
  CREATED: '新增',
  UPDATED: '更新',
  UNCHANGED: '未变化',
  DISABLED: '停用',
  ADOPTED: '接管',
  CONFLICT: '冲突',
  ISSUE: '待处理问题',
  SKIPPED: '跳过'
}
