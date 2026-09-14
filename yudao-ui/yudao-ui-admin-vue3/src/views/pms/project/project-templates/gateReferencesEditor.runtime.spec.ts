import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, reactive, ref } from 'vue'
import type { DesignerGateNode, TemplateDesignerDocument } from '@/api/pms/project/project-templates'
import GateEditor from './GateReferencesEditor.vue'
import TemplateEditor from './TemplateContentEditor.vue'
import { findByTestId, mount, passthrough, textOf } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

const messages = vi.hoisted(() => ({ confirm: vi.fn() }))
const permission = vi.hoisted(() => ({ hasPermission: vi.fn() }))
vi.mock('element-plus', () => ({ ElMessageBox: messages }))
vi.mock('@/directives/permission/hasPermi', () => permission)
vi.mock('@/api/pms/project/project-templates/directBinding', () => ({ createBindingSaveSession: () => new Map(), prepareTaskBinding: vi.fn() }))
vi.mock('@/api/pms/project/project-templates/designerAssets', () => ({}))
vi.mock('./TemplateFlowCanvas.vue', () => ({ default: { render: () => null } }))
vi.mock('./RuleSlotEditor.vue', () => ({ default: { render: () => null } }))
vi.mock('./RuleSimulationPanel.vue', () => ({ default: { render: () => null } }))
vi.mock('./DecisionTableEditor.vue', () => ({ default: { render: () => null } }))
vi.mock('./DefinitionSelect.vue', () => ({ default: { render: () => null } }))
vi.mock('./TaskBindingEditor.vue', () => ({ default: { render: () => null } }))
vi.mock('./ApprovalDefinitionSelect.vue', () => ({ default: {
  props: ['binding', 'readonly', 'bindingPermission'], emits: ['choose'],
  setup(props: { binding: { approvalDefinitionKey?: string; parameters: { processDefinitionId?: string } }; readonly?: boolean },
    { emit }: { emit: (name: string, value: { id: string; key: string; name: string; version: number }) => void }) {
    return () => h('button', { 'data-testid': `pick-${props.binding.approvalDefinitionKey}`,
    disabled: props.readonly, onClick: () => emit('choose', { id: 'review:1:101', key: 'review', name: '交付复核', version: 1 }) },
    props.binding.parameters.processDefinitionId) }
} }))
const apps: { unmount: () => void }[] = []
const gate = (): DesignerGateNode => ({ nodeKey: 'gate:ready', code: 'READY', name: '工前准备门禁', stageCode: 'PREP', gateType: 'EXIT',
  references: [{ refType: 'APPROVAL', refCode: 'old', refVersion: 'old:1:11' }, { refType: 'TASK', refCode: 'SURVEY' }] })
const flush = async () => { for (let i = 0; i < 6; i++) { await Promise.resolve(); await nextTick() } }
const render = async () => {
  const child = ref<any>(), props = reactive({ gate: gate(), readonly: false, consumers: ['需求分析'], bindingPermission: 'pms:project-plan:manage' as const })
  const view = mount(defineComponent({ setup: () => () => h(GateEditor, { ...props, ref: child }) }), {},
    { ElSelect: passthrough, ElOption: passthrough, ElInput: passthrough })
  apps.push(view.app); await flush()
  return { ...view, props, state: () => child.value.$.setupState }
}
const choice = { id: 'review:1:101', key: 'review', name: '交付复核', version: 1 }
beforeEach(() => { vi.clearAllMocks(); permission.hasPermission.mockReturnValue(true); messages.confirm.mockResolvedValue('confirm') })
afterEach(() => apps.splice(0).forEach(app => app.unmount()))

it('maps a real picker event to the exact gate pin without changing other references and displays direct consumers', async () => {
  const view = await render(), other = JSON.stringify(view.props.gate.references[1])
  const click = findByTestId(view.root, 'pick-old')!.props!.onClick as () => void
  click(); await flush()
  expect(view.props.gate.references[0]).toEqual({ refType: 'APPROVAL', refCode: 'review', refVersion: choice.id })
  expect(JSON.stringify(view.props.gate.references[1])).toBe(other)
  expect(messages.confirm.mock.calls[0][0]).toContain('需求分析')
  expect(textOf(view.root)).toContain(choice.id)
  expect(permission.hasPermission).toHaveBeenCalledWith(['pms:project-plan:manage'])
})

it('preserves exact pins through the parent prepareSave and reopen contract', async () => {
  const document: TemplateDesignerDocument = { schemaVersion: 2, match: {}, stages: [], tasks: [], gates: [gate()],
    milestones: [], deliverables: [], transitions: [], ruleAssets: [], rules: [] }
  const child = ref<any>(), props = reactive({ content: document })
  const view = mount(defineComponent({ setup: () => () => h(TemplateEditor, { ...props, ref: child }) }), {},
    { ElSelect: passthrough, ElOption: passthrough, ElInput: passthrough, ElRadioGroup: passthrough, ElRadioButton: passthrough })
  apps.push(view.app); child.value.$.setupState.selectedKey = 'gate:ready'; await flush()
  ;(findByTestId(view.root, 'pick-old')!.props!.onClick as () => void)(); await flush()
  const saved = await child.value.prepareSave()
  expect(saved.gates[0].references[0].refVersion).toBe(choice.id)
  props.content = JSON.parse(JSON.stringify(saved)); await flush()
  expect(textOf(view.root)).toContain(choice.id)
  expect(child.value.$.setupState.runtimeNode).toBeUndefined()
})

it.each(['cancel', 'removed', 'type', 'gate', 'readonly', 'permission', 'unmount'])('ignores a rejected or stale choice after %s', async change => {
  const view = await render(), reference = view.props.gate.references[0]
  let finish!: () => void
  messages.confirm.mockReturnValueOnce(new Promise<void>((resolve, reject) => { finish = () => change === 'cancel' ? reject(new Error('cancel')) : resolve() }))
  const pending = view.state().chooseProcess(reference, choice)
  if (change === 'removed') view.props.gate.references.splice(0, 1)
  if (change === 'type') view.state().changeType(reference, 'TASK')
  if (change === 'gate') view.props.gate = gate()
  if (change === 'readonly') view.props.readonly = true
  if (change === 'permission') permission.hasPermission.mockReturnValue(false)
  if (change === 'unmount') { view.app.unmount(); apps.splice(apps.indexOf(view.app), 1) }
  const expected = JSON.stringify(view.props.gate); await flush(); finish(); await pending
  expect(JSON.stringify(view.props.gate)).toBe(expected)
})

it('rejects duplicate process references and clears stale identity when switching reference kind', async () => {
  const view = await render(), reference = view.props.gate.references[0]
  view.props.gate.references.push({ refType: 'APPROVAL', refCode: 'review', refVersion: 'review:2:202' })
  await view.state().chooseProcess(reference, choice)
  expect(reference.refCode).toBe('old'); expect(messages.confirm).not.toHaveBeenCalled()
  expect(view.state().error).toContain('不能重复添加')
  view.state().changeType(reference, 'TASK')
  expect(reference).toEqual({ refType: 'TASK', refCode: '' })
  view.state().setCode(reference, 'SURVEY'); expect(reference.refCode).toBe('SURVEY')
})

it('keeps remaining row identity after removal and forbids writes in read-only mode', async () => {
  const view = await render(), remaining = view.props.gate.references[1], key = view.state().rowKey(remaining)
  view.state().remove(view.props.gate.references[0])
  expect(view.state().rowKey(remaining)).toBe(key)
  view.props.readonly = true; await flush(); const original = JSON.stringify(view.props.gate)
  view.state().remove(remaining); view.state().setCode(remaining, 'changed'); view.state().changeType(remaining, 'PROCESS'); view.state().add()
  expect(JSON.stringify(view.props.gate)).toBe(original)
})
