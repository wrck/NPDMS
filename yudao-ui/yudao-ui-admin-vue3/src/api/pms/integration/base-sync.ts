import request from '@/config/axios'
import type { Id } from './index'

export interface BaseSyncTask {
  code: string
  name: string
}
export interface Registration {
  id: Id
  connectionId: Id
  taskCode: string
  batchSize: number
  maxRows: number
  activeRunId?: Id | null
}
export interface SyncCounts {
  read: number
  inserted: number
  updated: number
  unchanged: number
  deleted: number
}
const baseURL = import.meta.env.VITE_BASE_URL
const root = '/api/v1/pms/integration/base-sync'
export const getTasks = () => request.get<BaseSyncTask[]>({ baseURL, url: root + '/tasks' })
export const getRegistrations = () =>
  request.get<Registration[]>({ baseURL, url: root + '/registrations' })
export interface RunRequest {
  preview: boolean
  taskId: Id
  batchSize?: number
  stages?: string[]
}
export const run = ({ preview, ...data }: RunRequest) =>
  request.post<SyncCounts>({
    baseURL,
    url: root + (preview ? '/preview' : '/execute'),
    data,
    timeout: 0
  })
