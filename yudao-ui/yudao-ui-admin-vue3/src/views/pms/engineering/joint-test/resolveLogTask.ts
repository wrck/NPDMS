import { getProjectPlan } from '@/api/pms/project/projects/projectPlan'
import { getProjectTasks } from '@/api/pms/project/task-workbench'

/** Use the effective PAGE binding, never the task name or a fixed template code. */
export const resolveJointLogTask = async (projectId: number | string) => {
  const plan = await getProjectPlan(projectId)
  const definitions = plan.effective.designer.tasks.filter(
    (task) => task.workBinding?.type === 'PAGE'
      && task.workBinding.parameters?.routePath === '/pms/engineering/execution/imp-joint-test'
  )
  if (definitions.length !== 1) throw new Error('JOINT_LOG_TASK_AMBIGUOUS')
  const definition = definitions[0]
  let cursor: string | undefined
  do {
    const page = await getProjectTasks(projectId, { mode: 'LOCATE', keyword: definition.code, pageSize: 100, cursor })
    const matches = page.rows.filter(
      (task) => !task.placeholder && task.taskCode === definition.code && task.stageCode === definition.stageCode
    )
    if (matches.length === 1) return matches[0].taskId
    if (matches.length > 1) throw new Error('JOINT_LOG_TASK_AMBIGUOUS')
    cursor = page.nextCursor
  } while (cursor)
  throw new Error('JOINT_LOG_TASK_UNAVAILABLE')
}
