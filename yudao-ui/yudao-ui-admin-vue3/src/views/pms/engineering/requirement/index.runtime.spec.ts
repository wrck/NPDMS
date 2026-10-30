import { defineComponent, h, nextTick, onMounted } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import Page from './index.vue'
import { mount, passthrough, textOf, type TestNode } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

const state = vi.hoisted(() => ({
  row: { id: 1, requirementType: 'BUSINESS', status: 0, name: '旧记录' },
  getProject: vi.fn()
}))
vi.mock('@/utils/dict', () => ({
  DICT_TYPE: {},
  getIntDictOptions: () => [],
  getStrDictOptions: () => [{ label: '接口规划', value: 'INTERFACE' }]
}))
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => ({}) }))
vi.mock('@/api/pms/project/projects', () => ({ __v_isRef: false, getProjectPage: vi.fn(), getProject: state.getProject }))
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
  Dialog: defineComponent({
    props: { modelValue: { type: Boolean, default: false } },
    setup: (props, { slots }) => () => (props.modelValue ? h('section', [slots.default?.(), slots.footer?.()]) : null)
  }),
  ElRow: passthrough, ElCol: passthrough, ElSelect: passthrough, ElOption: passthrough,
  ElInput: passthrough, Editor: passthrough,
  // 桩挂载即回填项目 123，模拟用户在项目选择器中选中项目
  PmsEntitySelect: defineComponent({
    props: { modelValue: { type: null, default: '' }, api: { type: null, default: undefined } },
    emits: ['update:modelValue'],
    setup(_, { emit }) {
      onMounted(() => emit('update:modelValue', 123))
      return () => h('div')
    }
  })
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
  it('新增接口规划选中项目后按规范自动生成名称', async () => {
    state.row.requirementType = 'INTERFACE'
    state.getProject.mockResolvedValue({ projectCode: 'PJT-TEST-001', projectName: '测试项目' })
    const page = mount(Page, {}, components)
    await nextTick(); await nextTick()
    await click(page.root, '新增接口规划')
    await vi.waitFor(() => {
      const form = nodes(page.root).find(n => (n.props?.model as any)?.requirementType === 'INTERFACE')
      expect((form?.props?.model as any)?.name).toMatch(/^PJT-TEST-001_测试项目_接口规划_\d{14}$/)
    })
    page.app.unmount()
  })
})
