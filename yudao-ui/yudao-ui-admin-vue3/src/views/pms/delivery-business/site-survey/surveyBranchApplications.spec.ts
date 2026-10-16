import { beforeEach, expect, it, vi } from 'vitest'
const apis = vi.hoisted(() => ({ exchange: vi.fn(), material: vi.fn(), procurement: vi.fn() }))
vi.mock('@/api/pms/engineering/material-exch', () => ({ getMaterialExchangePage: apis.exchange, getMaterialExchange: vi.fn() }))
vi.mock('@/api/pms/engineering/material-req', () => ({ getMaterialRequisitionPage: apis.material, getMaterialRequisition: vi.fn() }))
vi.mock('@/api/pms/engineering/ext-proc', () => ({ getExternalProcurementPage: apis.procurement, getExternalProcurement: vi.fn() }))
vi.mock('@/api/pms/engineering/outsource', () => ({ getOutsourceRequest: vi.fn() }))
import { loadBranchApplications } from './surveyBranchApplications'
beforeEach(() => vi.resetAllMocks())
it('reads all project pages and only returns the exact saved survey references', async () => {
  apis.material.mockResolvedValueOnce({ total: 101, list: [
    { id: 1, projectId: 7, triggerSource: 'SITE_SURVEY', triggerRefId: '2108611480670629890' },
    { id: 2, projectId: 8, triggerSource: 'SITE_SURVEY', triggerRefId: '2108611480670629890' },
    { id: 3, projectId: 7, triggerSource: 'SITE_SURVEY', triggerRefId: '2108611480670629891' }
  ] }).mockResolvedValueOnce({ total: 101, list: [{ id: 4, projectId: 7, triggerSource: 'SITE_SURVEY', triggerRefId: '2108611480670629890' }] })
  expect((await loadBranchApplications('material', 7, '2108611480670629890')).map(row => row.id)).toEqual([1, 4])
  expect(apis.material).toHaveBeenLastCalledWith(expect.objectContaining({ projectId: 7, pageNo: 2, triggerSource: 'SITE_SURVEY' }))
})
it('matches exchange source notes exactly and excludes other surveys and projects', async () => {
  apis.exchange.mockResolvedValue({ total: 3, list: [
    { id: 1, projectId: 7, remark: '来源工勘 #12' },
    { id: 2, projectId: 7, remark: '来源工勘 #123' },
    { id: 3, projectId: 8, remark: '来源工勘 #12' }
  ] })
  expect((await loadBranchApplications('exchange', 7, 12)).map(row => row.id)).toEqual([1])
})

it('keeps exact project and source filtering when a default-business page reader is supplied', async () => {
  const readPage = vi.fn().mockResolvedValue({ total: 3, list: [
    { id: 1, projectId: 7, triggerSource: 'SITE_SURVEY', triggerRefId: '2108611480670629890' },
    { id: 2, projectId: 8, triggerSource: 'SITE_SURVEY', triggerRefId: '2108611480670629890' },
    { id: 3, projectId: 7, triggerSource: 'SITE_SURVEY', triggerRefId: '2108611480670629891' }
  ] })
  expect((await loadBranchApplications('procurement', 7, '2108611480670629890', readPage)).map(row => row.id)).toEqual([1])
  expect(readPage).toHaveBeenCalledWith({ projectId: 7, pageNo: 1, pageSize: 100, triggerSource: 'SITE_SURVEY' })
  expect(apis.procurement).not.toHaveBeenCalled()
})
