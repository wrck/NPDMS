import request, { baseUrl } from './request'
import type {
  WireLong, ChecklistFileHandle, CutoverChecklistView, CutoverChecklistCommandResult,
  CutoverChecklistDeviceVersion, CutoverExternalQuery, CutoverExternalFeedback,
  CutoverExternalState, CutoverChecklistMutation, CutoverChecklistMatchingOptions, CutoverChecklistRevision
} from './types'

export interface ChecklistWrite<T = CutoverChecklistMutation> { taskId: WireLong; data: T; idempotencyKey: string }
export interface ChecklistItemWrite<T = CutoverChecklistMutation> extends ChecklistWrite<T> { stableItemKey: string }
interface ChecklistVersions { expectedTaskVersion: WireLong; expectedChecklistVersion?: WireLong }
const headers = (command: ChecklistWrite<ChecklistVersions>) => ({
  'X-Task-Version': String(command.data.expectedTaskVersion),
  ...(command.data.expectedChecklistVersion == null ? {} : { 'If-Match': String(command.data.expectedChecklistVersion) }),
  'Idempotency-Key': command.idempotencyKey
})
const itemUrl = (command: ChecklistItemWrite<unknown>) =>
  `${baseUrl}/${command.taskId}/checklist/items/${encodeURIComponent(command.stableItemKey)}`

export const getCutoverChecklist = (taskId: WireLong, checklistId?: WireLong) =>
  request.get<CutoverChecklistView>({ url: `${baseUrl}/${taskId}/checklist`, params: { checklistId }, silentError: true })
export const getCutoverExternalState = (taskId: WireLong, checklistId?: WireLong) =>
  request.get<CutoverExternalState>({ url: `${baseUrl}/${taskId}/checklist/external-state`, params: { checklistId } })
export const getCutoverChecklistMatchingOptions = (taskId: WireLong, checklistId?: WireLong) =>
  request.get<CutoverChecklistMatchingOptions>({ url: `${baseUrl}/${taskId}/checklist/matching-options`, params: { checklistId } })
export const getCutoverChecklistRevisions = (taskId: WireLong) =>
  request.get<CutoverChecklistRevision[]>({ url: `${baseUrl}/${taskId}/checklist/revisions` })

export const saveCutoverDeviceVersions = (command: ChecklistWrite<CutoverChecklistMutation & { devices: CutoverChecklistDeviceVersion[] }>) =>
  request.put<WireLong>({ url: `${baseUrl}/${command.taskId}/checklist/device-versions`, data: command.data, headers: headers(command) })
export const loadCutoverExternal = (command: ChecklistItemWrite) =>
  request.post<{ checklistFactVersion: WireLong | null; selected: boolean; queries: CutoverExternalQuery[] }>({
    url: `${itemUrl(command)}/external-queries`, data: command.data, headers: headers(command)
  })
export const saveCutoverExternalFeedback = (command: ChecklistItemWrite<CutoverChecklistMutation & {
  expectedResultId: WireLong; feedback: CutoverExternalFeedback[]
}>) => request.put({ url: `${itemUrl(command)}/external-feedback`, data: command.data, headers: headers(command) })

export interface ChecklistMatchInput {
  expectedTaskVersion: WireLong; expectedAssessmentVersion: WireLong; expectedProjectScopeVersion: WireLong;
  matchingInput: { cutoverType: string; networkMode: string | null; deviceTypeCodes: string[] };
  selectedConflictDefinitions: Record<string, { itemDefinitionId: WireLong; itemDefinitionVersion: number }>
}
export const generateCutoverChecklist = (command: ChecklistWrite<ChecklistMatchInput>) =>
  request.post({ url: `${baseUrl}/${command.taskId}/checklist/actions/generate`, data: command.data, headers: headers(command) })
export const rematchCutoverChecklist = (command: ChecklistWrite<ChecklistMatchInput & CutoverChecklistMutation & { expectedInputSnapshotHash: string }>) =>
  request.post({ url: `${baseUrl}/${command.taskId}/checklist/actions/rematch`, data: command.data, headers: headers(command) })
export const saveCutoverChecklist = (command: ChecklistWrite<CutoverChecklistMutation & {
  answers: Array<{ stableItemKey: string; answerSnapshot: string }>
}>) => request.put({ url: `${baseUrl}/${command.taskId}/checklist`, data: command.data, headers: headers(command) })
export const addCustomChecklistItem = (command: ChecklistWrite<CutoverChecklistMutation & {
  itemTypeCode: string; itemName: string; itemDescription: string; interfaceFormatCode: string;
  interfaceSchema: string; required: boolean; answerSnapshot: string | null
}>) => request.post({ url: `${baseUrl}/${command.taskId}/checklist/custom-items`, data: command.data, headers: headers(command) })
export const removeCustomChecklistItem = (command: ChecklistItemWrite) =>
  request.delete({ url: `${baseUrl}/${command.taskId}/checklist/custom-items/${encodeURIComponent(command.stableItemKey)}`,
    data: command.data, headers: headers(command) })
export const requestChecklistCollection = (command: ChecklistItemWrite<CutoverChecklistMutation & {
  deviceId: WireLong; commandTemplateId: WireLong | null
}> & { executionId?: WireLong }) => request.post({
  url: `${itemUrl(command)}/collection-requests`, data: command.data,
  params: { executionId: command.executionId }, headers: headers(command)
})
export const saveManualChecklistResult = (command: ChecklistItemWrite<CutoverChecklistMutation & {
  file: ChecklistFileHandle; factDescription: string
}>) => request.post({ url: `${itemUrl(command)}/manual-results`, data: command.data, headers: headers(command) })
export const exportCutoverChecklist = (taskId: WireLong, checklistVersion: number) =>
  request.download<Blob>({ url: `${baseUrl}/${taskId}/checklist/actions/export`, method: 'POST', data: { checklistVersion } })
export const submitCutoverChecklist = (command: ChecklistWrite<CutoverChecklistMutation & { expectedAssessmentVersion: WireLong }>) =>
  request.post<CutoverChecklistCommandResult>({ url: `${baseUrl}/${command.taskId}/checklist/actions/submit`, data: command.data, headers: headers(command) })
