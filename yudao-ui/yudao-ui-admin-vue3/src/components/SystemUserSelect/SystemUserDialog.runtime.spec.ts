import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import Dialog from './SystemUserDialog.vue'
import {
  mount,
  passthrough,
  tableColumn
} from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
const api = vi.hoisted(() => ({ page: vi.fn(), resolve: vi.fn(), active: vi.fn() }))
vi.mock('@/api/system/user/selection', () => ({
  systemUserSelection: api,
  sameSystemUserId: (a: any, b: any) => a != null && b != null && String(a) === String(b)
}))
vi.mock('@/views/system/dept/components/DeptTreeSelect.vue', () => ({
  default: {
    setup: (_: any, { expose }: any) => {
      expose({ reset: () => undefined, setCurrent: () => undefined })
      return () => null
    }
  }
}))
vi.mock('@/utils/dict', () => ({
  DICT_TYPE: { COMMON_STATUS: 'status' },
  getIntDictOptions: () => []
}))
vi.mock('@/utils/formatTime', () => ({ dateFormatter: () => '' }))
const apps: any[] = []
const render = (multiple = true, options: Record<string, unknown> = {}) => {
  const selected = vi.fn()
  const page = mount(
    Dialog,
    { multiple, onSelected: selected, ...options },
    {
      Dialog: passthrough,
      ElRow: passthrough,
      ElCol: passthrough,
      ContentWrap: passthrough,
      ElForm: passthrough,
      ElFormItem: passthrough,
      ElTable: defineComponent({
        setup: (_, { expose, slots }) => {
          expose({ clearSelection: vi.fn(), toggleRowSelection: vi.fn() })
          return () => h('section', slots.default?.())
        }
      }),
      ElTableColumn: tableColumn,
      ElSelect: passthrough,
      ElOption: passthrough,
      ElInput: passthrough,
      Pagination: passthrough
    }
  )
  apps.push(page.app)
  return { ...page, state: (page.vm as any).$.setupState, selected }
}
beforeEach(() => {
  vi.clearAllMocks()
  api.page.mockResolvedValue({ list: [{ id: 1, nickname: '第一页' }], total: 20 })
  api.resolve.mockResolvedValue([])
})
afterEach(() => apps.splice(0).forEach((app) => app.unmount()))
it('retains off-page preselected users and excludes explicitly forbidden identifiers', async () => {
  const id = '2115387679367467008'
  api.resolve.mockResolvedValue([
    { id, nickname: '跨页已选' },
    { id: 3, nickname: '禁止' }
  ])
  const page = render()
  await page.state.open([id, 3], ['3'])
  page.state.confirmSelect()
  expect(page.selected).toHaveBeenCalledWith([{ id, nickname: '跨页已选' }], undefined)
  expect(api.active).not.toHaveBeenCalled()
})
it('retains management search status and pagination without lower-privilege fallback', async () => {
  const page = render()
  page.state.queryParams.status = 1
  page.state.queryParams.nickname = '张'
  page.state.queryParams.pageNo = 2
  await page.state.getList()
  expect(api.page).toHaveBeenCalledWith(
    expect.objectContaining({ status: 1, nickname: '张', pageNo: 2 })
  )
  api.page.mockRejectedValue(new Error('403'))
  await page.state.getList()
  expect(page.state.loadError).toContain('失败')
  expect(page.state.list).toEqual([])
  expect(api.active).not.toHaveBeenCalled()
})
it('supports confirming a single historical user outside the first page', async () => {
  api.resolve.mockResolvedValue([{ id: 99, nickname: '历史停用', status: 1 }])
  const page = render(false)
  await page.state.open([99])
  page.state.confirmSelect()
  expect(page.selected).toHaveBeenCalledWith(
    [{ id: 99, nickname: '历史停用', status: 1 }],
    undefined
  )
})

it('keeps selection and confirmation unavailable until delayed preselection resolves', async () => {
  let finish: (value: any) => void = () => undefined
  api.resolve.mockReturnValue(
    new Promise((resolve) => {
      finish = resolve
    })
  )
  const page = render(false),
    opening = page.state.open([99])
  for (let i = 0; i < 12; i++) await import('vue').then((v) => v.nextTick())
  expect(page.state.restoring).toBe(true)
  page.state.handleRowClick({ id: 1, nickname: '尝试新选' })
  page.state.confirmSelect()
  expect(page.selected).not.toHaveBeenCalled()
  finish([{ id: 99, nickname: '原选择' }])
  await opening
  expect(page.state.restoring).toBe(false)
  page.state.handleRowClick({ id: 1, nickname: '真正新选' })
  page.state.confirmSelect()
  expect(page.selected).toHaveBeenCalledWith([{ id: 1, nickname: '真正新选' }], undefined)
})
it('does not reapply old preselection after the user changes selection and searches another page', async () => {
  api.resolve.mockResolvedValue([{ id: 99, nickname: '原选择' }])
  const page = render(false)
  await page.state.open([99])
  page.state.handleRowClick({ id: 1, nickname: '新选择' })
  api.page.mockResolvedValue({ list: [{ id: 99, nickname: '原选择' }], total: 1 })
  await page.state.getList()
  page.state.confirmSelect()
  expect(page.selected).toHaveBeenCalledWith([{ id: 1, nickname: '新选择' }], undefined)
})

it('retains the user multi-selection after restore and subsequent searches', async () => {
  api.resolve.mockResolvedValue([{ id: 99, nickname: '原选择' }])
  const page = render(true)
  await page.state.open([99])
  page.state.handleSelectionChange([{ id: 1, nickname: '新选择' }])
  api.page.mockResolvedValue({ list: [{ id: 99, nickname: '原选择' }], total: 1 })
  await page.state.getList()
  page.state.confirmSelect()
  expect(page.selected).toHaveBeenCalledWith([{ id: 1, nickname: '新选择' }], undefined)
})
it('ignores late preselection after the dialog is reopened for another selection', async () => {
  let finish: (value: any) => void = () => undefined
  api.resolve
    .mockReturnValueOnce(
      new Promise((resolve) => {
        finish = resolve
      })
    )
    .mockResolvedValueOnce([{ id: 2, nickname: '新上下文' }])
  const page = render(false),
    first = page.state.open([99])
  for (let i = 0; i < 12; i++) await import('vue').then((v) => v.nextTick())
  page.state.dialogVisible = false
  await page.state.open([2])
  finish([{ id: 99, nickname: '旧上下文' }])
  await first
  page.state.confirmSelect()
  expect(page.selected).toHaveBeenCalledWith([{ id: 2, nickname: '新上下文' }], undefined)
})

it('retains staff-search defaults, creation time and intentional empty confirmation only when enabled', async () => {
  const page = render(true, { defaultStatus: 'all', showCreateTime: true, allowEmpty: true })
  await page.state.open([])
  expect(api.page).toHaveBeenLastCalledWith(
    expect.objectContaining({ status: undefined, createTime: [] })
  )
  page.state.queryParams.createTime = ['2026-10-01 00:00:00', '2026-10-29 23:59:59']
  page.state.queryParams.username = 'staff'
  page.state.queryParams.mobile = '138'
  page.state.queryParams.deptId = 8
  await page.state.getList()
  expect(api.page).toHaveBeenLastCalledWith(
    expect.objectContaining({
      createTime: ['2026-10-01 00:00:00', '2026-10-29 23:59:59'],
      username: 'staff',
      mobile: '138',
      deptId: 8
    })
  )
  page.state.resetQuery()
  for (let i = 0; i < 8; i++) await import('vue').then((v) => v.nextTick())
  expect(page.state.queryParams.status).toBeUndefined()
  expect(page.state.queryParams.createTime).toEqual([])
  page.state.confirmSelect()
  expect(page.selected).toHaveBeenCalledWith([], undefined)
  expect(api.active).not.toHaveBeenCalled()
})
it('keeps ordinary management defaults and refuses an empty confirmation', async () => {
  const page = render()
  await page.state.open([])
  expect(api.page).toHaveBeenLastCalledWith(expect.objectContaining({ status: 0 }))
  page.state.confirmSelect()
  expect(page.selected).not.toHaveBeenCalled()
})

it('preserves the staff dialog draft on cancel and reopen but emits only on confirmation', async () => {
  const page = render(true, { retainDraftOnReopen: true, allowEmpty: true, defaultStatus: 'all' })
  await page.state.open()
  const draft = [{ id: '2115387679367467008', nickname: '待确认员工' }]
  page.state.handleSelectionChange(draft)
  page.state.queryParams.username = 'staff'
  page.state.dialogVisible = false
  expect(page.selected).not.toHaveBeenCalled()
  await page.state.open()
  expect(page.state.selectedRows).toEqual(draft)
  expect(page.state.queryParams.username).toBe('staff')
  expect(page.selected).not.toHaveBeenCalled()
  page.state.confirmSelect()
  expect(page.selected).toHaveBeenLastCalledWith(draft, undefined)
  await page.state.open()
  page.state.handleSelectionChange([])
  page.state.confirmSelect()
  expect(page.selected).toHaveBeenLastCalledWith([], undefined)
})
