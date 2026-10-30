import { beforeEach, expect, it, vi } from 'vitest'
import request from '@/config/axios'
import { pmsUserSelectionSource, searchPmsUserOptions } from './index'
import { nextTick } from 'vue'
import SystemUserScoped from '@/components/SystemUserSelect/SystemUserScoped.vue'
import { systemUserSelection } from '@/api/system/user/selection'
import { mount, passthrough, textOf } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

vi.mock('@/config/axios', () => ({ default: { get: vi.fn() } }))
beforeEach(() => vi.clearAllMocks())

it('does not expand a scoped source into another directory when it has no resolver', async () => {
  const source = { key: 'test', search: vi.fn().mockResolvedValue({ list: [], total: 0 }) }
  expect(await systemUserSelection.scopedResolve(source, ['9'])).toEqual([])
  expect(request.get).not.toHaveBeenCalled()
  expect(source.search).not.toHaveBeenCalled()
  expect(await systemUserSelection.scoped(source, { keyword: '', pageNo: 1, pageSize: 20 })).toEqual({ list: [], total: 0 })
})

it('keeps a source-owned resolver authoritative and does not fall back after rejection', async () => {
  const source = { key: 'restricted', search: vi.fn(), resolve: vi.fn().mockRejectedValue(new Error('403')) }
  await expect(systemUserSelection.scopedResolve(source, ['9'])).rejects.toThrow('403')
  expect(request.get).not.toHaveBeenCalled()
})

it('resolves large multi-selections in bounded ID batches', async () => {
  vi.mocked(request.get).mockResolvedValue({ list: [], total: 0 })
  const source = pmsUserSelectionSource('multi')
  const ids = Array.from({ length: 101 }, (_, i) => String(i + 1))
  await systemUserSelection.scopedResolve(source, ids)
  expect(request.get).toHaveBeenCalledTimes(2)
  expect((vi.mocked(request.get).mock.calls[0][0] as any).params.userIds.split(',')).toHaveLength(100)
  expect((vi.mocked(request.get).mock.calls[1][0] as any).params.userIds).toBe('101')
})

it('uses only business selection search and preserves sparse options, totals and identifiers', async () => {
  const result = { list: [{ id: '2115387679367467008', nickname: '张三' }], total: 3 }
  vi.mocked(request.get).mockResolvedValueOnce(result)
  const params = { keyword: 'ZHANGSAN', pageNo: 2, pageSize: 1 }
  expect(await pmsUserSelectionSource('survey:7').search(params)).toBe(result)
  expect(request.get).toHaveBeenCalledWith({ url: '/api/v1/pms/user-selections', params })
  expect(pmsUserSelectionSource('survey:7').search).toBe(searchPmsUserOptions)
})

it('propagates authorization failure without falling back to a broader directory', async () => {
  vi.mocked(request.get).mockRejectedValueOnce(new Error('403'))
  await expect(searchPmsUserOptions({ keyword: 'zhangsan', pageNo: 1, pageSize: 20 })).rejects.toThrow('403')
  expect(request.get).toHaveBeenCalledTimes(1)
})

it('resolves only the original identifiers through the same scoped directory and does not query empty sets', async () => {
  const source = pmsUserSelectionSource('survey:7')
  const user = { id: '2115387679367467008', nickname: '张三' }
  vi.mocked(request.get).mockResolvedValueOnce({ list: [user], total: 1 })
  expect(await source.resolve!([user.id])).toEqual([user])
  expect(request.get).toHaveBeenCalledWith({ url: '/api/v1/pms/user-selections', params: {
    keyword: '', pageNo: 1, pageSize: 100, userIds: user.id
  } })
  expect(await source.resolve!([])).toEqual([])
  expect(request.get).toHaveBeenCalledTimes(1)
})

it('keeps a selected surveyor name visible before opening and outside the current search page', async () => {
  const current = { id: '2115387679367467008', nickname: '当前工勘人', username: 's001' }
  vi.mocked(request.get).mockResolvedValueOnce({ list: [current], total: 1 })
    .mockResolvedValueOnce({ list: [{ id: '8', nickname: '其他人' }], total: 21 })
  const changed = vi.fn()
  const page = mount(SystemUserScoped, {
    modelValue: current.id, scopedSource: pmsUserSelectionSource('survey:7'), 'onUpdate:modelValue': changed
  }, { ElSelect: passthrough, ElOption: passthrough })
  try {
    for (let i = 0; i < 8; i++) { await Promise.resolve(); await nextTick() }
    const state = (page.vm as any).$.setupState
    expect(state.labelFor(current.id)).toBe('s001-当前工勘人')
    await state.open(true)
    for (let i = 0; i < 8; i++) { await Promise.resolve(); await nextTick() }
    expect(state.labelFor(current.id)).toBe('s001-当前工勘人')
    expect(state.historicalIds).toEqual([current.id])
    expect(textOf(page.root)).not.toContain('当前不可选')
    expect(changed).not.toHaveBeenCalled()
  } finally { page.app.unmount() }
})
