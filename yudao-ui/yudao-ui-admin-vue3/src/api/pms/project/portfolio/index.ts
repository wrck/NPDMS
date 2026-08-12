import request from '@/config/axios'

export interface PortfolioRuleVO {
  id?: number
  ruleField: string
  ruleOperator: string
  ruleValue: string
}

export interface PortfolioVO {
  id?: number
  code: string
  name: string
  purpose?: string
  ownerUserId?: number
  validFrom?: string
  validTo?: string
  status: number
  targetMetrics?: string
  memberType: string
  staticProjectIds?: number[]
  rules?: PortfolioRuleVO[]
  memberCount?: number
  version?: number
  createTime?: Date
}

export interface PortfolioMemberVO {
  id?: number
  portfolioId?: number
  projectId: number
  projectCode?: string
  projectName?: string
  inclusionType?: string
  inclusionReason?: string
  exclusionReason?: string
  status?: number
  version?: number
  createTime?: Date
}

const baseUrl = '/pms/portfolio'

export const getPortfolioPage = (params: PageParam) =>
  request.get({ url: `${baseUrl}/page`, params })
export const getPortfolio = (id: number) =>
  request.get({ url: `${baseUrl}/get`, params: { id } })
export const createPortfolio = (data: PortfolioVO) =>
  request.post({ url: `${baseUrl}/create`, data })
export const updatePortfolio = (data: PortfolioVO) =>
  request.put({ url: `${baseUrl}/update`, data })
export const deletePortfolio = (id: number) =>
  request.delete({ url: `${baseUrl}/delete`, params: { id } })
export const publishPortfolio = (id: number) =>
  request.post({ url: `${baseUrl}/publish`, params: { id } })
export const recalculatePortfolio = (id: number) =>
  request.post({ url: `${baseUrl}/recalculate`, params: { id } })
export const getPortfolioMembers = (portfolioId: number) =>
  request.get({ url: `${baseUrl}/members`, params: { portfolioId } })
