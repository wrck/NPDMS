import request, { writeHeaders } from './request'
import type { WireLong, TaskSummary } from './tasks'

export const action = (input: {
  taskId: WireLong
  action: 'start-offline' | 'start-online' | 'fetch-result' | 'complete-execution' | 'adopt-reports'
  version: WireLong
  key: string
  body?: { explanation?: string; executionId?: WireLong }
}) =>
  request.post<TaskSummary>({
    url: `/api/v1/pms/inspection-tasks/${input.taskId}/umc/actions/${input.action}`,
    data: input.body || {},
    timeout: 195000,
    headers: writeHeaders(input.key, input.version)
  })
