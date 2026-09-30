import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, reactive, ref } from 'vue'
import StageGateResultsPanel from './StageGateResultsPanel.vue'
import { mount, textOf, type TestNode } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
import type { StageGateWorkbench } from '@/api/pms/project/stage-gates'
import type { ProjectInstancesVO } from '@/api/pms/project/projects'

const api = vi.hoisted(() => ({ getStageGateWorkbench: vi.fn() }))
const leave = vi.hoisted(() => vi.fn())
vi.mock('@/api/pms/project/stage-gates', () => api)
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }), onBeforeRouteLeave: vi.fn() }))
vi.mock('@/utils/dict', () => ({ DICT_TYPE: {} }))
vi.mock('@/utils/permission', () => ({ checkPermi: () => true }))
vi.mock('@/api/pms/project/projects', () => ({ startProjectStageGateProcess: vi.fn() }))
vi.mock('@/views/pms/project/inheritance/detail/TaskApprovalForm.vue', () => ({ default: defineComponent({
  setup(_, { expose }) { expose({ requestLeave: leave, isBusy: () => false }); return () => h('div', '共用审批表单') }
}) }))
// 交付件弹窗内部走真实文件API（node环境无window），浅mock为入口桩
vi.mock('./ProjectDeliverableDialog.vue', () => ({ default: defineComponent({
  setup(_, { expose }) { expose({ open: vi.fn(), requestLeave: async () => true, isBusy: () => false }); return () => h('div', '交付件弹窗') }
}) }))
const apps: { unmount: () => void }[] = []
const flush = async () => { for (let i = 0; i < 20; i++) { await Promise.resolve(); await nextTick() } }
const result = (stageCode = 'PREP'): StageGateWorkbench => ({ projectId: '9', projectVersion: 4, planVersionId: '51',
  stageId: '11', stageCode, executionId: '61', executionRound: 2, recoverableError: null,
  gates: [{ gateId: '21', gateCode: 'READY', name: '工前准备门禁', gateType: 'ENTRY', persistedStatus: 'PASSED',
    evaluation: { kind: 'CONDITION', ruleVersionRef: 'plan:51:gate:21', outcome: 'UNKNOWN', reasonCode: 'FACT_UNAVAILABLE',
      conditions: [{ key: 'a', path: '$.rules[0]', component: 'pmsRulePredicate', outcome: 'UNKNOWN', reasonCode: 'FACT_UNAVAILABLE' }],
      steps: [], diagnostics: [] }, references: [{ gateReferenceId: '31', refType: 'APPROVAL', refCode: 'review', refVersion: 'review:2:222',
        canStart: false, process: { processInstanceId: null, status: 'UNKNOWN', outcome: 'DEPENDENCY_UNAVAILABLE', reasonCode: 'FACT_UNAVAILABLE' } }] }] })
const render = (instances?: ProjectInstancesVO) => {
  const child = ref<any>(), props = reactive({ projectId: 9, stageCode: 'PREP', projectVersion: 4, instances })
  const view = mount(defineComponent({ setup: () => () => h(StageGateResultsPanel, { ...props, ref: child }) }))
  apps.push(view.app)
  return { ...view, props, component: () => child.value, state: () => child.value.$.setupState }
}
beforeEach(() => { vi.clearAllMocks(); leave.mockResolvedValue(true); api.getStageGateWorkbench.mockResolvedValue(result()) })
afterEach(() => apps.splice(0).forEach(app => app.unmount()))

it('displays current unknown rather than persisted pass, and exposes frozen references and condition diagnostics', async () => {
  const view = render(); await flush()
  expect(api.getStageGateWorkbench).toHaveBeenCalledWith(9, 'PREP')
  expect(textOf(view.root)).toContain('第 2 轮')
  expect(textOf(view.root)).toContain('工前准备门禁')
  expect(textOf(view.root)).toContain('未知')
  expect(textOf(view.root)).toContain('规则条件未知（业务事实暂不可用）')
  expect(textOf(view.root)).toContain('审批')
  expect(textOf(view.root)).not.toContain('PASSED')
  expect(textOf(view.root)).toContain('准入条件')
})

it('refresh clears prior success when the request fails instead of showing stale pass as a fallback', async () => {
  const passed = result(); passed.gates[0].evaluation.outcome = 'MATCHED'
  api.getStageGateWorkbench.mockResolvedValueOnce(passed).mockRejectedValueOnce(new Error('private Owner error'))
  const view = render(); await flush()
  expect(view.state().workbench.gates[0].evaluation.outcome).toBe('MATCHED')
  await view.component().refresh(); await flush()
  expect(view.state().workbench).toBeUndefined()
  expect(textOf(view.root)).toContain('门禁结果读取失败')
  expect(textOf(view.root)).not.toContain('private Owner error')
})

it.each(['foreign-project', 'foreign-stage', 'missing-round', 'unavailable'])('does not render a usable result for %s', async reason => {
  const value = result()
  if (reason === 'foreign-project') value.projectId = '10'
  if (reason === 'foreign-stage') value.stageCode = 'OTHER'
  if (reason === 'missing-round') value.executionId = null
  if (reason === 'unavailable') value.recoverableError = 'STAGE_EXECUTION_UNAVAILABLE'
  api.getStageGateWorkbench.mockResolvedValue(value)
  const view = render(); await flush()
  expect(view.state().workbench).toBeUndefined()
  expect(textOf(view.root)).toContain('计划或执行轮次暂不可用')
})

it('ignores delayed results after selection changes, and reloads on a new project version', async () => {
  let finish!: (value: StageGateWorkbench) => void
  api.getStageGateWorkbench.mockReturnValueOnce(new Promise(resolve => { finish = resolve }))
    .mockResolvedValue(result('DELIVERY'))
  const view = render(); await flush()
  view.props.stageCode = 'DELIVERY'; await flush()
  finish(result()); await flush()
  expect(view.state().workbench.stageCode).toBe('DELIVERY')
  expect(view.state().error).toBe('')
  view.props.projectVersion++; await flush()
  expect(api.getStageGateWorkbench).toHaveBeenCalledTimes(3)
})

it('does not publish a late result after unmount', async () => {
  let finish!: (value: StageGateWorkbench) => void
  api.getStageGateWorkbench.mockReturnValueOnce(new Promise(resolve => { finish = resolve }))
  const view = render(); await flush(); const state = view.state()
  view.app.unmount(); apps.splice(apps.indexOf(view.app), 1)
  finish(result()); await flush()
  expect(state.workbench).toBeUndefined()
})

it('shows the empty state without pretending that an absent gate is an approval', async () => {
  const value = result(); value.gates = []
  api.getStageGateWorkbench.mockResolvedValue(value)
  const view = render(); await flush()
  expect(textOf(view.root)).toContain('当前阶段未配置门禁')
  expect(textOf(view.root)).not.toContain('发起审批流程')
})

it('keeps only one editable form, and preserves it when leaving or refreshing is declined', async () => {
  const value = result(), reference = value.gates[0].references[0]
  reference.canStart = true
  reference.process = { processInstanceId: null, status: 'NOT_STARTED', outcome: 'UNSATISFIED', reasonCode: null }
  value.gates[0].references.push({ ...reference, gateReferenceId: '32' })
  api.getStageGateWorkbench.mockResolvedValue(value)
  const view = render(); await flush()
  await view.state().openForm('31'); await flush()
  expect(textOf(view.root)).toContain('共用审批表单')
  leave.mockResolvedValue(false)
  expect(await view.component().requestLeave()).toBe(false)
  await view.state().openForm('32'); await view.component().refresh()
  expect(view.state().editingId).toBe('31'); expect(api.getStageGateWorkbench).toHaveBeenCalledTimes(1)
  leave.mockResolvedValue(true)
  await view.state().openForm('32'); await flush()
  expect(view.state().editingId).toBe('32')
  expect(textOf(view.root).split('共用审批表单')).toHaveLength(2)
  await view.state().handleSubmitted(); await flush()
  expect(api.getStageGateWorkbench).toHaveBeenCalledTimes(2)
  expect(view.state().editingId).toBeUndefined()
})

const instancesWithStage = (status: string): ProjectInstancesVO => ({
  projectId: 9,
  stages: [{ stageCode: 'PREP', name: '工前准备', sortOrder: 1, status }],
  tasks: [], milestones: [], gates: [],
  deliverables: [{ id: 5, deliverableCode: 'PLAN', name: '施工方案', stageCode: 'PREP', required: true, status: 'PENDING' }]
})

const workbenchWithDeliverableRef = () => {
  const value = result()
  value.gates[0].references.push({ gateReferenceId: '33', refType: 'DELIVERABLE', refCode: 'PLAN', refVersion: 'plan:2', canStart: false } as never)
  return value
}

const workbenchWithStartableApproval = () => {
  const value = workbenchWithDeliverableRef()
  value.gates[0].references[0].canStart = true
  value.gates[0].references[0].process = { processInstanceId: null, status: 'NOT_STARTED', outcome: 'UNSATISFIED', reasonCode: null }
  return value
}

const findButton = (node: TestNode, text: string): TestNode | undefined =>
  node.type === 'button' && textOf(node) === text ? node
    : node.children.map(child => findButton(child, text)).find(Boolean)

it('opens entry approval start for a not-yet-entered stage while hiding submission entries', async () => {
  api.getStageGateWorkbench.mockResolvedValue(workbenchWithStartableApproval())
  const view = render(instancesWithStage('PENDING')); await flush()
  const text = textOf(view.root)
  expect(text).toContain('工前准备门禁')
  expect(text).toContain('施工方案')
  // 注释占位会进textOf，按按钮节点断言提交入口已隐藏（业务操作禁用）
  expect(findButton(view.root, '提交与查看')).toBeUndefined()
  // 审批状态照常展示；准入审批发起入口照常放开（后端 phaseAllowsStart 对未进入阶段的 ENTRY 门禁放行）
  expect(text).toContain('审批')
  expect(text).toContain('未发起')
  expect(findButton(view.root, '填写审批表单')).toBeTruthy()
  expect(text).toContain('刷新门禁结果')
})

it('keeps submission and approval entries once the stage has been entered', async () => {
  api.getStageGateWorkbench.mockResolvedValue(workbenchWithStartableApproval())
  const view = render(instancesWithStage('ACTIVE')); await flush()
  const text = textOf(view.root)
  expect(text).toContain('提交与查看')
  expect(text).toContain('审批')
  expect(findButton(view.root, '填写审批表单')).toBeTruthy()
})
