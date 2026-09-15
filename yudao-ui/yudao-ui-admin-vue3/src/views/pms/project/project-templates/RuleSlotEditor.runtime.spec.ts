import { defineComponent, h, nextTick, reactive } from 'vue'
import { beforeEach, expect, it, vi } from 'vitest'
import RuleSlotEditor from './RuleSlotEditor.vue'
import { emptyDesignerDocument } from '@/api/pms/project/project-templates'
import { constantRule, ruleUses } from './versionRuleModel'
import { mount, passthrough, textOf, type TestNode } from '../../platform/dynamic-form/components/runtimeTestHarness'

const confirm = vi.hoisted(() => vi.fn<() => Promise<void>>())
vi.mock('element-plus', () => ({ ElMessageBox: { confirm } }))
vi.mock('@/config/axios', () => ({ default: {} }))
vi.mock('./RuleSimulationPanel.vue', () => ({ default: { render: () => null } }))
vi.mock('./RuleDecisionDesigner.vue', () => ({ default: defineComponent({
  props: { disabled: Boolean }, emits: ['update:modelValue'],
  setup: (props, { emit }) => () => h('button', {
    disabled: props.disabled, onClick: () => emit('update:modelValue', constantRule(true))
  }, '修改条件')
}) }))

const fixture = () => {
  const document = emptyDesignerDocument()
  document.tasks = ['A', 'B'].map(code => ({
    nodeKey: `task:${code}`, code, name: '同名任务', stageCode: 'PREP',
    workBinding: { type: 'TASK_NATIVE', parameters: {} }, permission: {}, completionRuleKey: 'shared'
  }))
  document.rules = [{ key: 'shared', name: '共同条件', kind: 'CONDITION', shared: false, expression: constantRule(false) }]
  return document
}
const findButton = (node: TestNode, label: string): TestNode | undefined =>
  node.type === 'button' && textOf(node) === label ? node : node.children.map(child => findButton(child, label)).find(Boolean)
const setup = () => {
  const state = reactive({ document: fixture(), key: 'shared' as string | undefined, readonly: false })
  const page = mount(defineComponent({ setup: () => () => h(RuleSlotEditor, {
    document: state.document, modelValue: state.key, label: '完成条件', readonly: state.readonly,
    'onUpdate:modelValue': key => { state.key = key; state.document.tasks[0].completionRuleKey = key }
  }) }), {}, { ElPopover: passthrough, ElCheckbox: passthrough, ElSelect: passthrough, ElOption: passthrough })
  const button = (label: string) => {
    const found = findButton(page.root, label)
    expect(found, label).toBeDefined()
    return found!
  }
  const click = (label: string) => (button(label).props!.onClick as () => void | Promise<void>)()
  return { ...page, state, button, click }
}
beforeEach(() => { confirm.mockReset(); confirm.mockResolvedValue(undefined) })

it('counts same-name consumers separately and confirms once while the impact remains unchanged', async () => {
  const page = setup()
  try {
    expect(ruleUses(page.state.document, 'shared')).toEqual(['任务 同名任务（A） · 完成', '任务 同名任务（B） · 完成'])
    expect(page.button('修改条件').props?.disabled).toBe(true)
    await page.click('修改条件')
    expect(page.state.document.rules![0].expression).toEqual(constantRule(false))
    expect(confirm).not.toHaveBeenCalled()
    await page.click('开始修改共享规则'); await nextTick()
    expect(confirm).toHaveBeenCalledWith(expect.stringContaining('同名任务（B）'), '确认共享影响', expect.any(Object))
    expect(page.button('修改条件').props?.disabled).toBe(false)
    await page.click('修改条件'); await nextTick()
    expect(page.state.document.rules![0].expression).toEqual(constantRule(true))
    expect(page.button('修改条件').props?.disabled).toBe(false)
    expect(confirm).toHaveBeenCalledTimes(1)
    page.state.document.tasks.push({ ...page.state.document.tasks[1], nodeKey: 'task:C', code: 'C' })
    await nextTick()
    expect(page.button('修改条件').props?.disabled).toBe(true)
    expect(textOf(page.root)).toContain('同名任务（C）')
  } finally { page.app.unmount() }
})

it.each(['document', 'rule', 'readonly', 'impact', 'unmount'] as const)('discards confirmation after %s changes', async (change) => {
  const page = setup()
  const original = page.state.document
  let approve!: () => void
  confirm.mockImplementationOnce(() => new Promise<void>(resolve => { approve = resolve }))
  try {
    const pending = page.click('开始修改共享规则')
    if (change === 'document') page.state.document = fixture()
    if (change === 'rule') page.state.document.rules![0] = { ...original.rules![0] }
    if (change === 'readonly') page.state.readonly = true
    if (change === 'impact') page.state.document.tasks.push({ ...original.tasks[1], nodeKey: 'task:C', code: 'C' })
    if (change === 'unmount') page.app.unmount()
    await nextTick()
    approve(); await pending; await nextTick()
    expect(original.rules![0].shared).toBe(false)
    expect(page.state.document.rules![0].shared).toBe(false)
    expect(page.state.document.rules![0].expression).toEqual(constantRule(false))
    if (change !== 'unmount') expect(page.button('修改条件').props?.disabled).toBe(true)
  } finally { if (change !== 'unmount') page.app.unmount() }
})

it('cancels shared editing and forks only the selected reference into an independent rule', async () => {
  const page = setup()
  try {
    confirm.mockRejectedValueOnce('cancel')
    await page.click('开始修改共享规则'); await nextTick()
    expect(page.button('修改条件').props?.disabled).toBe(true)
    await page.click('复制为独立规则'); await nextTick()
    expect(page.state.key).not.toBe('shared')
    expect(page.state.document.tasks[1].completionRuleKey).toBe('shared')
    expect(page.button('修改条件').props?.disabled).toBe(false)
    await page.click('修改条件')
    expect(page.state.document.rules![0].expression).toEqual(constantRule(false))
    expect(page.state.document.rules!.find(rule => rule.key === page.state.key)).toMatchObject({
      shared: false, expression: constantRule(true)
    })
  } finally { page.app.unmount() }
})

it('counts repeated decision references once per consumer slot', () => {
  const document = fixture()
  const reference = { predicate: 'DECISION', parameters: { ruleKey: 'table' } }
  document.rules![0].expression = { operator: 'ALL', rules: [reference, reference] }
  expect(ruleUses(document, 'table')).toHaveLength(2)
  document.tasks.pop()
  expect(ruleUses(document, 'table')).toHaveLength(1)
})
