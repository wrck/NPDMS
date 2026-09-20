import request from '@/config/axios'
import { customerConfirmationBaseUrl } from '@/api/pms/customerConfirmation'

/** 现场培训记录（ACC-01） */
export interface TrainingVO {
  printTemplateId?: number
  printRevisionId?: number
  printLayoutSnapshot?: string
  id?: number
  projectId?: number
  code?: string
  name?: string
  contactName?: string
  contactPhone?: string
  trainingTypes?: string
  trainingTime?: string
  trainerUserId?: number
  trainerName?: string
  traineeCount?: number
  content?: string
  status?: number
  tokenExpiresAt?: string
  skillRating?: string
  effectRating?: string
  satisfactionRating?: string
  confirmationTemplateId?: number
  confirmationRevisionId?: number
  confirmationFormRules?: string
  confirmationValues?: string
  signatureImageDataUrl?: string
  signOpinion?: string
  signConfirmerName?: string
  signTime?: string | number
  fileUrl?: string
  fileName?: string
  fileSize?: number
  fileChecksum?: string
  version?: number
  remark?: string
  createTime?: string
}

export interface TrainingIssueVO {
  id: number
  token: string
  signPath: string
  tokenExpiresAt: string
  fileUrl: string
  notice: string
}

const baseUrl = '/pms/imp-training'
export const downloadTrainingPdf = (id: number) =>
  request.download<Blob>({ url: `/api/v1/pms/training-records/${id}/pdf` })

export const getTrainingPage = (params: PageParam & Record<string, any>) =>
  request.get<{ list: TrainingVO[]; total: number }>({ url: `${baseUrl}/page`, params })
export const getTraining = (id: number) => request.get<TrainingVO>({ url: `${baseUrl}/get`, params: { id } })
export const createTraining = (data: TrainingVO) => request.post<number>({ url: `${baseUrl}/create`, data })
export const updateTraining = (data: TrainingVO) => request.put<boolean>({ url: `${baseUrl}/update`, data })
export const deleteTraining = (id: number) => request.delete<boolean>({ url: `${baseUrl}/delete`, params: { id } })
export const issueTraining = (id: number) => request.put<TrainingIssueVO>({ url: `${baseUrl}/issue`, params: { id } })
export const voidTraining = (id: number) => request.put<boolean>({ url: `${baseUrl}/void`, params: { id } })
export const generateTrainingFile = (id: number) =>
  request.post<string>({ url: `${baseUrl}/generate-file`, params: { id } })

/** 公开端（无鉴权，令牌 + 租户头） */
const tenantHeaders = (tenantId: string | number) => ({ 'tenant-id': String(tenantId) })
export interface TrainingPublicVO {
  code: string
  name: string
  trainingTypeLabels: string
  trainingTime: string
  trainerName: string
  content?: string
  tokenExpiresAt: string
  status: number
  signConfirmerName?: string
  signTime?: string | number
  skillRating?: string
  effectRating?: string
  satisfactionRating?: string
  confirmationTemplateId?: number
  confirmationRevisionId?: number
  confirmationFormRules?: string
  confirmationValues?: string
  signatureImageDataUrl?: string
  signOpinion?: string
}
export const inspectPublicTraining = (token: string, tenantId: string | number) =>
  request.get<TrainingPublicVO>({
    url: `/api/v1/pms/training-records/${encodeURIComponent(token)}`,
    baseURL: customerConfirmationBaseUrl(),
    headers: { ...tenantHeaders(tenantId), isToken: false }
  })
export const confirmPublicTraining = (token: string, tenantId: string | number, data: Record<string, any>) =>
  request.post<boolean>({
    url: `/api/v1/pms/training-records/${encodeURIComponent(token)}/confirm`,
    data,
    baseURL: customerConfirmationBaseUrl(),
    headers: { ...tenantHeaders(tenantId), isToken: false }
  })
