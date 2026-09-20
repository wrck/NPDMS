import { defineComponent, h, nextTick } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import Page from './index.vue'
import { mount, passthrough, textOf, type TestNode } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

const state = vi.hoisted(() => ({ row: { id: 1, requirementType: 'BUSINESS', status: 0, name: '旧记录' } }))
vi.mock('@/utils/dict', () => ({ DICT_TYPE: {}, getIntDictOptions: () => [], getStrDictOptions: () => [] }))
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => ({}) }))
vi.mock('@/api/pms/project/projects', () => ({ __v_isRef: false, getProjectPage: vi.fn() }))
vi.mock('@/api/pms/engineering/requirement', () => ({ getRequirementPage: vi.fn(async () => ({ list: [state.row], total: 1 })) }))
const nodes = (node: TestNode): TestNode[] => [node, ...node.children.flatMap(nodes)]
const click = async (root: TestNode, label: string) => {
  const target = nodes(root).find(n => n.type === 'button' && textOf(n) === label)
  expect(target).toBeDefined()
  ;(target!.props!.onClick as Function)()
  await nextTick()
}
const components = {
  ElTable: passthrough,
  ElTableColumn: defineComponent({ setup: (_, { slots }) => () => h('section', slots.default?.({ row: state.row })) }),
  Dialog: defineComponent({ setup: (_, { slots }) => () => h('section', [slots.default?.(), slots.footer?.()]) }),
  ElRow: passthrough, ElCol: passthrough, ElSelect: passthrough, ElOption: passthrough,
  ElInput: passthrough, Editor: passthrough, PmsEntitySelect: passthrough
}
describe('legacy requirement entry', () => {
  it('keeps BUSINESS read-only, including rich text editors', async () => {
    state.row.requirementType = 'BUSINESS'
    const page = mount(Page, {}, components)
    await nextTick(); await nextTick()
    await click(page.root, '查看')
    const labels = nodes(page.root).filter(n => n.type === 'button').map(textOf)
    for (const label of ['保存', '编辑', '提交', '删除', '归档', '标记生效']) expect(labels).not.toContain(label)
    expect(nodes(page.root).some(n => n.props?.disabled === true && n.props?.model)).toBe(true)
    expect(nodes(page.root).filter(n => n.props?.readonly === true)).toHaveLength(2)
    page.app.unmount()
  })
  it('retains INTERFACE actions and defaults new records to INTERFACE', async () => {
    state.row.requirementType = 'INTERFACE'
    const page = mount(Page, {}, components)
    await nextTick(); await nextTick()
    const labels = nodes(page.root).filter(n => n.type === 'button').map(textOf)
    for (const label of ['编辑', '提交', '删除']) expect(labels).toContain(label)
    await click(page.root, '新增接口规划')
    const form = nodes(page.root).find(n => (n.props?.model as any)?.requirementType === 'INTERFACE')
    expect(form).toBeDefined()
    expect((form?.props?.model as any)?.id).toBeUndefined()
    expect(nodes(page.root).filter(n => n.type === 'button').map(textOf)).toContain('保存')
    page.app.unmount()
  })
})
