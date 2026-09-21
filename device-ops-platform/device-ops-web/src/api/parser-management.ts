import { deviceOpsApi } from './device-ops'
import type { LogType, ParserRelease, ReleaseRequest, ReleaseValidation, RuntimeStatus, ParseTask } from '@/types/management'
import type { ParseResultEnvelope } from '@/types/parser'
const id = encodeURIComponent
const get = <T>(path: string, signal?: AbortSignal) => deviceOpsApi.get<T>(path, { signal }).then(r => r.data)
const post = <T>(path: string, body?: unknown) => deviceOpsApi.post<T>(path, body).then(r => r.data)
export const parserApi = {
  logTypes: (signal?: AbortSignal) => get<LogType[]>('parser-log-types', signal),
  logType: (type: string, signal?: AbortSignal) => get<LogType>(`parser-log-types/${id(type)}`, signal),
  createLogType: (body: { logType: string; displayName: string; description?: string }) => post<LogType>('parser-log-types', body),
  releases: (type: string, signal?: AbortSignal) => get<ParserRelease[]>(`parser-log-types/${id(type)}/releases`, signal),
  release: (release: string, signal?: AbortSignal) => get<ParserRelease>(`parser-releases/${id(release)}`, signal),
  draft: (type: string, body: ReleaseRequest) => post<ParserRelease>(`parser-log-types/${id(type)}/releases`, body),
  validate: (release: string) => post<ReleaseValidation>(`parser-releases/${id(release)}/validations`),
  publish: (release: string) => post<ParserRelease>(`parser-releases/${id(release)}/publications`),
  active: (type: string, signal?: AbortSignal) => get<{ logType: string; releaseId: string | null }>(`parser-log-types/${id(type)}/active-release`, signal),
  activate: (type: string, releaseId: string, expectedCurrentReleaseId: string | null) => deviceOpsApi.put<ParserRelease>(`parser-log-types/${id(type)}/active-release`, { releaseId, expectedCurrentReleaseId }).then(r => r.data),
  revoke: (type: string, expectedCurrentReleaseId: string) => deviceOpsApi.delete(`parser-log-types/${id(type)}/active-release`, { params: { expectedCurrentReleaseId } }).then(() => undefined),
  runtime: (signal?: AbortSignal) => get<RuntimeStatus>('parser-runtime/status', signal),
  tasks: (afterTaskId?: string, signal?: AbortSignal) => deviceOpsApi.get<ParseTask[]>('parse-tasks', { params: { limit: 20, afterTaskId }, signal }).then(r => r.data),
  task: (task: string, signal?: AbortSignal) => get<ParseTask>(`parse-tasks/${id(task)}`, signal),
  result: (task: string, signal?: AbortSignal) => get<ParseResultEnvelope>(`parse-tasks/${id(task)}/result`, signal),
  submit: (body: { logType: string; releaseId?: string; inputFormat: string; inputContent: string; mediaType: string; contextSnapshot: Record<string, unknown> }, idempotencyKey: string) => deviceOpsApi.post<Pick<ParseTask, 'taskId' | 'state' | 'releaseId' | 'coordinate'>>('parse-tasks', body, { headers: { 'Idempotency-Key': idempotencyKey } }).then(r => r.data),
  cancel: (task: string) => post<void>(`parse-tasks/${id(task)}/cancellations`),
  terminate: (task: string) => post<void>(`parse-tasks/${id(task)}/terminations`)
}
