import { beforeEach, describe, expect, it, vi } from 'vitest'

const getTenantList = vi.hoisted(() => vi.fn())
vi.mock('@/api/system/tenant', () => ({ getTenantList }))
vi.mock('vue', async (importOriginal) => ({
  ...(await importOriginal<typeof import('vue')>()),
  onMounted: vi.fn()
}))

describe('tenant-count based selection', () => {
  beforeEach(() => {
    vi.resetModules()
    getTenantList.mockReset()
    vi.stubEnv('VITE_APP_TENANT_ENABLE', 'true')
  })

  it('selects the only tenant once and hides the selector', async () => {
    const options = [{ id: 1, name: '默认租户' }]
    getTenantList.mockResolvedValue(options)
    const { useTenantSelection } = await import('./useTenantSelection')
    const select = vi.fn()
    const state = useTenantSelection(select)
    await Promise.all([state.initializeTenantSelection(), state.initializeTenantSelection()])
    expect(state.showTenantSelector.value).toBe(false)
    expect(select).toHaveBeenCalledExactlyOnceWith(options[0], options)
    expect(getTenantList).toHaveBeenCalledTimes(1)
  })

  it('defaults to the first returned tenant rather than a hardcoded ID', async () => {
    const options = [
      { id: 9, name: '首个租户' },
      { id: 1, name: '第二租户' }
    ]
    getTenantList.mockResolvedValue(options)
    const { useTenantSelection } = await import('./useTenantSelection')
    const select = vi.fn()
    const state = useTenantSelection(select)
    await state.initializeTenantSelection()
    expect(state.showTenantSelector.value).toBe(true)
    expect(select).toHaveBeenCalledExactlyOnceWith(options[0], options)
  })

  it('does not invent a tenant when the directory is empty', async () => {
    getTenantList.mockResolvedValue([])
    const { useTenantSelection } = await import('./useTenantSelection')
    const select = vi.fn()
    const state = useTenantSelection(select)
    await state.initializeTenantSelection()
    expect(select).not.toHaveBeenCalled()
    expect(state.showTenantSelector.value).toBe(false)
  })

  it('allows retry after a failed directory request', async () => {
    getTenantList
      .mockRejectedValueOnce(new Error('unavailable'))
      .mockResolvedValueOnce([{ id: 1, name: '默认' }])
    const { useTenantSelection } = await import('./useTenantSelection')
    const select = vi.fn()
    const state = useTenantSelection(select)
    await expect(state.initializeTenantSelection()).rejects.toThrow('unavailable')
    expect(select).not.toHaveBeenCalled()
    await state.initializeTenantSelection()
    expect(select).toHaveBeenCalledTimes(1)
  })

  it('keeps the existing explicit tenant-disabled mode', async () => {
    vi.stubEnv('VITE_APP_TENANT_ENABLE', 'false')
    const { useTenantSelection } = await import('./useTenantSelection')
    const state = useTenantSelection(vi.fn())
    await state.initializeTenantSelection()
    expect(state.showTenantSelector.value).toBe(false)
    expect(getTenantList).not.toHaveBeenCalled()
  })
})
