import { expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import Dropdown from './SystemUserDropdown.vue'
import { systemUserSelection } from '@/api/system/user/selection'
import {
  mount,
  passthrough,
  type TestNode
} from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

vi.mock('@/api/system/user/selection', () => ({ systemUserSelection: { active: vi.fn() } }))

const nodes = (node: TestNode): TestNode[] => [node, ...node.children.flatMap(nodes)]
const render = (extra: Record<string, unknown> = {}) => {
  const update = vi.fn()
  const page = mount(
    Dropdown,
    { modelValue: '9', currentLabel: '当前工程师', 'onUpdate:modelValue': update, ...extra },
    { ElSelect: passthrough, ElOption: passthrough }
  )
  const state = (page.vm as any).$.setupState
  const selectedOption = () =>
    nodes(page.root).find((node) => String(node.props?.value) === String(state.selectValue))!
  return { ...page, state, update, selectedOption }
}

it('keeps the known selected name stable before, during and after delayed directory loading', async () => {
  let finish: (items: any[]) => void = () => undefined
  vi.mocked(systemUserSelection.active).mockReturnValueOnce(
    new Promise((resolve) => {
      finish = resolve
    })
  )
  const page = render()
  try {
    expect(page.selectedOption().props?.label).toBe('当前工程师')
    page.state.open(true)
    await nextTick()
    expect(page.state.loading).toBe(true)
    expect(page.selectedOption().props?.label).toBe('当前工程师')
    finish([
      { id: 9, nickname: '当前工程师', status: 0 },
      { id: 87, nickname: '原责任人', status: 0 }
    ])
    await Promise.resolve()
    await nextTick()
    expect(page.state.loading).toBe(false)
    expect(page.selectedOption().props?.label).toBe('当前工程师')
    expect(page.state.selectValue).toBe(9)
    expect(page.update).not.toHaveBeenCalled()
  } finally {
    page.app.unmount()
  }
})

it('retains the display name after a failed directory request without adding selectable candidates', async () => {
  vi.mocked(systemUserSelection.active).mockRejectedValueOnce(new Error('503'))
  const page = render()
  try {
    page.state.open(true)
    await Promise.resolve()
    await nextTick()
    expect(page.state.error).toContain('加载失败')
    expect(page.selectedOption().props?.label).toBe('当前工程师')
    expect([true, '']).toContain(page.selectedOption().props?.disabled)
    expect(page.state.allList).toEqual([])
    expect(page.update).not.toHaveBeenCalled()
  } finally {
    page.app.unmount()
  }
})
