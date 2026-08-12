import request from '@/config/axios'

export interface AnnouncementCheckVO {
  id?: number
  code: string
  projectId: number
  announcementId: number
  deviceId?: number
  deviceSerial?: string
  deviceModel?: string
  deviceVersion?: string
  matchResult?: string
  eomStatus?: string
  handlingSuggestion?: string
  status?: number
  version?: number
  checkerUserId?: number
  checkTime?: string
  handleOpinion?: string
  handleTime?: string
  creatorUserId?: number
  remark?: string
  createTime?: string
}

const baseUrl = '/pms/eng-announcement-check'

export const getAnnouncementCheckPage = (params: PageParam) =>
  request.get({ url: `${baseUrl}/page`, params })
export const getAnnouncementCheck = (id: number) =>
  request.get({ url: `${baseUrl}/get`, params: { id } })
export const createAnnouncementCheck = (data: AnnouncementCheckVO) =>
  request.post({ url: `${baseUrl}/create`, data })
export const updateAnnouncementCheck = (data: AnnouncementCheckVO) =>
  request.put({ url: `${baseUrl}/update`, data })
export const deleteAnnouncementCheck = (id: number) =>
  request.delete({ url: `${baseUrl}/delete`, params: { id } })
export const performCheck = (id: number) =>
  request.put({ url: `${baseUrl}/perform-check`, params: { id } })
export const handleCheck = (data: {
  id: number
  handleOpinion?: string
  handleAction?: string
  version?: number
}) => request.put({ url: `${baseUrl}/handle`, data })
