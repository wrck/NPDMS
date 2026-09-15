import { defineComponent, h, nextTick, reactive, ref } from 'vue'
import { beforeEach, expect, it, vi } from 'vitest'
import TemplateContentEditor from './TemplateContentEditor.vue'
import type { TemplateDesignerDocument } from '@/api/pms/project/project-templates'
import { prepareTaskBinding } from '@/api/pms/project/project-templates/directBinding'
import {
  mount,
  passthrough,
  textOf,
  type TestNode
} from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

// Confirm resolves the action string; prompt alone returns input data.
// https://element-plus.org/en-US/component/message-box.html#confirm
const confirmMessage = vi.hoisted(() => vi.fn<() => Promise<'confirm'>>())
vi.mock('element-plus', () => ({ ElMessageBox: { confirm: confirmMessage } }))
vi.mock('@/directives/permission/hasPermi', () => ({ hasPermission: () => true }))
vi.mock('./ApprovalDefinitionSelect.vue', () => ({ default: { render: () => null } }))
vi.mock('@/api/pms/project/project-templates/directBinding', () => ({
  createBindingSaveSession: () => ({}),
  prepareTaskBinding: vi.fn()
}))
vi.mock('@/api/pms/project/project-templates/designerAssets', () => ({}))
vi.mock('./TemplateFlowCanvas.vue', () => ({
  default: defineComponent({
    emits: ['select', 'enter'],
    setup:
      (_, { emit }) =>
      () =>
        h(
          'div',
          ['stage:S', 'task:A', 'task:B'].map((key) =>
            h('button', { onClick: () => emit('select', key) }, key)
          )
        )
  })
}))
vi.mock('./RuleSlotEditor.vue', () => ({ default: { render: () => null } }))
vi.mock('./RuleSimulationPanel.vue', () => ({ default: { render: () => null } }))
vi.mock('./DecisionTableEditor.vue', () => ({ default: { render: () => null } }))
vi.mock('./DefinitionSelect.vue', () => ({ default: { render: () => null } }))
vi.mock('./TaskBindingEditor.vue', () => ({
  default: defineComponent({
    emits: ['update:model-value'],
    setup:
      (_, { emit }) =>
      () =>
        h(
          'button',
          {
            onClick: () => emit('update:model-value', { kind: 'VIEW', view: { id: 9 } })
          },
          '选择业务页面'
        )
  })
}))

const fixture = (): TemplateDesignerDocument => ({
  schemaVersion: 2,
  match: {},
  ruleAssets: [],
  stages: [
    { nodeKey: 'stage:S', code: 'S', name: '工前准备', sortOrder: 0, start: true, terminal: true,
      workBinding: { type: 'STAGE_NATIVE', parameters: {} }, permission: {} }
  ],
  tasks: ['A', 'B'].map((code) => ({
    nodeKey: `task:${code}`,
    code,
    name: code,
    stageCode: 'S',
    workBinding: { type: 'BUSINESS_OBJECT', parameters: { targetContext: 'SOL' } },
    permission: { policyRef: 'EXISTING_TASK_POLICY' },
    source: { definitionRevisionId: 1, workBindingRevisionId: 2, completionRuleRevisionId: 3 },
    admissionRuleKey: 'admission',
    completionRuleKey: 'shared',
    exitRuleKey: 'exit'
  })),
  rules: [
    {
      key: 'shared',
      name: '共享完成',
      kind: 'CONDITION',
      shared: true,
      expression: { predicate: 'BUSINESS_FACT', parameters: { factCode: 'SURVEY_CONFIRMED' } }
    }
  ],
  milestones: [],
  deliverables: [],
  gates: [],
  transitions: []
})
const visit = (node: TestNode, label: string): TestNode | undefined =>
  node.type === 'button' && textOf(node) === label
    ? node
    : node.children.map((child) => visit(child, label)).find(Boolean)
const tick = async () => {
  await nextTick()
  await nextTick()
}
const setup = () => {
  const state = reactive({ content: fixture(), readonly: false })
  const editor = ref<InstanceType<typeof TemplateContentEditor>>()
  const components = Object.fromEntries(
    [
      'ElRadioGroup',
      'ElRadioButton',
      'ElDivider',
      'ElSelect',
      'ElOption',
      'ElInput',
      'ElInputNumber',
      'ElSwitch'
    ].map((name) => [name, passthrough])
  )
  const page = mount(
    defineComponent({
      setup: () => () =>
        h(TemplateContentEditor, {
          ref: editor,
          content: state.content,
          readonly: state.readonly
        })
    }),
    {},
    components
  )
  const click = async (label: string) => {
    const button = visit(page.root, label)
    expect(button, label).toBeDefined()
    await (button!.props!.onClick as () => unknown)()
    await tick()
  }
  return { ...page, state, editor, click }
}
beforeEach(() => vi.resetAllMocks())

it('saves an independent manual completion rule without rewriting shared rules or node permissions', async () => {
  const page = setup()
  try {
    await page.click('task:A')
    const shared = JSON.stringify(page.state.content.rules)
    const other = JSON.stringify(page.state.content.tasks[1])
    confirmMessage.mockResolvedValue('confirm')
    await page.click('切换为手工办理')
    const saved = await page.editor.value!.prepareSave()
    const task = saved.tasks[0]
    expect(task.workBinding).toEqual({ type: 'TASK_NATIVE', parameters: {} })
    expect(task.permission).toEqual({ policyRef: 'EXISTING_TASK_POLICY' })
    expect(task.admissionRuleKey).toBe('admission')
    expect(task.exitRuleKey).toBe('exit')
    expect(task.source).toEqual({ definitionRevisionId: 1 })
    expect(task.completionRule).toBeUndefined()
    expect(saved.rules?.find((rule) => rule.key === task.completionRuleKey)).toMatchObject({
      shared: false,
      expression: { predicate: 'TASK_NATIVE_STATUS', parameters: { requiredStatus: 'DONE' } }
    })
    expect(JSON.stringify(saved.rules?.slice(0, 1))).toBe(shared)
    expect(JSON.stringify(saved.tasks[1])).toBe(other)
    page.state.content = saved
    await tick()
    expect(visit(page.root, '切换为手工办理')).toBeUndefined()
    expect(prepareTaskBinding).not.toHaveBeenCalled()
  } finally {
    page.app.unmount()
  }
})

it('cancels without mutation and clears an unsaved business selection only after confirmation', async () => {
  const page = setup()
  try {
    page.state.content.tasks[0].workBinding = { type: 'TASK_NATIVE', parameters: {} }
    await page.click('task:A')
    await page.click('配置业务页面／表单／审批')
    await page.click('选择业务页面')
    const before = JSON.stringify(page.state.content)
    confirmMessage.mockRejectedValueOnce('cancel')
    await page.click('切换为手工办理')
    expect(JSON.stringify(page.state.content)).toBe(before)
    expect(page.editor.value!.hasPendingBindings()).toBe(true)
    confirmMessage.mockResolvedValue('confirm')
    await page.click('切换为手工办理')
    expect(page.editor.value!.hasPendingBindings()).toBe(false)
    await page.editor.value!.prepareSave()
    expect(prepareTaskBinding).not.toHaveBeenCalled()
  } finally {
    page.app.unmount()
  }
})

it.each(['document', 'selection', 'readonly'] as const)(
  'does not apply a delayed confirmation after %s changes',
  async (change) => {
    const page = setup()
    try {
      await page.click('task:A')
      const original = page.state.content
      let confirm!: (value: 'confirm') => void
      confirmMessage.mockReturnValue(
        new Promise((resolve) => {
          confirm = resolve
        })
      )
      const pending = page.click('切换为手工办理')
      if (change === 'document') page.state.content = fixture()
      if (change === 'selection') await page.click('task:B')
      if (change === 'readonly') page.state.readonly = true
      await tick()
      confirm('confirm')
      await pending
      expect(original.tasks[0].workBinding.type).toBe('BUSINESS_OBJECT')
      expect(page.state.content.tasks[1].workBinding.type).toBe('BUSINESS_OBJECT')
      expect(original.rules).toHaveLength(1)
    } finally {
      page.app.unmount()
    }
  }
)

it('does not offer task manual switching on stages or read-only nodes', async () => {
  const page = setup()
  try {
    await page.click('stage:S')
    expect(visit(page.root, '切换为手工办理')).toBeUndefined()
    page.state.readonly = true
    await page.click('task:A')
    expect(visit(page.root, '切换为手工办理')).toBeUndefined()
  } finally {
    page.app.unmount()
  }
})
