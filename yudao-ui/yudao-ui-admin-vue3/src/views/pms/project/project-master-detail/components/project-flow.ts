import type { TaskNode } from '@/api/pms/project/task-workbench'

export interface ProjectFlowSelection {
  kind: 'stage' | 'task'
  stageCode: string
  taskId?: number | string
  /** 导航选中的任务快照：工作台返回前供头部即时填充，避免占位标题与详情两种格式跳变 */
  task?: TaskNode
}
