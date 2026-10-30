import { beforeEach, describe, expect, it, vi } from 'vitest'
import { listMaterials } from '@/api/pms/platform/delivery'
import { resolveSummaryPreview } from './resolveSummaryPreview'

vi.mock('@/api/pms/platform/delivery', () => ({ listMaterials: vi.fn() }))

describe('summary preview authorization and identity', () => {
  const row = { category: 'OTHER', materialLocator: { materialId: '2116148100229378050', ownerModule: 'SOL',
    entityType: 'requirementAnalysis', entityId: '2116148100229378049', typeCode: 'TOPOLOGY' } }
  const file = { id: '2116148100229378050', status: 'ACTIVE', materialKind: 'FILE', fileArtifactId: '2116148100229378051',
    fileVersionNo: 2, fileBusinessKey: { ownerContext: 'PLT', objectType: 'DELIVERY_MATERIAL', objectId: 'SOL:requirementAnalysis:2116148100229378049', purposeCode: 'TOPOLOGY', referenceKey: 'ref' } }
  beforeEach(() => vi.resetAllMocks())
  it('resolves only the exact authorized material without losing long ID precision', async () => {
    vi.mocked(listMaterials).mockResolvedValue([file] as any)
    expect(await resolveSummaryPreview(row)).toEqual(file)
    expect(listMaterials).toHaveBeenCalledWith('SOL', 'requirementAnalysis', '2116148100229378049', 'TOPOLOGY')
  })
  it('rejects withdrawn, missing, and business-result materials', async () => {
    for (const candidate of [{ ...file, status: 'WITHDRAWN' }, { ...file, materialKind: 'BUSINESS_RESULT' }, { ...file, fileBusinessKey: undefined }, { ...file, id: 'other' }]) {
      vi.mocked(listMaterials).mockResolvedValue([candidate] as any)
      await expect(resolveSummaryPreview(row)).rejects.toThrow()
    }
  })
})
