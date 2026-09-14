import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, reactive, ref } from 'vue'
import Editor from './TemplateContentEditor.vue'
import { mount, passthrough } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
const confirmation = vi.hoisted(() => ({ confirm: vi.fn() }))
const permissions = vi.hoisted(() => ({ hasPermission: vi.fn() }))
vi.mock('element-plus', () => ({ ElMessageBox: confirmation }))
vi.mock('@/directives/permission/hasPermi', () => permissions)
vi.mock('@/api/pms/project/project-templates/directBinding', () => ({ createBindingSaveSession: () => new Map(), prepareTaskBinding: vi.fn() }))
vi.mock('@/api/pms/project/project-templates/designerAssets', () => ({}))
vi.mock('./TemplateFlowCanvas.vue', () => ({ default: { render: () => null } }))
vi.mock('./RuleSlotEditor.vue', () => ({ default: { render: () => null } }))
vi.mock('./RuleSimulationPanel.vue', () => ({ default: { render: () => null } }))
vi.mock('./DecisionTableEditor.vue', () => ({ default: { render: () => null } }))
vi.mock('./DefinitionSelect.vue', () => ({ default: { render: () => null } }))
vi.mock('./TaskBindingEditor.vue', () => ({ default: { render: () => null } }))
vi.mock('./ApprovalDefinitionSelect.vue', () => ({ default: { render: () => null } }))
const apps: { unmount: () => void }[] = []
const fixture = () => ({ schemaVersion: 2, match: {}, ruleAssets: [],
  stages: [{ nodeKey: 'stage:S', code: 'S', name: '工前准备', start: true, terminal: true }],
  tasks: ['A', 'B'].map(code => ({ nodeKey: `task:${code}`, code, name: code, stageCode: 'S',
    workBinding: { type: 'TASK_NATIVE', parameters: {} }, permission: { policyRef: 'existing' },
    completionRuleKey: 'shared', admissionRuleKey: 'admission', exitRuleKey: 'exit', source: { definitionRevisionId: 1, workBindingRevisionId: 2, completionRuleRevisionId: 3 } })),
  rules: [{ key: 'shared', kind: 'CONDITION', name: '共享条件', shared: true,
    expression: { predicate: 'TASK_NATIVE_STATUS', parameters: { requiredStatus: 'DONE' } } }],
  milestones: [], deliverables: [], gates: [], transitions: [] })
const render = async () => {
  const child = ref<any>(), props = reactive({ content: fixture(), readonly: false, bindingPermission: 'pms:project-plan:manage' })
  const view = mount(defineComponent({ setup: () => () => h(Editor, { ...props, ref: child } as any) }), {},
    Object.fromEntries(['ElRadioGroup', 'ElRadioButton', 'ElDivider', 'ElSelect', 'ElOption', 'ElInput', 'ElInputNumber', 'ElSwitch'].map(name => [name, passthrough])))
  apps.push(view.app); child.value.$.setupState.selectedKey = 'task:A'; await nextTick()
  return { props, state: () => child.value.$.setupState, editor: () => child.value }
}
const choice = { id: 'review:1:exact', key: 'review', name: '交付复核', version: 1 }
beforeEach(() => { vi.clearAllMocks(); permissions.hasPermission.mockReturnValue(true); confirmation.confirm.mockResolvedValue('confirm') })
afterEach(() => apps.splice(0).forEach(app => app.unmount()))
it('saves an exact approval pin and independent completion criteria while preserving shared rules and permissions', async () => {
  const view = await render(), shared = JSON.stringify(view.props.content.rules), other = JSON.stringify(view.props.content.tasks[1])
  await view.state().setApprovalBinding(choice)
  const saved = await view.editor().prepareSave(), task = saved.tasks[0]
  expect(task.workBinding).toEqual({ type: 'APPROVAL', approvalDefinitionKey: 'review', parameters: { processDefinitionId: choice.id } })
  expect(task.permission).toEqual({ policyRef: 'existing' })
  expect(task.admissionRuleKey).toBe('admission'); expect(task.exitRuleKey).toBe('exit')
  expect(JSON.stringify(saved.rules.slice(0, 1))).toBe(shared); expect(JSON.stringify(saved.tasks[1])).toBe(other)
  expect(saved.rules.find((rule: any) => rule.key === task.completionRuleKey)).toMatchObject({ shared: false, expression: { predicate: 'CONSTANT', parameters: { value: true } } })
  expect(task.source).toEqual({ definitionRevisionId: 1 })
  expect(confirmation.confirm.mock.calls[0][0]).toContain('本轮审批通过')
  view.props.content = JSON.parse(JSON.stringify(saved)); await nextTick()
  expect(view.state().runtimeNode.workBinding.parameters.processDefinitionId).toBe(choice.id)
})
it('changes an existing approval version without changing its additional conditions', async () => {
  const view = await render()
  await view.state().setApprovalBinding(choice)
  const previous = JSON.stringify(view.props.content.rules), key = view.props.content.tasks[0].completionRuleKey
  await view.state().setApprovalBinding({ ...choice, id: 'review:2:exact', version: 2 })
  expect(JSON.stringify(view.props.content.rules)).toBe(previous)
  expect(view.props.content.tasks[0].completionRuleKey).toBe(key)
  expect(view.state().runtimeNode.workBinding.parameters.processDefinitionId).toBe('review:2:exact')
})
it.each(['cancel', 'selection', 'readonly', 'permission', 'document'])('does not apply a stale or rejected approval selection: %s', async change => {
  const view = await render(), original = JSON.stringify(view.props.content)
  let finish!: () => void
  confirmation.confirm.mockReturnValueOnce(new Promise<void>((resolve, reject) => { finish = () => change === 'cancel' ? reject(new Error('cancel')) : resolve() }))
  const pending = view.state().setApprovalBinding(choice)
  if (change === 'selection') view.state().selectedKey = 'task:B'
  if (change === 'readonly') view.props.readonly = true
  if (change === 'permission') permissions.hasPermission.mockReturnValue(false)
  if (change === 'document') view.props.content = fixture()
  await nextTick(); finish(); await pending
  expect(JSON.stringify(view.props.content)).toBe(original)
})
