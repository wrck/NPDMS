import request from '@/config/axios'

export interface CompletionCertificateDeviceVO {
  id?: number
  deviceType?: string
  deviceModel?: string
  quantity: number
}

export interface CompletionCertificateVO {
  id?: number
  projectId: number
  code?: string
  name: string
  customerId?: number
  certificateNo?: string
  completionDate?: string
  serviceType?: string
  engineerUserId?: number
  engineerName?: string
  engineerContact?: string
  customerUnit?: string
  contractNo?: string
  itemArrival?: string
  itemInstall?: string
  itemCutover?: string
  itemTraining?: string
  itemDocs?: string
  customerSignUrl?: string
  customerSignDate?: string
  vendorSignUrl?: string
  vendorSignDate?: string
  devices?: CompletionCertificateDeviceVO[]
  content?: string
  attachmentUrl?: string
  status?: number
  remark?: string
  version?: number
  createTime?: Date
  satisfactionTemplateId?: number
  completionPrintTemplateId?: number
  completionPrintSnapshot?: string
  satisfactionPrintTemplateId?: number
  satisfactionPrintSnapshot?: string
}

export interface CompletionSurveyVO {
  taskId: number
  collectionKey: string
  revisionNo: number
  status: string
  questionnaireJson?: string
  answerSnapshot?: string
  submittedAt?: string
  signatureImageDataUrl?: string
  score?: number
  threshold?: number
  passed?: boolean
}
export interface CompletionConfirmationDetail {
  certificate: CompletionCertificateVO
  survey?: CompletionSurveyVO
  history: CompletionSurveyVO[]
}
export interface CompletionIssueVO { id: number; taskId: number; signPath: string; tokenExpiresAt: string }
const workflowUrl = '/api/v1/pms/completion-confirmations'
export const getConfirmationPage = (params: PmsProjectPageParam & { survey?: boolean }) => request.get({ url: workflowUrl, params })
export const getConfirmation = (id: number) => request.get<CompletionConfirmationDetail>({ url: `${workflowUrl}/${id}` })
export const saveConfirmation = (data: CompletionCertificateVO) => data.id
  ? request.put<number>({ url: `${workflowUrl}/${data.id}`, data }) : request.post<number>({ url: workflowUrl, data })
export const issueConfirmation = (id: number) => request.post<CompletionIssueVO>({ url: `${workflowUrl}/${id}/actions/issue` })
export const voidConfirmation = (id: number) => request.post({ url: `${workflowUrl}/${id}/actions/void` })
export const deleteConfirmation = (id: number) => request.delete({ url: `${workflowUrl}/${id}` })
export const saveConfirmationPdf = (id: number, expectedVersion: number, kind: 'COMPLETION' | 'SATISFACTION', file: File, key: string) => {
  const data = new FormData()
  data.append('file', file)
  return request.post<{ materialId: number }>({ url: `${workflowUrl}/${id}/documents`, params: { expectedVersion, kind }, data,
    headersType: 'multipart/form-data', headers: { 'Idempotency-Key': key } })
}
export const getRegisteredConfirmationPdf = ({ id, ...params }: {
  id: number; expectedVersion: number; kind: 'COMPLETION' | 'SATISFACTION'; layoutKey: string
}) => request.get<{ materialId: number } | null>({ url: `${workflowUrl}/${id}/documents`, params })
export const getConfirmationFileTicket = (id: number, materialId: number) => request.get<string>({ url: `${workflowUrl}/${id}/files/${materialId}` })

const baseUrl = '/pms/acc-completion-certificate'

export const getCompletionCertificatePage = (params: PmsProjectPageParam) =>
  request.get({ url: `${baseUrl}/page`, params })
export const getCompletionCertificate = (id: number) =>
  request.get({ url: `${baseUrl}/get`, params: { id } })
export const createCompletionCertificate = (data: CompletionCertificateVO) =>
  request.post({ url: `${baseUrl}/create`, data })
export const updateCompletionCertificate = (data: CompletionCertificateVO) =>
  request.put({ url: `${baseUrl}/update`, data })
export const deleteCompletionCertificate = (id: number) =>
  request.delete({ url: `${baseUrl}/delete`, params: { id } })
// 状态动作: 0草稿 1待客户确认 2客户已确认 3已归档 4已驳回
export const submitCompletionCertificate = (id: number) =>
  request.put({ url: `${baseUrl}/submit`, params: { id } })
export const customerConfirmCompletionCertificate = (id: number) =>
  request.put({ url: `${baseUrl}/customer-confirm`, params: { id } })
export const rejectCompletionCertificate = (id: number) =>
  request.put({ url: `${baseUrl}/reject`, params: { id } })
export const archiveCompletionCertificate = (id: number) =>
  request.put({ url: `${baseUrl}/archive`, params: { id } })
