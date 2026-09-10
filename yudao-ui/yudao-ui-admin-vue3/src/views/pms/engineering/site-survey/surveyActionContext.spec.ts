import { beforeEach, describe, expect, it, vi } from 'vitest'
vi.mock('@/api/pms/engineering/site-survey', () => ({ getSiteSurvey: vi.fn() }))
vi.mock('@/api/pms/asset/device', () => ({ getDevice: vi.fn() }))
import { getSiteSurvey } from '@/api/pms/engineering/site-survey'
import { getDevice } from '@/api/pms/asset/device'
import { loadSurveyActionContext } from './surveyActionContext'

describe('survey shortcuts preserve real source identities', () => {
  beforeEach(() => vi.resetAllMocks())
  it('uses the saved survey context for procurement without inventing quantities', async () => {
    vi.mocked(getSiteSurvey).mockResolvedValue({ id: 1, projectId: 7, name: '工勘', status: 0, formExtraValues: { extra_railTrayRequired: true } })
    const result = await loadSurveyActionContext(1, 'material')
    expect(result).toMatchObject({ projectId: 7, triggerSource: 'SITE_SURVEY', triggerRefId: 1 })
    expect(result).not.toHaveProperty('quantity')
  })
  it('revalidates a selected device and does not confuse AST deviceId with legacy equipmentId', async () => {
    vi.mocked(getSiteSurvey).mockResolvedValue({ id: 1, projectId: 7, name: '工勘', status: 0, formExtraValues: { extra_materialMatches: false, extra_selectedMaterials: [{ deviceId: 9, sn: 'SN1', reason: '接口不匹配' }] } })
    vi.mocked(getDevice).mockResolvedValue({ summary: { deviceId: 9, projectId: 7, sn: 'SN1', productCode: 'SOURCE-P1', productName: '来源产品' } } as any)
    const result = await loadSurveyActionContext(1, 'exchange', 'SN1')
    expect(result).toMatchObject({ materialCode: 'SOURCE-P1', materialName: '来源产品', reason: '接口不匹配' })
    expect(result).not.toHaveProperty('equipmentId')
    vi.mocked(getDevice).mockResolvedValue({ summary: { deviceId: 9, projectId: 8, sn: 'SN1' } } as any)
    await expect(loadSurveyActionContext(1, 'exchange', 'SN1')).rejects.toThrow('设备项目归属已变化')
  })
  it('does not launch a shortcut from a completed survey or a condition that is false', async () => {
    vi.mocked(getSiteSurvey).mockResolvedValue({ status: 3 })
    await expect(loadSurveyActionContext(1, 'material')).rejects.toThrow('可编辑')
    vi.mocked(getSiteSurvey).mockResolvedValue({ status: 0, formExtraValues: { extra_railTrayRequired: false } })
    await expect(loadSurveyActionContext(1, 'procurement')).rejects.toThrow('导轨')
  })
})
