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
