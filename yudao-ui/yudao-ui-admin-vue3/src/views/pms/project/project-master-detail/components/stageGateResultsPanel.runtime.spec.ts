import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, reactive, ref } from 'vue'
import StageGateResultsPanel from './StageGateResultsPanel.vue'
import { mount, textOf } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
import type { StageGateWorkbench } from '@/api/pms/project/stage-gates'

const api = vi.hoisted(() => ({ getStageGateWorkbench: vi.fn() }))
vi.mock('@/api/pms/project/stage-gates', () => api)
const apps: { unmount: () => void }[] = []
const flush = async () => { for (let i = 0; i < 6; i++) { await Promise.resolve(); await nextTick() } }
const result = (stageCode = 'PREP'): StageGateWorkbench => ({ projectId: '9', projectVersion: 4, planVersionId: '51',
  stageId: '11', stageCode, executionId: '61', executionRound: 2, recoverableError: null,
  gates: [{ gateId: '21', gateCode: 'READY', name: '工前准备门禁', gateType: 'ENTRY', persistedStatus: 'PASSED',
    evaluation: { kind: 'CONDITION', ruleVersionRef: 'plan:51:gate:21', outcome: 'UNKNOWN', reasonCode: 'FACT_UNAVAILABLE',
      conditions: [{ key: 'a', path: '$.rules[0]', component: 'pmsRulePredicate', outcome: 'UNKNOWN', reasonCode: 'FACT_UNAVAILABLE' }],
      steps: [], diagnostics: [] }, references: [{ gateReferenceId: '31', refType: 'APPROVAL', refCode: 'review', refVersion: 'review:2:222' }] }] })
const render = () => {
  const child = ref<any>(), props = reactive({ projectId: 9, stageCode: 'PREP', projectVersion: 4 })
  const view = mount(defineComponent({ setup: () => () => h(StageGateResultsPanel, { ...props, ref: child }) }))
  apps.push(view.app)
  return { ...view, props, component: () => child.value, state: () => child.value.$.setupState }
}
beforeEach(() => { vi.clearAllMocks(); api.getStageGateWorkbench.mockResolvedValue(result()) })
afterEach(() => apps.splice(0).forEach(app => app.unmount()))

it('displays current unknown rather than persisted pass, and exposes frozen references and condition diagnostics', async () => {
  const view = render(); await flush()
  expect(api.getStageGateWorkbench).toHaveBeenCalledWith(9, 'PREP')
  expect(textOf(view.root)).toContain('第 2 轮')
  expect(textOf(view.root)).toContain('工前准备门禁')
  expect(textOf(view.root)).toContain('未知')
  expect(textOf(view.root)).toContain('review:2:222')
  expect(textOf(view.root)).toContain('pmsRulePredicate')
  expect(textOf(view.root)).not.toContain('PASSED')
  expect(textOf(view.root)).toContain('条件与引用明细')
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
