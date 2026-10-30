import request, { writeHeaders, type WireLong } from './request'
import type { ReportFileView } from './tasks'

export interface UmcValue {
  id: string
  object?: string
  value?: string
  result?: boolean
}
export interface UmcTarget {
  id: string
  name: string
  info?: string
  error?: string
  result?: boolean
  valuelist?: UmcValue[]
}
export interface UmcRule {
  id: string
  ruleid: string
  name: string
  type?: string
  level?: number
  cmd?: string
  output?: string
  info?: string
  suggest?: string
  error?: string
  result?: boolean
  support?: boolean
  targetlist?: UmcTarget[]
}
export interface UmcDevice {
  id: string
  deviceip?: string
  devicelabel?: string
  devmodel?: string
  sn?: string
  version?: string
  info?: string
  rulelist?: UmcRule[]
}
export interface UmcReport {
  id: WireLong
  attemptId: WireLong
  revisionNo: number
  statusCode: string
  failureMessage?: string
  generatedAt?: number
  files: ReportFileView[]
  detail?: { id: string; taskid: string; name: string; info?: string; devlist: UmcDevice[] }
}
const base = '/api/v1/pms/inspection-reports'
export const detail = (taskId: WireLong, reportId: WireLong) =>
  request.get<UmcReport>({ url: `${base}/${reportId}/result`, params: { taskId } })
export const retry = (input: {
  taskId: WireLong
  reportId: WireLong
  version: WireLong
  key: string
}) =>
  request.post<UmcReport>({
    url: `${base}/${input.reportId}/actions/retry-external`,
    data: { taskId: input.taskId },
    timeout: 195000,
    headers: writeHeaders(input.key, input.version)
  })
