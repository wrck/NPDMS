import { computed, defineComponent, h, nextTick, provide } from 'vue'
import { expect, it, vi } from 'vitest'
import type { RuleSimulation, VersionRule } from '@/api/pms/project/project-templates/rules'
import RuleSimulationPanel from './RuleSimulationPanel.vue'
import { relativeTimeOptionsKey } from './relativeTimeModel'
import { mount, passthrough, textOf, type TestNode } from '../../platform/dynamic-form/components/runtimeTestHarness'

const simulate = vi.hoisted(() => vi.fn())
vi.mock('@/api/pms/project/project-templates/rules', () => ({ simulateRule: simulate }))
const find = (node: TestNode, predicate: (node: TestNode) => boolean): TestNode | undefined =>
  predicate(node) ? node : node.children.map(child => find(child, predicate)).find(Boolean)

it('distinguishes each source clock and submits dates under their exact input keys without inventing missing values', async () => {
  const rules: VersionRule[] = [{ key: 'wait', name: '等待', kind: 'CONDITION', shared: false, expression: {
    predicate: 'WAIT_ELAPSED', parameters: { anchor: 'NODE_COMPLETED', duration: 'PT30M', sourceNodeKey: 'survey' }
  } }]
  const response: RuleSimulation = { el: 'native', decisions: {}, inputs: [
    { key: 'clock.now', label: '模拟当前时间', valueType: 'DATETIME' },
    { key: 'clock.completed:survey', label: '模拟起算时间', valueType: 'DATETIME' },
    { key: 'clock.completed:analysis', label: '模拟起算时间', valueType: 'DATETIME' },
    { key: 'clock.activation', label: '模拟起算时间', valueType: 'DATETIME' }
  ], evaluation: { kind: 'CONDITION', outcome: 'UNKNOWN', ruleVersionRef: 'simulation:wait', conditions: [], steps: [], diagnostics: [] } }
  simulate.mockResolvedValue(response)
  const view = mount(defineComponent({ setup() {
    provide(relativeTimeOptionsKey, computed(() => ({ available: true, activation: false, sources: [
      { key: 'survey', label: '任务 · 现场工勘（SURVEY）', disabled: false },
      { key: 'analysis', label: '任务 · 需求分析（ANALYSIS）', disabled: false }
    ] })))
    return () => h(RuleSimulationPanel, { rules, ruleKey: 'wait' })
  } }), {}, { ElCollapse: passthrough, ElCollapseItem: passthrough, ElForm: passthrough, ElFormItem: passthrough, ElInput: passthrough })
  const run = async () => {
    const button = find(view.root, node => node.type === 'button' && textOf(node) === '试算')!
    await (button.props!.onClick as () => Promise<void>)(); await nextTick()
  }
  await run(); expect(simulate).toHaveBeenLastCalledWith(rules, 'wait', {})
  const input = (label: string) => {
    const formItem = find(view.root, node => node.props?.label === label)
    expect(formItem, label).toBeDefined()
    const control = find(formItem!, node => !!node.props?.['onUpdate:modelValue'])!
    return control.props!['onUpdate:modelValue'] as (value: string) => void
  }
  input('模拟当前时间')('2026-09-15T10:00:00+08:00')
  input('任务 · 现场工勘（SURVEY） · 本轮完成时间（含时区）')('2026-09-15T09:00:00+08:00')
  input('任务 · 需求分析（ANALYSIS） · 本轮完成时间（含时区）')('')
  input('模拟本节点本轮激活时间（含时区）')('')
  await run()
  expect(simulate).toHaveBeenLastCalledWith(rules, 'wait', { 'clock.now': '2026-09-15T10:00:00+08:00', 'clock.completed:survey': '2026-09-15T09:00:00+08:00' })
  view.app.unmount()
})
