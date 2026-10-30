import { deviceOpsApi, type ScheduleRequest } from './device-ops'
export interface Schedule extends ScheduleRequest { scheduleKey: string; nextRunAt: string | null; lastRunAt: string | null; lastStatus: string; version: number }
const path = (project: string, key?: string) => `projects/${encodeURIComponent(project)}/schedules${key ? '/' + encodeURIComponent(key) : ''}`
export const scheduleApi = {
  list: (project: string, namespace: string, signal?: AbortSignal) => deviceOpsApi.get<Schedule[]>(path(project), { params: { namespace }, signal }).then(r => r.data),
  save: (project: string, key: string, request: ScheduleRequest, existing: boolean) => deviceOpsApi.put<Schedule>(path(project, key), { ...request, enabled: existing && request.enabled }).then(r => r.data),
  disable: (project: string, namespace: string, key: string) => deviceOpsApi.delete(path(project, key), { params: { namespace } }).then(() => undefined)
}
export function parseSchedule(text: string, project: string, namespace: string): ScheduleRequest {
  const value = JSON.parse(text) as ScheduleRequest
  if (!value || value.projectKey !== project || value.namespace !== namespace) throw new Error('JSON 的 projectKey / namespace 必须匹配当前查询范围。')
  for (const key of ['projectHint', 'scriptKey', 'scriptVersion', 'cron', 'timezone', 'callbackUri'] as const) if (typeof value[key] !== 'string' || !value[key].trim()) throw new Error(`${key} 必填。`)
  if (!Array.isArray(value.deviceKeyHints) || !value.deviceKeyHints.length || value.deviceKeyHints.some(v => typeof v !== 'string' || !v.trim())) throw new Error('deviceKeyHints 必须包含非空设备标识。')
  if (typeof value.enabled !== 'boolean') throw new Error('enabled 必须为布尔值。')
  return value
}
