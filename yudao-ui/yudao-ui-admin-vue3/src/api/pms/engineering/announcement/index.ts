import request from '@/config/axios'

export interface AnnouncementVO {
  id?: number
  code: string
  title: string
  announcementType?: string
  productModel?: string
  affectedVersions?: string
  publishDate?: string
  effectiveDate?: string
  expireDate?: string
  severity?: string
  content?: string
  handlingSuggestion?: string
  fileUrl?: string
  fileName?: string
  fileSize?: number
  fileChecksum?: string
  status?: number
  version?: number
  creatorUserId?: number
  remark?: string
  createTime?: string
}

const baseUrl = '/pms/eng-announcement'

export const getAnnouncementPage = (params: PageParam) =>
  request.get({ url: `${baseUrl}/page`, params })
export const getAnnouncement = (id: number) =>
  request.get({ url: `${baseUrl}/get`, params: { id } })
export const createAnnouncement = (data: AnnouncementVO) =>
  request.post({ url: `${baseUrl}/create`, data })
export const updateAnnouncement = (data: AnnouncementVO) =>
  request.put({ url: `${baseUrl}/update`, data })
export const deleteAnnouncement = (id: number) =>
  request.delete({ url: `${baseUrl}/delete`, params: { id } })
export const publishAnnouncement = (id: number) =>
  request.put({ url: `${baseUrl}/publish`, params: { id } })
export const disableAnnouncement = (id: number) =>
  request.put({ url: `${baseUrl}/disable`, params: { id } })
