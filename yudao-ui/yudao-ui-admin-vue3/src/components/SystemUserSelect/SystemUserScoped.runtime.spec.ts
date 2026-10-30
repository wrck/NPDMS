vi.mock('@/api/system/user', () => ({
  getSimpleUserList: vi.fn(),
  getUserPage: vi.fn(),
  getUserList: vi.fn()
}))
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { nextTick, reactive, defineComponent, h } from 'vue'
import Scoped from './SystemUserScoped.vue'
import { systemUserSelection } from '@/api/system/user/selection'
import { mount, passthrough, textOf, findByTestId } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
const apps: any[] = []
const flush = async () => {
  for (let i = 0; i < 10; i++) await nextTick()
}
const source = () =>
  reactive({
    key: 'project-1-role-engineer',
    search: vi.fn().mockResolvedValue({ list: [{ id: 1, nickname: '获资格人员' }], total: 1 }),
    resolve: vi.fn().mockResolvedValue([])
  })
const render = (scope: any, extra: Record<string, unknown> = {}) => {
  const update = vi.fn(),
    change = vi.fn()
  const page = mount(
    Scoped,
    { scopedSource: scope, 'onUpdate:modelValue': update, onChange: change, ...extra },
    { ElSelect: passthrough, ElOption: passthrough }
  )
  apps.push(page.app)
  return { ...page, state: (page.vm as any).$.setupState, update, change }
}
beforeEach(() => vi.restoreAllMocks())
afterEach(() => {
  apps.splice(0).forEach((app) => app.unmount())
  vi.useRealTimers()
})
it('uses only the authorized source for empty or denied results', async () => {
  const scope = source(),
    active = vi.spyOn(systemUserSelection, 'active')
  scope.search.mockResolvedValue({ list: [], total: 0 })
  const page = render(scope, { modelValue: '9' })
  await page.state.load(1)
  expect(page.state.rows).toEqual([])
  scope.search.mockRejectedValue(new Error('403'))
  await page.state.load(1)
  expect(page.state.error).toContain('失败')
  expect(active).not.toHaveBeenCalled()
  expect(page.update).not.toHaveBeenCalled()
})
it('keeps resolved historical names read-only rather than adding them as qualified candidates', async () => {
  const scope = source()
  scope.resolve.mockResolvedValue([{ id: '2115387679367467008', nickname: '历史人员' }])
  const page = render(scope, { modelValue: '2115387679367467008' })
  await flush()
  await page.state.load(1)
  expect(page.state.historicalIds).toEqual(['2115387679367467008'])
  expect(page.state.labelFor('2115387679367467008')).toBe('历史人员')
  expect(page.state.rows.map((u: any) => u.id)).toEqual([1])
  expect(page.update).not.toHaveBeenCalled()
})
it('keeps the selected ID type on a qualified option so ElSelect can display its name', async () => {
  const scope = source()
  scope.search.mockResolvedValue({ list: [{ id: 9, nickname: '当前工勘人' }], total: 1 })
  scope.resolve.mockResolvedValue([{ id: 9, nickname: '当前工勘人' }])
  const page = render(scope, { modelValue: '9' })
  await flush()
  await page.state.load(1)
  expect(page.state.optionValue(9)).toBe('9')
  expect(page.state.historicalIds).toEqual([])
  expect(page.state.labelFor('9')).toBe('当前工勘人')
  expect(page.update).not.toHaveBeenCalled()
})
it('preserves search/page inputs and appends pages without duplicates', async () => {
  const scope = source()
  scope.search
    .mockResolvedValueOnce({ list: [{ id: 1, nickname: '一' }], total: 2 })
    .mockResolvedValueOnce({
      list: [
        { id: 1, nickname: '一' },
        { id: 2, nickname: '二' }
      ],
      total: 2
    })
  const page = render(scope)
  page.state.search('张')
  await flush()
  page.state.more()
  await flush()
  expect(scope.search).toHaveBeenNthCalledWith(2, { keyword: '张', pageNo: 2, pageSize: 20 })
  expect(page.state.rows).toHaveLength(2)
})
it('isolates late responses when project or role scope changes', async () => {
  const scope = source()
  let finish: (value: any) => void = () => undefined
  scope.search.mockReturnValueOnce(
    new Promise((resolve) => {
      finish = resolve
    })
  )
  const page = render(scope)
  page.state.load(1)
  scope.key = 'project-2-role-manager'
  await flush()
  finish({ list: [{ id: 7, nickname: '旧范围' }], total: 1 })
  await flush()
  expect(page.state.rows).toEqual([])
  expect(page.state.pageNo).toBe(0)
})
it('does not query or submit while disabled', async () => {
  const scope = source(),
    page = render(scope, { disabled: true })
  page.state.open(true)
  page.state.update(1)
  await flush()
  expect(scope.search).not.toHaveBeenCalled()
  expect(page.update).not.toHaveBeenCalled()
})

it('preserves legacy name storage while selecting the exact duplicate-name user by ID', async () => {
  const scope = source()
  scope.search.mockResolvedValue({
    list: [
      { id: 1, nickname: '同名' },
      { id: 2, nickname: '同名' }
    ],
    total: 2
  })
  const page = render(scope, { modelValue: '同名', valueField: 'nickname' })
  await flush()
  expect(scope.resolve).not.toHaveBeenCalled()
  expect(page.state.selectModel).toContain('__historical_name__')
  await page.state.load(1)
  page.state.update(2)
  expect(page.update).toHaveBeenCalledWith('同名')
  expect(page.change).toHaveBeenCalledWith({ id: 2, nickname: '同名' })
  expect(page.state.nameSelection.id).toBe(2)
  expect(page.state.selectModel).toBe(2)
})

it('refreshes an open candidate list when its authorized source changes asynchronously', async () => {
  const scope = source()
  scope.search.mockResolvedValue({ list: [], total: 0 })
  const page = render(scope)
  page.state.open(true)
  await flush()
  expect(page.state.rows).toEqual([])
  scope.search.mockResolvedValue({ list: [{ id: 8, nickname: '异步资格人员' }], total: 1 })
  scope.key = 'project-1-role-engineer:8'
  await flush()
  expect(page.state.rows).toEqual([{ id: 8, nickname: '异步资格人员' }])
  expect(scope.search).toHaveBeenCalledTimes(2)
})
it('does not reload a changed source when the dropdown is closed or disabled', async () => {
  const scope = source(), page = render(scope)
  scope.key = 'closed-source'
  await flush()
  expect(scope.search).not.toHaveBeenCalled()
  page.state.open(true)
  await flush()
  page.state.open(false)
  scope.key = 'changed-after-close'
  await flush()
  expect(scope.search).toHaveBeenCalledTimes(1)
  const disabledScope = source(), disabled = render(disabledScope, { disabled: true })
  disabled.state.open(true)
  disabledScope.key = 'disabled-source'
  await flush()
  expect(disabledScope.search).not.toHaveBeenCalled()
})

it('automatically pages only at the open dropdown bottom and preserves account labels', async () => {
  const scope = source()
  let finish: (value: any) => void = () => undefined
  scope.search.mockResolvedValueOnce({ list: [{ id: 1, nickname: '项目人员', username: 'engineer' }], total: 2 })
    .mockReturnValueOnce(new Promise(resolve => { finish = resolve }))
  const page = render(scope, { optionLabel: 'nickname-account' })
  page.state.open(true)
  await flush()
  page.state.selectRef = { scrollbarRef: { wrapRef: { scrollHeight: 300, clientHeight: 100 } } }
  expect(page.state.labelFor(1)).toBe('项目人员（engineer）')
  page.state.popupScroll({ scrollTop: 20 })
  expect(scope.search).toHaveBeenCalledTimes(1)
  page.state.popupScroll({ scrollTop: 200 })
  page.state.popupScroll({ scrollTop: 200 })
  expect(scope.search).toHaveBeenCalledTimes(2)
  finish({ list: [{ id: 2, nickname: '下一页' }], total: 2 })
  await flush()
  expect(page.state.rows).toHaveLength(2)
  page.state.popupScroll({ scrollTop: 200 })
  expect(scope.search).toHaveBeenCalledTimes(2)
  expect(textOf(page.root)).not.toContain('加载更多人员')
})

it('keeps one paging status footer through idle, loading, end and retry states', async () => {
  const scope = source()
  let fail: (error: Error) => void = () => undefined
  scope.search.mockResolvedValueOnce({ list: [{ id: 1, nickname: '首屏' }], total: 2 })
    .mockReturnValueOnce(new Promise((_resolve, reject) => { fail = reject }))
    .mockResolvedValueOnce({ list: [{ id: 2, nickname: '末页' }], total: 2 })
  const select = defineComponent({ setup: (_, { slots }) => () => h('section', [slots.default?.(), slots.footer?.()]) })
  const page = mount(Scoped, { scopedSource: scope }, { ElSelect: select, ElOption: passthrough })
  apps.push(page.app)
  const state = (page.vm as any).$.setupState
  await state.load(1)
  const status = findByTestId(page.root, 'user-pagination-status')!
  expect(status).toBeTruthy()
  expect(textOf(status)).toContain('加载更多')
  state.more()
  await flush()
  expect(findByTestId(page.root, 'user-pagination-status')).toBe(status)
  expect(textOf(status)).toContain('正在加载')
  fail(new Error('network'))
  await flush()
  expect(findByTestId(page.root, 'user-pagination-status')).toBe(status)
  expect(textOf(status)).toContain('加载失败')
  state.retry()
  await flush()
  expect(findByTestId(page.root, 'user-pagination-status')).toBe(status)
  expect(textOf(status)).toContain('已全部加载')
  scope.search.mockResolvedValue({ list: [{ id: 1, nickname: '单页' }], total: 1 })
  state.search('单页')
  await flush()
  expect(findByTestId(page.root, 'user-pagination-status')).toBeUndefined()
})

it('coalesces initial open and identical remote search without blocking changed queries', async () => {
  const scope = source()
  let finish: (value: any) => void = () => undefined
  scope.search.mockReturnValueOnce(new Promise(resolve => { finish = resolve }))
    .mockResolvedValue({ list: [{ id: 2, nickname: '搜索结果' }], total: 1 })
  const page = render(scope)
  page.state.open(true)
  page.state.search('')
  expect(scope.search).toHaveBeenCalledTimes(1)
  finish({ list: [{ id: 1, nickname: '首屏' }], total: 1 })
  await flush()
  page.state.search('')
  expect(scope.search).toHaveBeenCalledTimes(1)
  page.state.search('搜索')
  await flush()
  expect(scope.search).toHaveBeenCalledTimes(2)
  expect(page.state.rows[0].id).toBe(2)
})
it('allows explicit retry after an identical first-page request failed', async () => {
  const scope = source()
  scope.search.mockRejectedValueOnce(new Error('network')).mockResolvedValue({ list: [{ id: 1, nickname: '恢复' }], total: 1 })
  const page = render(scope)
  page.state.open(true)
  await flush()
  page.state.retry()
  await flush()
  expect(scope.search).toHaveBeenCalledTimes(2)
  expect(page.state.rows[0].nickname).toBe('恢复')
})

it('reuses an in-flight query when typing away and back while only applying the latest result', async () => {
  const scope = source()
  let finish: (value: any) => void = () => undefined
  scope.search.mockReturnValueOnce(new Promise(resolve => { finish = resolve }))
    .mockResolvedValue({ list: [{ id: 2, nickname: '另一搜索' }], total: 1 })
  const page = render(scope)
  page.state.open(true)
  page.state.search('其他')
  page.state.search('')
  expect(scope.search).toHaveBeenCalledTimes(2)
  finish({ list: [{ id: 1, nickname: '原搜索' }], total: 1 })
  await flush()
  expect(page.state.rows.map((row: any) => row.id)).toEqual([1])
})

it('invalidates old options before remote debounce while retaining the selected label', async () => {
  vi.useFakeTimers()
  const scope = source()
  let finish: (value: any) => void = () => undefined
  scope.search.mockResolvedValueOnce({ list: [{ id: 1, nickname: '已选人员' }, { id: 2, nickname: '旧候选' }], total: 5 })
    .mockReturnValueOnce(new Promise(resolve => { finish = resolve }))
  const page = render(scope, { modelValue: 1 })
  await page.state.load(1)
  const capture = page.root.children[0].props?.onInput as (event: unknown) => void
  capture?.({ target: { tagName: 'INPUT', value: '新词' } })
  expect(scope.search).toHaveBeenCalledTimes(1)
  expect(page.state.rows).toEqual([])
  expect(page.state.total).toBe(0)
  expect(page.state.pageNo).toBe(0)
  expect(page.state.paginationVisible).toBe(false)
  expect(page.state.historicalIds).toEqual([1])
  expect(page.state.labelFor(1)).toBe('已选人员')
  page.state.update(2)
  expect(page.update).not.toHaveBeenCalled()
  await vi.advanceTimersByTimeAsync(299)
  expect(scope.search).toHaveBeenCalledTimes(1)
  await vi.advanceTimersByTimeAsync(1)
  expect(scope.search).toHaveBeenCalledTimes(2)
  expect(page.state.rows).toEqual([])
  finish({ list: [{ id: 3, nickname: '新候选' }], total: 1 })
  await flush()
  expect(page.state.rows.map((row: any) => row.id)).toEqual([3])
  page.state.update(3)
  expect(page.update).toHaveBeenCalledWith(3)
})

it('rejects a previous response arriving during the remote debounce window', async () => {
  vi.useFakeTimers()
  const scope = source()
  let finish: (value: any) => void = () => undefined
  scope.search.mockReturnValueOnce(new Promise(resolve => { finish = resolve }))
    .mockResolvedValue({ list: [{ id: 3, nickname: '新结果' }], total: 1 })
  const page = render(scope)
  page.state.open(true)
  const capture = page.root.children[0].props?.onInput as (event: unknown) => void
  capture?.({ target: { tagName: 'INPUT', value: '新词' } })
  finish({ list: [{ id: 2, nickname: '旧请求' }], total: 1 })
  await flush()
  expect(page.state.rows).toEqual([])
  await vi.advanceTimersByTimeAsync(300)
  expect(page.state.rows.map((row: any) => row.id)).toEqual([3])
})

it('keeps historical selections removable and rejects additions from invalidated rows', async () => {
  const scope = source()
  scope.search.mockResolvedValue({ list: [{ id: 2, nickname: '旧候选' }], total: 1 })
  const page = render(scope, { modelValue: [9], multiple: true })
  await page.state.load(1)
  const capture = page.root.children[0].props?.onInput as (event: unknown) => void
  capture?.({ target: { tagName: 'INPUT', value: '新词' } })
  page.state.update([9, 2])
  expect(page.update).not.toHaveBeenCalled()
  page.state.update([9])
  expect(page.update).toHaveBeenCalledWith([9])
  page.state.update([])
  expect(page.update).toHaveBeenLastCalledWith([])
})

it('reloads after typing away and back even if ElSelect suppresses its repeated remote query', async () => {
  vi.useFakeTimers()
  const scope = source()
  scope.search.mockResolvedValue({ list: [{ id: 1, nickname: '当前结果' }], total: 1 })
  const page = render(scope)
  page.state.search('')
  await flush()
  const capture = page.root.children[0].props?.onInput as (event: unknown) => void
  capture?.({ target: { tagName: 'INPUT', value: '临时输入' } })
  capture?.({ target: { tagName: 'INPUT', value: '' } })
  expect(page.state.rows).toEqual([])
  // A repeated query does not invoke remote-method in ElementPlus.
  await vi.advanceTimersByTimeAsync(300)
  expect(scope.search).toHaveBeenCalledTimes(2)
  expect(scope.search).toHaveBeenLastCalledWith({ keyword: '', pageNo: 1, pageSize: 20 })
  expect(page.state.rows.map((row: any) => row.id)).toEqual([1])
  expect(page.state.loading).toBe(false)
})

it('waits for composition to end and resumes even when its final text is unchanged', async () => {
  vi.useFakeTimers()
  const scope = source()
  const page = render(scope)
  await page.state.load(1)
  const capture = page.root.children[0].props?.onInput as (event: unknown) => void
  capture?.({ target: { tagName: 'INPUT', value: '中文' }, isComposing: true })
  await vi.advanceTimersByTimeAsync(300)
  expect(scope.search).toHaveBeenCalledTimes(1)
  const end = page.root.children[0].props?.onCompositionend as (event: unknown) => void
  end?.({ type: 'compositionend', target: { tagName: 'INPUT', value: '中文' } })
  await vi.advanceTimersByTimeAsync(300)
  expect(scope.search).toHaveBeenCalledTimes(2)
  expect(scope.search).toHaveBeenLastCalledWith({ keyword: '中文', pageNo: 1, pageSize: 20 })
  expect(page.state.loading).toBe(false)
})
