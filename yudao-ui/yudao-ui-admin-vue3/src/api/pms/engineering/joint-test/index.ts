import request from '@/config/axios'

export interface JointTestVO {
  id?: number
  projectId: number
  code: string
  testCase: string
  equipmentId?: number
  participants?: string
  testTime?: Date
  testerUserId?: number
  result?: string
  exceptionRecord?: string
  evidenceUrl?: string
  status?: number
  remark?: string
  version?: number
  createTime?: Date
}

const baseUrl = '/pms/eng-joint-test'

export const getJointTestPage = (params: PmsProjectPageParam) =>
  request.get({ url: `${baseUrl}/page`, params })
export const getJointTest = (id: number) =>
  request.get({ url: `${baseUrl}/get`, params: { id } })
export const createJointTest = (data: JointTestVO) =>
  request.post({ url: `${baseUrl}/create`, data })
export const updateJointTest = (data: JointTestVO) =>
  request.put({ url: `${baseUrl}/update`, data })
export const deleteJointTest = (id: number) =>
  request.delete({ url: `${baseUrl}/delete`, params: { id } })
export const startJointTest = (id: number) =>
  request.put({ url: `${baseUrl}/start`, params: { id } })
export const passJointTest = (id: number) =>
  request.put({ url: `${baseUrl}/pass`, params: { id } })
export const failJointTest = (id: number, exceptionRecord: string) =>
  request.put({ url: `${baseUrl}/fail`, params: { id, exceptionRecord } })
