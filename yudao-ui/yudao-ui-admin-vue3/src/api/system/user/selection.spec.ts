import { beforeEach, expect, it, vi } from 'vitest'
import { systemUserSelection } from './selection'
const api = vi.hoisted(() => ({
  getSimpleUserList: vi.fn(),
  getUserPage: vi.fn(),
  getUserList: vi.fn()
}))
vi.mock('./index', () => api)
beforeEach(() => vi.clearAllMocks())
it('uses enabled candidates only for the existing ordinary selection contract', async () => {
  api.getSimpleUserList.mockResolvedValue([{ id: 1 }])
  expect(await systemUserSelection.active()).toEqual([{ id: 1 }])
  expect(api.getUserPage).not.toHaveBeenCalled()
})
it('preserves management filters and never falls back to a lower permission source after denial', async () => {
  const query = { pageNo: 2, pageSize: 10, status: 1, mobile: '139', deptId: 3 }
  api.getUserPage.mockRejectedValue(new Error('403'))
  await expect(systemUserSelection.page(query)).rejects.toThrow('403')
  expect(api.getUserPage).toHaveBeenCalledWith(query)
  expect(api.getSimpleUserList).not.toHaveBeenCalled()
})
it('resolves original identifiers without truncation or filtering historical users', async () => {
  const id = '2115387679367467008'
  api.getUserList.mockResolvedValue([{ id, status: 1 }])
  expect(await systemUserSelection.resolve([id])).toEqual([{ id, status: 1 }])
  expect(api.getUserList).toHaveBeenCalledWith([id])
})

it('uses only the supplied workflow candidate set for search, paging and historical resolution', async () => {
  const source = systemUserSelection.fromCandidates('bpm-task-1', [
    { id: 2, nickname: '合格甲' },
    { id: 3, nickname: '合格乙' }
  ])
  expect(
    await systemUserSelection.scoped(source, { keyword: '合格', pageNo: 2, pageSize: 1 })
  ).toEqual({ list: [{ id: 3, nickname: '合格乙' }], total: 2 })
  expect(await systemUserSelection.scopedResolve(source, [1, 2])).toEqual([
    { id: 2, nickname: '合格甲' }
  ])
  expect(api.getSimpleUserList).not.toHaveBeenCalled()
  expect(api.getUserPage).not.toHaveBeenCalled()
})

it('matches email pinyin within supplied candidates while retaining paging and original name search', async () => {
  const first = { id: 2, nickname: '张三甲', email: 'ZhangSan.one@example.test' }
  const second = { id: 3, nickname: '张三乙', email: 'zhangsan.two@example.test' }
  const source = systemUserSelection.fromCandidates('members', [
    first,
    second,
    { id: 4, nickname: '李四' }
  ])
  expect(await source.search({ keyword: ' ZHANGSAN ', pageNo: 2, pageSize: 1 })).toEqual({
    list: [second], total: 2
  })
  expect(await source.search({ keyword: 'zhangsan.one@example.test', pageNo: 1, pageSize: 20 })).toEqual({
    list: [first], total: 1
  })
  expect(await source.search({ keyword: '张三甲', pageNo: 1, pageSize: 20 })).toEqual({
    list: [first], total: 1
  })
  expect(await source.search({ keyword: 'unknown', pageNo: 1, pageSize: 20 })).toEqual({
    list: [], total: 0
  })
  expect(api.getSimpleUserList).not.toHaveBeenCalled()
  expect(api.getUserPage).not.toHaveBeenCalled()
})
