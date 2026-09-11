export interface ProjectFlowSelection {
  kind: 'stage' | 'task'
  stageCode: string
  taskId?: number
}
