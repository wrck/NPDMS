// @vitest-environment happy-dom
import { afterEach, expect, it, vi } from 'vitest'
import { createApp, nextTick, defineComponent, h } from 'vue'
import { ElTable, ElTableColumn } from 'element-plus'
import Dialog from './SystemUserDialog.vue'
import { passthrough } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

const api = vi.hoisted(() => ({ page: vi.fn(), resolve: vi.fn() }))
vi.mock('@/api/system/user/selection', () => ({
  systemUserSelection: api,
  sameSystemUserId: (a: unknown, b: unknown) => a != null && b != null && String(a) === String(b)
}))
vi.mock('@/views/system/dept/components/DeptTreeSelect.vue', () => ({
  default: {
    setup: (_: unknown, { expose }: any) => {
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

const mounted: ReturnType<typeof createApp>[] = []
afterEach(() => {
  mounted.splice(0).forEach((app) => app.unmount())
  document.body.replaceChildren()
})
const settle = async () => {
  for (let i = 0; i < 5; i++) await nextTick()
}

it('retains real Element Table checkbox selections across pages, deselection and reopening', async () => {
  const first = { id: '2115387679367467008', nickname: '第一页' }
  const second = { id: '2115387679367467009', nickname: '第二页' }
  api.page.mockImplementation(async ({ pageNo }) => ({
    list: [pageNo === 1 ? first : second],
    total: 2
  }))
  api.resolve.mockResolvedValue([first])
  const selected = vi.fn()
  const app = createApp(Dialog, { multiple: true, onSelected: selected })
  mounted.push(app)
  app.component('ElTable', ElTable)
  app.component('ElTableColumn', ElTableColumn)
  for (const name of [
    'Dialog',
    'ElAlert',
    'ElRow',
    'ElCol',
    'ContentWrap',
    'ElForm',
    'ElFormItem',
    'ElInput',
    'ElSelect',
    'ElOption',
    'ElButton',
    'ElRadio',
    'Icon',
    'DictTag',
    'Pagination'
  ])
    app.component(name, passthrough)
  app.directive('loading', {})
  const host = document.createElement('div')
  document.body.appendChild(host)
  const vm = app.mount(host)
  const state = (vm as any).$.setupState
  const clickRowCheckbox = async () => {
    const checkbox = host.querySelector<HTMLInputElement>(
      '.el-table__body-wrapper tbody tr input[type="checkbox"]'
    )
    expect(checkbox).not.toBeNull()
    checkbox!.click()
    await settle()
  }
  await state.open([first.id])
  await settle()
  expect(host.querySelector<HTMLInputElement>('.el-table__body-wrapper tbody input')!.checked).toBe(
    true
  )
  expect(state.selectedRows.map((user: any) => user.id)).toEqual([first.id])
  state.queryParams.pageNo = 2
  await state.getList()
  await settle()
  await clickRowCheckbox()
  expect(state.selectedRows.map((user: any) => user.id)).toEqual([first.id, second.id])
  state.queryParams.pageNo = 1
  await state.getList()
  await settle()
  expect(host.querySelector<HTMLInputElement>('.el-table__body-wrapper tbody input')!.checked).toBe(
    true
  )
  await clickRowCheckbox()
  state.confirmSelect()
  expect(selected).toHaveBeenLastCalledWith([expect.objectContaining(second)], undefined)
  await state.open([])
  await settle()
  expect(state.selectedRows).toEqual([])
  expect(host.querySelector<HTMLInputElement>('.el-table__body-wrapper tbody input')!.checked).toBe(
    false
  )
})

it('restores off-page staff drafts into a destroyed real table before allowing new checkbox changes', async () => {
  const first = { id: '2115387679367467008', nickname: '第一页' }
  const second = { id: '2115387679367467009', nickname: '第二页' }
  api.page.mockImplementation(async ({ pageNo }) => ({ list: [pageNo === 1 ? first : second], total: 2 }))
  api.resolve.mockResolvedValue([])
  const selected = vi.fn()
  const app = createApp(Dialog, { multiple: true, retainDraftOnReopen: true, allowEmpty: true, defaultStatus: 'all', onSelected: selected })
  mounted.push(app)
  app.component('ElTable', ElTable)
  app.component('ElTableColumn', ElTableColumn)
  // Mirror the platform Dialog destroy-on-close boundary: the actual ElTable instance is unmounted.
  app.component('Dialog', defineComponent({ props: { modelValue: Boolean }, setup(props, { slots }) {
    return () => props.modelValue ? h('section', slots.default?.()) : null
  } }))
  for (const name of ['ElAlert', 'ElRow', 'ElCol', 'ContentWrap', 'ElForm', 'ElFormItem', 'ElInput', 'ElSelect', 'ElOption', 'ElButton', 'ElRadio', 'ElDatePicker', 'Icon', 'DictTag', 'Pagination']) app.component(name, passthrough)
  app.directive('loading', {})
  const host = document.createElement('div')
  document.body.appendChild(host)
  const vm = app.mount(host), state = (vm as any).$.setupState
  const clickRow = async () => {
    const checkbox = host.querySelector<HTMLInputElement>('.el-table__body-wrapper tbody tr input[type="checkbox"]')
    expect(checkbox).not.toBeNull()
    checkbox!.click()
    await settle()
  }
  await state.open()
  await settle()
  await clickRow()
  state.queryParams.pageNo = 2
  await state.getList()
  await settle()
  await clickRow()
  expect(state.selectedRows.map((user: any) => user.id)).toEqual([first.id, second.id])
  state.dialogVisible = false
  await settle()
  expect(host.querySelector('.el-table')).toBeNull()
  expect(selected).not.toHaveBeenCalled()
  await state.open()
  await settle()
  expect(state.selectedRows.map((user: any) => user.id)).toEqual([first.id, second.id])
  await clickRow()
  expect(state.selectedRows.map((user: any) => user.id)).toEqual([first.id])
  state.confirmSelect()
  expect(selected).toHaveBeenLastCalledWith([expect.objectContaining(first)], undefined)
})
