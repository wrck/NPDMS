import request from '@/config/axios'
import type { Entry, Id } from './deviceCollection'
import type { ParseResultEnvelope } from '@/components/DeviceCollection/semantic/types'

export interface LogParseInput {
  requestKey: string
  projectId: Id
  inputName?: string
  inputText?: string
  commandHint?: string
  entry?: Entry
  objectId?: Id
  executionId?: Id
}

export interface LogParse {
  id: Id
  requestKey: string
  inputName: string
  projectId: Id
  entry?: Entry
  objectId?: Id
  executionId?: Id
  sourceFileVersionId?: Id
  state: string
  taskId?: string
  releaseVersion?: string
  waitReason?: string
  createTime?: string
  warnings: string[]
  result?: ParseResultEnvelope
  message?: string
  deviceProjection?: {
    status: string
    deviceId?: Id
    serialNumber?: string
    versionStatus: string
    message: string
  }
}

const root = '/api/v1/pms/device-collection/log-parses'
export const submitLogParse = (data: LogParseInput) =>
  request.post<LogParse>({ url: root, data, timeout: 30000 })
export const getLogParse = (id: Id) => request.get<LogParse>({ url: `${root}/${id}` })
export const pageLogParses = (params: {
  projectId: Id
  entry?: Entry
  objectId?: Id
  executionId?: Id
  pageNo: number
}) =>
  request.get<{ list: LogParse[]; total: number }>({
    url: root,
    params: { ...params, pageSize: 10 }
  })
