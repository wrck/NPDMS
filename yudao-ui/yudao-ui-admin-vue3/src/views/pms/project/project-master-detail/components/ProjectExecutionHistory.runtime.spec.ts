import { defineComponent, h, inject, nextTick, provide, ref, type PropType } from 'vue'
import { expect, it, vi } from 'vitest'
import type { ExecutionHistory } from '@/api/pms/project/projects/nodeExecutions'
import { mount, passthrough, textOf } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
import ProjectExecutionHistory from './ProjectExecutionHistory.vue'

const api = vi.hoisted(() => ({ getExecutionHistory: vi.fn() }))
vi.mock('@/api/pms/project/projects/nodeExecutions', () => api)
vi.mock('@/utils/formatTime', () => ({ formatDate: (value: unknown) => String(value ?? '') }))

const row = defineComponent({
  props: { value: Object as PropType<Record<string, unknown>> },
  setup: (props, { slots }) => {
    provide('history-test-row', props.value)
    return () => h('section', slots.default?.())
  }
})
const table = defineComponent({
  props: { data: Array as PropType<Record<string, unknown>[]> },
  setup: (props, { slots }) => () => h('section', (props.data ?? []).map(value => h(row, { value }, slots)))
})
const column = defineComponent({
  setup: (_, { slots }) => {
    const current = inject('history-test-row')
    return () => h('section', slots.default?.({ row: current }))
  }
})

it.each([true, false])('renders recorded gate evidence without labelling it as exit (gate=%s)', async hasGate => {
  const evaluation = { kind: 'CONDITION' as const, ruleVersionRef: 'plan:51:rule:complete', outcome: 'MATCHED' as const,
    conditions: [], steps: ['pmsRulePredicate'], diagnostics: [] }
  const history: ExecutionHistory = { plans: [], rounds: [{
    id: 31, planVersionId: 51, planRevisionNo: 1, nodeKey: 'task:analysis', nodeKind: 'TASK',
    nodeCode: 'ANALYSIS', name: '需求分析', roundNo: 1, current: false, status: 'DONE', canViewSubmissionNote: false,
    evaluations: [
      { purpose: 'completion', name: '完成条件', result: evaluation },
      { purpose: 'exit', name: '退出条件', result: evaluation },
      ...(hasGate ? [{ purpose: 'gate', name: '原工勘门禁', result: { ...evaluation, ruleVersionRef: 'plan:51:gate:8:version:4' } }] : [])
    ]
  }] }
  api.getExecutionHistory.mockReset().mockResolvedValue(history)
  const visible = ref(false)
  const page = mount(defineComponent({ setup: () => () => h(ProjectExecutionHistory, { projectId: 9, modelValue: visible.value }) }),
    {}, { ElDialog: passthrough, ElTable: table, ElTableColumn: column })
  try {
    visible.value = true
    for (let i = 0; i < 8; i++) { await Promise.resolve(); await nextTick() }
    expect(api.getExecutionHistory).toHaveBeenCalledWith(9)
    const text = textOf(page.root)
    expect(text).toContain('完成 · 完成条件 · 满足')
    expect(text).toContain('退出 · 退出条件 · 满足')
    expect(text.includes('门禁 · 原工勘门禁 · 满足')).toBe(hasGate)
    expect(text).not.toContain('退出 · 原工勘门禁')
    expect(text).toContain('历史轮次')
    expect(text).toContain('pmsRulePredicate')
  } finally { page.app.unmount() }
})
