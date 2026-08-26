import request from '@/config/axios'
import { service } from '@/config/axios/service'

export type TaskAction = 'START' | 'SUBMIT' | 'COMPLETE' | 'CANCEL'
export type TaskTreeMode =
  | 'DIRECT_CHILDREN'
  | 'ALL_DESCENDANTS'
  | 'ANCESTOR_CHAIN'
  | 'BUSINESS_LEVEL'
  | 'LOCATE'

export interface StageTaskNavigation {
  stageCode: string
  stageName: string
  stageStatus: string
  taskCount: number
}

export interface ProjectWorkspace {
  projectId: number
  projectCode: string
  projectName: string
  overviewTabs: string[]
  stageTaskNavigation: StageTaskNavigation[]
  taskTreeVersion: number
  projectionWatermark: string
  allowedActions: string[]
}

export interface TaskNode {
  taskId: number
  projectId?: number
  parentTaskId?: number
  rootTaskId?: number
  treeDepth: number
  placeholder: boolean
  taskCode?: string
  name?: string
  stageCode?: string
  businessLevelCode?: string
  status?: string
  priority?: number
  sortOrder?: number
  progress?: number
  planStartTime?: string
  planEndTime?: string
  assigneeUserId?: number
  description?: string
  version?: number
  children?: TaskNode[]
}

export interface TaskTreeQuery {
  mode?: TaskTreeMode
  parentTaskId?: number
  taskId?: number
  businessLevelCode?: string
  keyword?: string
  cursor?: string
  pageSize?: number
}

export interface CursorResult<T> {
  rows: T[]
  nextCursor?: string
  taskTreeVersion: number
  projectionWatermark?: string
}

export interface TaskDetail extends TaskNode {
  estimatedHours?: number
  actualStartTime?: string
  actualEndTime?: string
}

export interface TaskWorkbench {
  task: TaskDetail
  executionContractId?: number
  contractVersion?: number
  bindingType?: string
  trustedTargetRef?: string
  allowedActions: string[]
  factVersion?: string
  recoverableError?: string
}

export interface TaskCommandResult {
  taskId: number
  taskVersion: number
  taskTreeVersion: number
  status: string
  replayDecision: string
}

export interface TaskAssigneeCandidate {
  userId: number
  username: string
  nickname: string
  employeeNo?: string
  companyId?: number
  departmentId?: number
  departmentCode?: string
  departmentName?: string
}

export interface CandidatePageQuery {
  pageNo: number
  pageSize: number
  keyword?: string
}

export interface TaskCreateCommand {
  taskCode: string
  name: string
  stageCode: string
  parentTaskId?: number
  businessLevelCode?: string
  planStartTime?: string
  planEndTime?: string
  priority?: number
  sortOrder?: number
  description?: string
}

export interface TaskMoveCommand {
  targetParentTaskId?: number
  expectedTaskTreeVersion: number
  reason: string
}

export interface TaskActionCommand {
  reason?: string
  executionContractId?: number
  contractVersion?: number
  factObjectKey?: string
  factVersion?: number
}

const baseUrl = '/api/v1/pms'

const patchTask = async <T>(url: string, data: unknown, version: number) => {
  const response = await service({
    url,
    method: 'PATCH',
    data,
    headers: { 'Content-Type': 'application/json', 'If-Match': String(version) }
  })
  return response.data as T
}

export const getProjectWorkspace = (projectId: number) =>
  request.get<ProjectWorkspace>({ url: `${baseUrl}/projects/${projectId}/workspace` })

export const getProjectTasks = (projectId: number, params: TaskTreeQuery) =>
  request.get<CursorResult<TaskNode>>({ url: `${baseUrl}/projects/${projectId}/tasks`, params })

export const getTaskWorkbench = (taskId: number) =>
  request.get<TaskWorkbench>({ url: `${baseUrl}/project-tasks/${taskId}/workbench` })

export const getTaskAssigneeCandidates = (taskId: number, params: CandidatePageQuery) =>
  request.get<PageResult<TaskAssigneeCandidate>>({
    url: `${baseUrl}/project-tasks/${taskId}/assignee-candidates`,
    params
  })

export const createTask = (projectId: number, data: TaskCreateCommand, idempotencyKey: string) =>
  request.post<TaskCommandResult>({
    url: `${baseUrl}/projects/${projectId}/tasks`,
    data,
    headers: { 'Idempotency-Key': idempotencyKey }
  })

export const updateTask = (taskId: number, data: Partial<TaskDetail>, version: number) =>
  patchTask<TaskCommandResult>(`${baseUrl}/project-tasks/${taskId}`, data, version)

export const updateTaskProgress = (taskId: number, progress: number, version: number) =>
  patchTask<TaskCommandResult>(`${baseUrl}/project-tasks/${taskId}`, { progress }, version)

export const moveTask = (
  taskId: number,
  data: TaskMoveCommand,
  version: number,
  idempotencyKey: string
) =>
  request.post<TaskCommandResult>({
    url: `${baseUrl}/project-tasks/${taskId}/actions/move`,
    data,
    headers: { 'Idempotency-Key': idempotencyKey, 'If-Match': String(version) }
  })

export const assignTask = (
  taskId: number,
  assigneeUserId: number,
  reason: string,
  version: number,
  idempotencyKey: string
) =>
  request.post<TaskCommandResult>({
    url: `${baseUrl}/project-tasks/${taskId}/actions/assign`,
    data: { assigneeUserId, reason },
    headers: { 'Idempotency-Key': idempotencyKey, 'If-Match': String(version) }
  })

export const executeTaskAction = (
  taskId: number,
  action: TaskAction,
  command: TaskActionCommand,
  version: number,
  idempotencyKey: string
) =>
  request.post<TaskCommandResult>({
    url: `${baseUrl}/project-tasks/${taskId}/actions/${action.toLowerCase()}`,
    data: command,
    headers: { 'Idempotency-Key': idempotencyKey, 'If-Match': String(version) }
  })
