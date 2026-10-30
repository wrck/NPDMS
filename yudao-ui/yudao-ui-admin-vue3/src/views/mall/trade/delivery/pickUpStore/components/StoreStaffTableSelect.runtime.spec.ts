import { defineComponent, h, nextTick } from 'vue'
import { expect, it, vi } from 'vitest'
import StoreStaff from './StoreStaffTableSelect.vue'
import {
  mount,
  passthrough,
  tableColumn,
  textOf,
  type TestNode
} from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
const api = vi.hoisted(() => ({ page: vi.fn(), resolve: vi.fn(), active: vi.fn() }))
vi.mock('@/api/system/user/selection', () => ({
  systemUserSelection: api,
  sameSystemUserId: (a: unknown, b: unknown) => a != null && b != null && String(a) === String(b)
}))
vi.mock('@/views/system/dept/components/DeptTreeSelect.vue', () => ({
  default: { setup: (_: unknown, { expose }: { expose: (value: unknown) => void }) => { expose({ reset: vi.fn(), setCurrent: vi.fn() }); return () => null } }
}))
vi.mock('@/utils/dict', () => ({ DICT_TYPE: {}, getIntDictOptions: () => [] }))
vi.mock('@/utils/formatTime', () => ({ dateFormatter: () => '' }))
const find = (node: TestNode, predicate: (item: TestNode) => boolean): TestNode | undefined => {
  if (predicate(node)) return node
  for (const child of node.children) {
    const found = find(child, predicate)
    if (found) return found
  }
}
const flush = async () => {
  for (let i = 0; i < 8; i++) {
    await nextTick()
    await Promise.resolve()
  }
}
it('preserves the staff open/change contract, cancelled draft, long IDs and empty binding selection', async () => {
  const user = { id: '2115387679367467008', nickname: '店员' }
  api.page.mockResolvedValue({ list: [user], total: 1 })
  api.resolve.mockResolvedValue([])
  const changed = vi.fn()
  const page = mount(
    StoreStaff,
    { onChange: changed },
    {
      Dialog: defineComponent({ inheritAttrs: false, setup(_, { attrs, slots }) {
        return () => h('section', attrs, [...(slots.default?.() || []), ...(slots.footer?.() || [])])
      } }),
      ElRow: passthrough,
      ElCol: passthrough,
      ElSelect: passthrough,
      ElOption: passthrough,
      ElInput: passthrough,
      ElDatePicker: passthrough,
    ElRadio: passthrough,
    DictTag: passthrough,
      ElTableColumn: tableColumn,
      ElTable: defineComponent({
        inheritAttrs: false,
        setup(_, { attrs, slots, expose }) {
          expose({ clearSelection: vi.fn(), toggleRowSelection: vi.fn() })
          return () => h('table', attrs, slots.default?.())
        }
      })
    }
  )
  try {
    await (page.vm as any).open()
    const table = () =>
      find(page.root, (item) => typeof item.props?.onSelectionChange === 'function')!
    ;(table().props!.onSelectionChange as (rows: unknown[]) => void)([user])
    const dialog = find(page.root, (item) => item.props?.title === '选择店员')!
    ;(dialog.props!['onUpdate:modelValue'] as (visible: boolean) => void)(false)
    await flush()
    expect(changed).not.toHaveBeenCalled()
    await (page.vm as any).open()
    const confirm = find(
      page.root,
      (item) => typeof item.props?.onClick === 'function' && textOf(item).includes('确 定')
    )!
    ;(confirm.props!.onClick as () => void)()
    expect(changed).toHaveBeenLastCalledWith([user])
    expect(api.page).toHaveBeenCalledWith(
      expect.objectContaining({ status: undefined, createTime: [] })
    )
    expect(api.active).not.toHaveBeenCalled()
    await (page.vm as any).open()
    ;(table().props!.onSelectionChange as (rows: unknown[]) => void)([])
    ;(confirm.props!.onClick as () => void)()
    expect(changed).toHaveBeenLastCalledWith([])
  } finally {
    page.app.unmount()
  }
})
