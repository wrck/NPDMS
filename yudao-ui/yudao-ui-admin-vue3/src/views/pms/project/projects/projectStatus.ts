export interface ProjectStatusSource {
  lifecycleStatus?: string
  currentStage?: string
  status?: string
}

export const closedProjectStatuses = [
  { value: 'NORMAL_CLOSED', label: '正常闭环', tone: 'green', icon: 'ep:circle-check' },
  { value: 'NO_TRACKING_CLOSED', label: '不予跟踪闭环', tone: 'gray', icon: 'ep:lock' },
  { value: 'EXCEPTION_CLOSED', label: '异常关闭', tone: 'yellow', icon: 'ep:warning' }
]

export function projectStatus(source?: ProjectStatusSource | null) {
  const lifecycle = source?.lifecycleStatus
  if (lifecycle && lifecycle !== 'ACTIVE') {
    return {
      value: lifecycle,
      label: closedProjectStatuses.find((item) => item.value === lifecycle)?.label || lifecycle,
      type: lifecycle === 'NORMAL_CLOSED' ? 'success' as const :
        lifecycle === 'EXCEPTION_CLOSED' ? 'warning' as const : 'info' as const,
      stage: false
    }
  }
  return { value: source?.currentStage || source?.status || '', stage: true }
}
