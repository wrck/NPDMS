import { beforeEach, describe, expect, it, vi } from 'vitest'
const mocks = vi.hoisted(() => ({ types: vi.fn(), create: vi.fn() }))
vi.mock('@/api/pms/platform/delivery', () => ({ getDeliveryTypes: mocks.types }))
vi.mock('@/components/DeliveryArtifact/uploadDeliveryFile', () => ({ createDeliveryUploadAttempt: mocks.create }))
import { prepareGeneratedSolutionHtml } from './saveGeneratedSolutionHtml'
describe('formal solution HTML owner binding and recovery', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.types.mockResolvedValue([{ typeCode: 'IMPLEMENTATION_PLAN', maxSizeBytes: 52428800 }])
    mocks.create.mockImplementation((owner, type, file, title, sourceKind) => ({ owner, type, file, title, sourceKind, referenceKey: 'stable' }))
  })
  it('rejects an unsaved solution before file creation', async () => {
    await expect(prepareGeneratedSolutionHtml(0, '<html/>', 'plan.html')).rejects.toThrow('请先保存')
    expect(mocks.create).not.toHaveBeenCalled()
  })
  it('binds the real solution root and generated content', async () => {
    const attempt = await prepareGeneratedSolutionHtml(42, '<html>snapshot</html>', 'plan.html')
    expect(attempt.owner).toEqual({ ownerModule: 'SOL', entityType: 'solution', entityId: 42 })
    expect(attempt.sourceKind).toBe('GENERATED')
    expect(attempt.file.type).toBe('text/html')
    expect(attempt.file.name).toBe('plan.html')
  })
  it('retains the original reference after a failed registration retry', async () => {
    const first = await prepareGeneratedSolutionHtml(42, '<html/>', 'plan.html')
    first.referenceId = 81
    expect(await prepareGeneratedSolutionHtml(42, '<html/>', 'plan.html', first)).toBe(first)
    expect(mocks.create).toHaveBeenCalledTimes(1)
  })
})
