import { defineComponent, h, nextTick, reactive } from 'vue'
import { expect, it, vi } from 'vitest'
import Editor from './TemplateContentEditor.vue'
import { emptyDesignerDocument } from '@/api/pms/project/project-templates'
import { createDeliveryNode } from './templateCanvasModel'
import { mount, passthrough, textOf, type TestNode } from '../../platform/dynamic-form/components/runtimeTestHarness'

vi.mock('element-plus', () => ({ ElMessageBox: { confirm: vi.fn().mockResolvedValue(undefined) } }))
vi.mock('@/config/axios', () => ({ default: {} }))
vi.mock('@/directives/permission/hasPermi', () => ({ hasPermission: () => true }))
vi.mock('@/api/pms/project/project-templates/directBinding', () => ({ createBindingSaveSession: () => new Map(), prepareTaskBinding: vi.fn() }))
vi.mock('@/api/pms/project/project-templates/designerAssets', () => ({}))
vi.mock('./TemplateFlowCanvas.vue', () => ({ default: defineComponent({
  inheritAttrs: false, setup: (_, { attrs }) => () => h('canvas-probe', attrs)
}) }))
vi.mock('./RuleSlotEditor.vue', () => ({ default: { render: () => null } }))
vi.mock('./RuleSimulationPanel.vue', () => ({ default: { render: () => null } }))
vi.mock('./DefinitionSelect.vue', () => ({ default: { render: () => null } }))
vi.mock('./TaskBindingEditor.vue', () => ({ default: { render: () => null } }))
vi.mock('./ApprovalDefinitionSelect.vue', () => ({ default: { render: () => null } }))
vi.mock('./DecisionTableEditor.vue', () => ({ default: { render: () => null } }))

const find = (node: TestNode, predicate: (node: TestNode) => boolean): TestNode | undefined =>
  predicate(node) ? node : node.children.map(child => find(child, predicate)).find(Boolean)

const setup = () => {
  const content = emptyDesignerDocument()
  const prep = createDeliveryNode(content, 'STAGE', undefined, 'prep', { x: 0, y: 0 })
  const after = createDeliveryNode(content, 'STAGE', undefined, 'after', { x: 400, y: 0 })
  const survey = createDeliveryNode(content, 'TASK', prep.code, 'survey', { x: 70, y: 80 })
  const analysis = createDeliveryNode(content, 'TASK', after.code, 'analysis', { x: 400, y: 100 })
  content.rules!.push({ key: 'from-survey', name: '工勘已确认', kind: 'CONDITION', shared: true,
    expression: { predicate: 'BUSINESS_FACT', parameters: {
      sourceNodeKey: survey.nodeKey, factCode: 'SURVEY_CONFIRMED', quantifier: 'ALL'
    } } })
  content.stages[1].admissionRuleKey = 'from-survey'
  content.tasks[1].admissionRuleKey = 'from-survey'
  const state = reactive({ content, readonly: false })
  const page = mount(defineComponent({ setup: () => () => h(Editor, state) }), {},
    Object.fromEntries(['ElRadioGroup', 'ElRadioButton', 'ElCollapse', 'ElCollapseItem', 'ElTable',
      'ElOption', 'ElSelect', 'ElInput', 'ElSwitch', 'ElDivider', 'ElAlert', 'ElForm', 'ElFormItem'].map(name => [name, passthrough])))
  const canvas = () => find(page.root, node => node.type === 'canvas-probe')!.props!
  return { ...page, state, prep, after, survey, analysis, canvas }
}

it('shows the task source on the main canvas and removes only its visible reference', async () => {
  const page = setup()
  try {
    expect(page.canvas().nodes).toContainEqual(expect.objectContaining({ key: page.survey.nodeKey, reference: true }))
    expect(page.canvas().edges).toEqual([expect.objectContaining({ from: page.survey.nodeKey, to: page.after.nodeKey })])
    const source = JSON.stringify(page.state.content.tasks[0])
    ;(page.canvas().onSelect as Function)(page.survey.nodeKey)
    await nextTick()
    expect(find(page.root, node => node.props?.title === '跨阶段引用只读；位置仅保存在当前画布，不修改原节点。')).toBeDefined()
    ;(page.canvas().onMove as Function)(page.survey.nodeKey, { x: 900, y: 100 })
    expect(page.state.content.layout?.nodes?.[`$stages:reference:${page.survey.nodeKey}`]).toEqual({ x: 900, y: 100 })
    expect(page.state.content.layout?.nodes?.[page.survey.nodeKey]).toEqual({ x: 70, y: 80 })
    ;(page.canvas().onConnect as Function)(page.after.nodeKey, page.survey.nodeKey)
    expect(JSON.stringify(page.state.content.tasks[0])).toBe(source)
    await (page.canvas().onRemove as Function)(page.survey.nodeKey)
    await nextTick()
    expect(page.state.content.tasks).toHaveLength(2)
    expect(JSON.stringify(page.state.content.tasks[0])).toBe(source)
    expect(page.state.content.tasks[1].admissionRuleKey).toBe('from-survey')
    expect(page.state.content.stages[1].admissionRuleKey).not.toBe('from-survey')
    expect(page.canvas().edges).toHaveLength(0)
  } finally { page.app.unmount() }
})

it('projects the same business dependency in the task canvas and opens its real owner', async () => {
  const page = setup()
  try {
    ;(page.canvas().onEnter as Function)(page.after.nodeKey)
    await nextTick()
    expect(page.canvas().edges).toEqual([expect.objectContaining({ from: page.survey.nodeKey, to: page.analysis.nodeKey })])
    expect(page.canvas().nodes).toContainEqual(expect.objectContaining({ key: page.survey.nodeKey, reference: true }))
    const before = JSON.stringify(page.state.content.rules)
    ;(page.canvas().onConnect as Function)(page.survey.nodeKey, page.analysis.nodeKey)
    expect(JSON.stringify(page.state.content.rules)).toBe(before)
    ;(page.canvas().onSelect as Function)(page.survey.nodeKey)
    await nextTick()
    const open = find(page.root, node => node.type === 'button' && textOf(node) === '打开所属阶段编辑')!
    ;(open.props!.onClick as Function)()
    await nextTick()
    expect(page.canvas().nodes).toContainEqual(expect.objectContaining({ key: page.survey.nodeKey, reference: false }))
    expect(find(page.root, node => node.props?.title === '跨阶段引用只读；位置仅保存在当前画布，不修改原节点。')).toBeUndefined()
  } finally { page.app.unmount() }
})

it('ignores stale reference move, connect and removal handlers after switching to read-only', async () => {
  const page = setup()
  try {
    const handlers = page.canvas()
    page.state.readonly = true
    await nextTick()
    const before = JSON.stringify(page.state.content)
    ;(handlers.onMove as Function)(page.survey.nodeKey, { x: 1, y: 2 })
    ;(handlers.onConnect as Function)(page.prep.nodeKey, page.after.nodeKey)
    await (handlers.onRemove as Function)(page.survey.nodeKey)
    expect(JSON.stringify(page.state.content)).toBe(before)
  } finally { page.app.unmount() }
})
