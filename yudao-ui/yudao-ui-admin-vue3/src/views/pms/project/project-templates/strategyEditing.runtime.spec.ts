import { defineComponent, h, nextTick, reactive, ref } from 'vue'
import { beforeEach, expect, it, vi } from 'vitest'
import Editor from './TemplateContentEditor.vue'
import { emptyDesignerDocument } from '@/api/pms/project/project-templates'
import { mount, passthrough, tableColumn, textOf, type TestNode } from '../../platform/dynamic-form/components/runtimeTestHarness'

const dialogs = vi.hoisted(() => ({ confirm: vi.fn<() => Promise<void>>(), flush: vi.fn<() => Promise<void>>() }))
vi.mock('element-plus', () => ({ ElMessageBox: { confirm: dialogs.confirm } }))
vi.mock('@/config/axios', () => ({ default: {} }))
vi.mock('@/directives/permission/hasPermi', () => ({ hasPermission: () => true }))
vi.mock('@/api/pms/project/project-templates/directBinding', () => ({ createBindingSaveSession: () => new Map(), prepareTaskBinding: vi.fn() }))
vi.mock('@/api/pms/project/project-templates/designerAssets', () => ({}))
vi.mock('./TemplateFlowCanvas.vue', () => ({ default: { render: () => null } }))
vi.mock('./RuleSlotEditor.vue', () => ({ default: { render: () => null } }))
vi.mock('./RuleSimulationPanel.vue', () => ({ default: { render: () => null } }))
vi.mock('./DefinitionSelect.vue', () => ({ default: { render: () => null } }))
vi.mock('./TaskBindingEditor.vue', () => ({ default: { render: () => null } }))
vi.mock('./ApprovalDefinitionSelect.vue', () => ({ default: { render: () => null } }))
vi.mock('./DecisionTableEditor.vue', () => ({ default: defineComponent({
  props: { modelValue: Object, readonly: Boolean }, emits: ['update:modelValue'],
  setup: (props, { emit, expose }) => {
    expose({ flush: dialogs.flush })
    return () => h('button', { disabled: props.readonly,
      onClick: () => emit('update:modelValue', { ...props.modelValue, xml: 'edited-xml' }) }, '修改策略表')
  }
}) }))
const find = (node: TestNode, predicate: (node: TestNode) => boolean): TestNode | undefined =>
  predicate(node) ? node : node.children.map(child => find(child, predicate)).find(Boolean)
const setup = async () => {
  const state = reactive({ content: emptyDesignerDocument(), readonly: false })
  const editor = ref<InstanceType<typeof Editor>>()
  const page = mount(defineComponent({ setup: () => () => h(Editor, { ...state, ref: editor }) }), {},
    { ...Object.fromEntries(['ElRadioGroup', 'ElRadioButton', 'ElCollapse', 'ElCollapseItem', 'ElTable',
      'ElOption', 'ElSelect', 'ElInput', 'ElSwitch', 'ElDivider'].map(name => [name, passthrough])), ElTableColumn: tableColumn })
  const mode = find(page.root, node => typeof node.props?.['onUpdate:modelValue'] === 'function')!
  ;(mode.props!['onUpdate:modelValue'] as (mode: string) => void)('RULES')
  await nextTick()
  const button = (label: string) => {
    const result = find(page.root, node => node.type === 'button' && textOf(node) === label)
    expect(result, label).toBeDefined()
    return result!
  }
  const click = (label: string) => (button(label).props!.onClick as () => void | Promise<void>)()
  await click('添加策略决策表（可选）')
  const table = state.content.rules![0]
  state.content.rules!.push({ key: 'condition', name: '表结果条件', kind: 'CONDITION', shared: true, expression: {
    predicate: 'DECISION', parameters: { ruleKey: table.key, fieldCode: 'result', operator: '=', valueType: 'BOOLEAN', value: true }
  } })
  state.content.tasks = ['A', 'B'].map(code => ({
    nodeKey: code, code, name: code, stageCode: 'PREP', completionRuleKey: 'condition',
    workBinding: { type: 'TASK_NATIVE', parameters: {} }, permission: {}
  }))
  await nextTick()
  return { ...page, state, editor, table, click, button }
}
beforeEach(() => {
  dialogs.confirm.mockReset().mockResolvedValue(undefined)
  dialogs.flush.mockReset().mockResolvedValue(undefined)
})

it('requires shared confirmation, preserves normal editing and invalidates it when another consumer is added', async () => {
  const page = await setup()
  try {
    const original = page.table.decision!.xml
    await page.click('修改策略表')
    expect(page.table.decision!.xml).toBe(original)
    await page.click('确认影响并编辑共享策略'); await nextTick()
    expect(page.button('修改策略表').props?.disabled).toBe(false)
    await page.click('修改策略表'); await nextTick()
    expect(page.table.decision!.xml).toBe('edited-xml')
    expect(dialogs.confirm).toHaveBeenCalledTimes(1)
    expect(page.button('修改策略表').props?.disabled).toBe(false)
    page.state.content.tasks.push({ ...page.state.content.tasks[0], code: 'C', nodeKey: 'C' })
    await nextTick()
    expect(page.button('修改策略表').props?.disabled).toBe(true)
  } finally { page.app.unmount() }
})

it.each(['document', 'rule', 'readonly', 'impact', 'unmount'] as const)('discards the shared strategy confirmation after %s changes', async change => {
  const page = await setup()
  let finish!: () => void
  dialogs.confirm.mockImplementationOnce(() => new Promise<void>(resolve => { finish = resolve }))
  try {
    const pending = page.click('确认影响并编辑共享策略')
    if (change === 'document') page.state.content = JSON.parse(JSON.stringify(page.state.content))
    if (change === 'rule') page.state.content.rules![0] = { ...page.table }
    if (change === 'readonly') page.state.readonly = true
    if (change === 'impact') page.state.content.tasks.push({ ...page.state.content.tasks[0], code: 'C', nodeKey: 'C' })
    if (change === 'unmount') page.app.unmount()
    await nextTick(); finish(); await pending; await nextTick()
    expect(page.table.shared).toBe(false)
    expect(page.state.content.rules![0].shared).toBe(false)
    if (change !== 'unmount') expect(page.button('修改策略表').props?.disabled).toBe(true)
  } finally { if (change !== 'unmount') page.app.unmount() }
})

it.each(['document', 'readonly', 'unmount'] as const)('does not return a save snapshot after %s changes while XML is flushing', async change => {
  const page = await setup()
  let finish!: () => void
  dialogs.flush.mockImplementationOnce(() => new Promise<void>(resolve => { finish = resolve }))
  try {
    const saving = page.editor.value!.prepareSave()
    const rejected = expect(saving).rejects.toThrow('编辑上下文已变化')
    if (change === 'document') page.state.content = emptyDesignerDocument()
    if (change === 'readonly') page.state.readonly = true
    if (change === 'unmount') page.app.unmount()
    await nextTick(); finish(); await rejected
  } finally { if (change !== 'unmount') page.app.unmount() }
})
