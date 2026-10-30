import request from '@/config/axios'

export type ProjectSelectionId = string | number
export interface ProjectOption { id: ProjectSelectionId; code: string; name: string; customerName?: string }
export interface ProjectPresentation {
  projectId: ProjectSelectionId; projectCode: string; projectName: string; customerName?: string
  projectManagerName?: string; serviceManagerNames?: string; salesNames?: string
  departmentName?: string; industryName?: string; majorProjectLevel?: string
}
export interface ProjectContext { project: ProjectPresentation; customerServiceLevel?: string | null }
export interface ProjectDevice {
  deviceId: ProjectSelectionId; serialNumber: string; productCode?: string; productModel?: string
  currentSoftwareVersion?: string | null
}
export interface ProjectDeviceQuery { keyword?: string; pageNo: number; pageSize: number }
const base = '/api/v1/pms/projects'
export const searchProjectOptions = (keyword = '') =>
  request.get<ProjectOption[]>({ url: `${base}/options`, params: { keyword } })
export const getProjectContext = (projectId: ProjectSelectionId) =>
  request.get<ProjectContext>({ url: `${base}/${encodeURIComponent(String(projectId))}/context` })
export const getProjectDevicePage = (projectId: ProjectSelectionId, params: ProjectDeviceQuery) =>
  request.get<{ list: ProjectDevice[]; total: number }>({ url: `${base}/${encodeURIComponent(String(projectId))}/devices`, params })

/** 下拉多选消费同一分页契约；每次只读取服务端授权的当前项目范围。 */
export const getAllProjectDevices = async (projectId: ProjectSelectionId) => {
  const devices: ProjectDevice[] = []
  for (let pageNo = 1; ; pageNo++) {
    const page = await getProjectDevicePage(projectId, { pageNo, pageSize: 100 })
    devices.push(...page.list)
    if (!page.list.length || devices.length >= page.total) return devices
  }
}
