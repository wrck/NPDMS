import type { TaskSummary, TaskWorkbench, WireLong } from '@/api/pms/inspection/tasks'

export type WorkbenchTaskRow = Partial<TaskSummary> & {
  id: WireLong
  taskCode: string
  taskName: string
  issueId?: WireLong
}

export function workbenchTaskRows(input: {
  data: TaskWorkbench | null
  canQuery: boolean
  canReadIssues: boolean
}) {
  const pending = new Map<string, WorkbenchTaskRow>()
  const created = input.canQuery ? input.data?.createdTasks || [] : []
  for (const task of input.canQuery ? input.data?.pendingTasks || [] : []) {
    pending.set(String(task.id), task)
  }
  for (const task of created) {
    if (task.statusCode === 'TRACKING') pending.set(String(task.id), task)
  }
  const known = new Map(created.map((task) => [String(task.id), task]))
  for (const issue of input.canReadIssues ? input.data?.myIssues || [] : []) {
    if (issue.statusCode === 'CLOSED') continue
    if (pending.has(String(issue.taskId))) continue
    pending.set(
      String(issue.taskId),
      known.get(String(issue.taskId)) || {
        id: issue.taskId,
        taskCode: issue.taskCode,
        taskName: issue.taskName,
        // Preserve the authorized issue entry when the task itself is not in the readable task lists.
        issueId: issue.id
      }
    )
  }
  return {
    pending: [...pending.values()],
    created: created.filter((task) => !pending.has(String(task.id)))
  }
}
