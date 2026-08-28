import request from '@/config/axios'

export interface CompanyVO {
  id?: number
  code: string
  name: string
  status: number
  version?: number
  expectedVersion?: number
  createTime?: Date
}

const baseUrl = '/system/companies'

export const getCompanyPage = (params: PageParam) => request.get({ url: `${baseUrl}/page`, params })
export const getCompany = (id: number) => request.get({ url: `${baseUrl}/get`, params: { id } })
export const getSimpleCompanyList = (): Promise<CompanyVO[]> =>
  request.get({ url: `${baseUrl}/simple-list` })
export const createCompany = (data: CompanyVO) => request.post({ url: `${baseUrl}/create`, data })
export const updateCompany = (data: CompanyVO) => request.put({ url: `${baseUrl}/update`, data })
