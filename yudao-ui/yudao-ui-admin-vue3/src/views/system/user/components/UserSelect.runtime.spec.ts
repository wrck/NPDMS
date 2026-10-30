import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, reactive } from 'vue'
import UserSelect from '@/components/SystemUserSelect/SystemUserDropdown.vue'
import {
  mount,
  passthrough,
  textOf
} from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
const api = vi.hoisted(() => ({ list: vi.fn() }))
vi.mock('@/api/system/user', () => ({ getSimpleUserList: api.list }))
const apps: { unmount: () => void }[] = []
const flush = async () => {
  for (let i = 0; i < 8; i++) await nextTick()
}
function render(values: Record<string, unknown> = {}) {
  const props = reactive({
    modelValue: undefined as string | number | undefined,
    disabled: false,
    ...values
  })
  const update = vi.fn(),
    change = vi.fn()
  const page = mount(
    UserSelect as any,
    { ...props, 'onUpdate:modelValue': update, onChange: change },
    {
      ElSelect: passthrough,
      ElOption: defineComponent({
        setup:
          (_, { attrs }) =>
          () =>
            h('span', String(attrs.label || ''))
      }),
      ElTag: passthrough
    }
  )
  apps.push(page.app)
  return { ...page, state: (page.vm as any).$.setupState, update, change }
}
beforeEach(() => {
  api.list.mockReset()
  api.list.mockResolvedValue([{ id: 1, nickname: '测试管理员', deptName: '部门' }])
})
afterEach(() => apps.splice(0).forEach((app) => app.unmount()))
it('loads candidates on demand and preserves legacy numeric change events', async () => {
  const page = render()
  expect(api.list).not.toHaveBeenCalled()
  page.state.open(true)
  await flush()
  expect(api.list).toHaveBeenCalledTimes(1)
  page.state.selectValue = 1
  page.state.handleChange(1)
  expect(page.update).toHaveBeenCalledWith(1)
  expect(page.change).toHaveBeenCalledWith(
    expect.objectContaining({ id: 1, nickname: '测试管理员' })
  )
  page.state.open(false)
  page.state.open(true)
  await flush()
  expect(api.list).toHaveBeenCalledTimes(1)
})
it('keeps full string IDs, filters departments and does not select historical missing users', async () => {
  const id = '2115387679367467008'
  api.list.mockResolvedValue([
    { id, nickname: '长编号用户', deptName: '吉林' },
    { id: 2, nickname: '其他', status: 1 }
  ])
  const page = render({ modelValue: id })
  await flush()
  expect(page.state.selectValue).toBe(id)
  page.state.selectValue = id
  expect(page.update).toHaveBeenCalledWith(id)
  page.state.handleFilter('吉林')
  expect(page.state.filteredList).toHaveLength(1)
  expect(page.state.allList).toHaveLength(1)
  const history = render({ modelValue: '999', currentLabel: '历史责任人' })
  expect(textOf(history.root)).toContain('历史责任人')
  expect(history.state.missingSelection).toBe(true)
  expect(history.update).not.toHaveBeenCalled()
})
it('preserves selected value on load failure and permits an explicit retry', async () => {
  api.list.mockRejectedValueOnce(new Error('unavailable'))
  const page = render({ modelValue: 7 })
  await flush()
  expect(textOf(page.root)).toContain('用户候选加载失败')
  expect(page.update).not.toHaveBeenCalled()
  await page.state.load()
  await flush()
  expect(page.state.error).toBe('')
  expect(api.list).toHaveBeenCalledTimes(2)
})
it('does not load while disabled or apply a response after unmount', async () => {
  const disabled = render({ disabled: true, modelValue: 7 })
  disabled.state.open(true)
  await flush()
  expect(api.list).not.toHaveBeenCalled()
  let finish: (value: unknown) => void = () => undefined
  api.list.mockReturnValue(
    new Promise((resolve) => {
      finish = resolve
    })
  )
  const page = render()
  page.state.open(true)
  page.app.unmount()
  finish([{ id: 3, nickname: '迟到' }])
  await flush()
  expect(page.state.allList).toEqual([])
})

it('preserves the explicit all-users sentinel while default clearing remains undefined', () => {
  const broadcast = render({ preserveEmptyValue: true })
  broadcast.state.selectValue = ''
  expect(broadcast.update).toHaveBeenCalledWith('')
  const ordinary = render()
  ordinary.state.selectValue = ''
  expect(ordinary.update).toHaveBeenCalledWith(undefined)
})
it('supports multiple IDs and department narrowing without loading an empty selection eagerly', async () => {
  api.list.mockResolvedValue([
    { id: 1, nickname: '部门一', deptId: 1 },
    { id: '2115387679367467008', nickname: '部门二', deptId: 2 }
  ])
  const page = render({ multiple: true, modelValue: [], deptId: 2 })
  expect(api.list).not.toHaveBeenCalled()
  page.state.open(true)
  await flush()
  expect(page.state.filteredList.map((row: any) => row.id)).toEqual(['2115387679367467008'])
  page.state.handleChange(['2115387679367467008'])
  expect(page.change).toHaveBeenCalledWith([expect.objectContaining({ id: '2115387679367467008' })])
})
