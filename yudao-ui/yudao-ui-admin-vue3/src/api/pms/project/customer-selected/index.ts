import request from '@/config/axios'
import type { ProjectCreateReqVO, ProjectCreateRespVO } from '@/api/pms/project/projects'

export type CustomerSelectedProjectCreate = Omit<ProjectCreateReqVO, 'customerName' | 'parentId' | 'customerCode'> & {
  customerCode: string
}

export const createCustomerSelectedProject = (data: CustomerSelectedProjectCreate, idempotencyKey: string) =>
  request.post<ProjectCreateRespVO>({
    url: '/api/v1/pms/projects',
    data,
    headers: { 'Idempotency-Key': idempotencyKey }
  })

export interface CustomerCorrectionInspection {
  projectId: number
  version: number
  customerCode?: string
  customerName?: string
  canCorrect: boolean
  references: { source: string; label: string; count: number }[]
}
export interface CustomerCorrectionResult {
  projectId: number
  version: number
  customerId: number
  customerCode: string
  customerName: string
  changed: boolean
}
export const inspectCustomerCorrection = (id: number) =>
  request.get<CustomerCorrectionInspection>({ url: `/api/v1/pms/projects/${id}/customer` })
export const correctProjectCustomer = (id: number, version: number, customerCode: string,
  reason: string, idempotencyKey: string) => request.put<CustomerCorrectionResult>({
    url: `/api/v1/pms/projects/${id}/customer`, data: { customerCode, reason },
    headers: { 'If-Match': String(version), 'Idempotency-Key': idempotencyKey }
  })
