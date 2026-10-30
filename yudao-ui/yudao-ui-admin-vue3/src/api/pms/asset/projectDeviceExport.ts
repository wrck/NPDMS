import request from '@/config/axios'

export interface ProjectSerialExportFilters {
  sn?: string
  name?: string
  productCode?: string
}

export const exportProjectDevices = (query: {
  projectId: number | string
  kind: 'equipment' | 'serials'
  filters?: ProjectSerialExportFilters
}): Promise<Blob> => request.download({
  baseURL: import.meta.env.VITE_BASE_URL || '/',
  url: `/api/v1/pms/projects/${encodeURIComponent(String(query.projectId))}/${query.kind}/export`,
  params: query.kind === 'serials' ? query.filters : undefined
})
