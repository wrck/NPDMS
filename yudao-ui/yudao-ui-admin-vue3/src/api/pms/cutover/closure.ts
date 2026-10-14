import request, { baseUrl } from './request'
import type { WireLong, CutoverClosureContent, CutoverClosureView, LinkCutoverClosureManualResultRequest, CutoverResultProgress } from './types'

export const getCutoverClosure = (taskId: WireLong) =>
  request.get<CutoverClosureView>({ url: `${baseUrl}/${taskId}/closure` })

export const saveCutoverClosure = (command: { taskId: WireLong; taskVersion: WireLong; closureVersion: WireLong | null; data: CutoverClosureContent; idempotencyKey: string }) =>
  request.put<CutoverClosureView>({
    url: `${baseUrl}/${command.taskId}/closure`,
    data: command.data,
    headers: {
      ...(command.closureVersion === null ? {} : { 'If-Match': String(command.closureVersion) }),
      'X-Task-Version': String(command.taskVersion),
      'Idempotency-Key': command.idempotencyKey
    }
  })

export const linkCutoverClosureManualResult = (command: { taskId: WireLong; taskVersion: WireLong; closureVersion: WireLong; data: LinkCutoverClosureManualResultRequest; idempotencyKey: string }) =>
  request.post<CutoverClosureView>({
    url: `${baseUrl}/${command.taskId}/closure/actions/link-manual-result`, data: command.data,
    headers: { 'If-Match': String(command.closureVersion), 'X-Task-Version': String(command.taskVersion), 'Idempotency-Key': command.idempotencyKey }
  })

export const submitCutoverClosure = (command: { taskId: WireLong; taskVersion: WireLong; closureVersion: WireLong; finalResult: 'SUCCESS' | 'FAILED'; idempotencyKey: string }) =>
  request.post<CutoverClosureView>({
    url: `${baseUrl}/${command.taskId}/closure/actions/submit`, data: { finalResult: command.finalResult },
    headers: { 'If-Match': String(command.closureVersion), 'X-Task-Version': String(command.taskVersion), 'Idempotency-Key': command.idempotencyKey }
  })

export const getResultProgress = (taskId: WireLong) => request.get<CutoverResultProgress>({ url: `${baseUrl}/${taskId}/result-progress` })

export const getProjectCoverage = (projectId: WireLong) => request.get<CutoverResultProgress['projectCoverage']>({ url: `${baseUrl}/project-coverage`, params: { projectId } })

export const linkClosureExecution = (command: {
  taskId: WireLong; taskVersion: WireLong; closureVersion: WireLong; entry: string; executionId: WireLong; idempotencyKey: string
}) => request.post<CutoverClosureView>({
  url: `${baseUrl}/${command.taskId}/closure/actions/link-collection-execution`,
  data: { entry: command.entry, executionId: command.executionId },
  headers: { 'X-Task-Version': String(command.taskVersion), 'If-Match': String(command.closureVersion), 'Idempotency-Key': command.idempotencyKey }
})
