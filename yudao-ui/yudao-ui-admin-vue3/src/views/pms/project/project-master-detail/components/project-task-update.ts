import type { TaskDetail, TaskUpdateCommand } from '@/api/pms/project/task-workbench'

export interface TaskEditForm {
  name: string
  businessLevelCode: string
  planStartTime?: string
  planEndTime?: string
  priority?: number
  sortOrder?: number
  description: string
}

export interface TaskEditSnapshot {
  name: string
  businessLevelCode: string | null
  planStartTime: string | null
  planEndTime: string | null
  priority: number | null
  sortOrder: number | null
  description: string | null
}

const optionalText = (value?: string) => value?.trim() || null

export const snapshotTaskEdit = (task: TaskDetail): TaskEditSnapshot => ({
  name: task.name?.trim() || '',
  businessLevelCode: optionalText(task.businessLevelCode),
  planStartTime: task.planStartTime || null,
  planEndTime: task.planEndTime || null,
  priority: task.priority ?? null,
  sortOrder: task.sortOrder ?? null,
  description: optionalText(task.description)
})

export const buildTaskUpdatePayload = (
  original: TaskEditSnapshot,
  form: TaskEditForm
): TaskUpdateCommand => {
  const current: TaskEditSnapshot = {
    name: form.name.trim(),
    businessLevelCode: optionalText(form.businessLevelCode),
    planStartTime: form.planStartTime || null,
    planEndTime: form.planEndTime || null,
    priority: form.priority ?? null,
    sortOrder: form.sortOrder ?? null,
    description: optionalText(form.description)
  }
  const payload: TaskUpdateCommand = {}
  if (current.name !== original.name) payload.name = current.name
  if (current.businessLevelCode !== original.businessLevelCode) {
    payload.businessLevelCode = current.businessLevelCode
  }
  if (current.planStartTime !== original.planStartTime)
    payload.planStartTime = current.planStartTime
  if (current.planEndTime !== original.planEndTime) payload.planEndTime = current.planEndTime
  if (current.priority !== original.priority) {
    if (current.priority == null) throw new Error('priority cannot be cleared')
    payload.priority = current.priority
  }
  if (current.sortOrder !== original.sortOrder) {
    if (current.sortOrder == null) throw new Error('sortOrder cannot be cleared')
    payload.sortOrder = current.sortOrder
  }
  if (current.description !== original.description) payload.description = current.description
  return payload
}
