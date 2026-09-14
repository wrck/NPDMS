import { computed, defineComponent, h, nextTick, provide, reactive } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import type { JsonObject } from '@/api/pms/project/project-templates'
import RulePredicateEditor from './RulePredicateEditor.vue'
import { ruleBusinessSourcesKey, type RuleBusinessSource } from './ruleBusinessSources'
import { decodeTree, encodeTree } from './ruleTreeModel'
import { mount, passthrough, type TestNode } from '../../platform/dynamic-form/components/runtimeTestHarness'

vi.mock('./DecisionTableConditionEditor.vue', () => ({ default: { render: () => null } }))
const find = (node: TestNode, predicate: (node: TestNode) => boolean): TestNode | undefined =>
  predicate(node) ? node : node.children.map((child) => find(child, predicate)).find(Boolean)

describe('business fact source editing', () => {
  it('stores an absolute instant across timezone display and tree reopening', async () => {
    const state = reactive<{ parameters: JsonObject }>({ parameters: { at: '2026-09-15T09:00:00+08:00' } })
    const host = defineComponent({ setup: () => () => h(RulePredicateEditor, {
      predicate: 'TIME_REACHED', parameters: state.parameters, fields: [], facts: [],
      onChange: (_type, value) => { state.parameters = value }
    }) })
    const view = mount(host, {}, { ElSelect: passthrough, ElOption: passthrough, ElDatePicker: passthrough, ElInput: passthrough })
    const picker = find(view.root, (node) => node.props?.['aria-label'] === '绝对时间点')!
    expect((picker.props!['model-value'] as Date).toISOString()).toBe('2026-09-15T01:00:00.000Z')
    ;(picker.props!['onUpdate:modelValue'] as (value: Date | null) => void)(new Date('2026-09-16T09:00:00+08:00'))
    await nextTick()
    expect(state.parameters.at).toBe('2026-09-16T01:00:00.000Z')
    expect(encodeTree(decodeTree({ predicate: 'TIME_REACHED', parameters: state.parameters })).parameters).toEqual(state.parameters)
    ;(picker.props!['onUpdate:modelValue'] as (value: Date | null) => void)(null)
    await nextTick()
    expect(state.parameters.at).toBe('')
    view.app.unmount()
  })
  it('selects a version-local source, filters Owner facts and preserves it through a tree round trip', async () => {
    const state = reactive<{ parameters: JsonObject; sources: RuleBusinessSource[] }>({
      parameters: { factCode: 'SURVEY_CONFIRMED', quantifier: 'ALL' },
      sources: [{ key: 'prep', label: '阶段 · 工前准备', ownerContext: 'SOL', objectType: 'SURVEY' }]
    })
    const host = defineComponent({ setup() {
      provide(ruleBusinessSourcesKey, computed(() => state.sources))
      return () => h(RulePredicateEditor, { predicate: 'BUSINESS_FACT', parameters: state.parameters, fields: [],
        facts: [
          { ownerContext: 'SOL', objectType: 'SURVEY', factCode: 'SURVEY_CONFIRMED', label: '工勘完成' },
          { ownerContext: 'ACC', objectType: 'REPORT', factCode: 'REPORT_EFFECTIVE', label: '报告生效' }
        ], onChange: (_type, value) => { state.parameters = value } })
    } })
    const view = mount(host, {}, { ElSelect: passthrough, ElOption: passthrough })
    const select = find(view.root, (node) => node.props?.['aria-label'] === '业务结果来源节点')!
    ;(select.props!['onUpdate:modelValue'] as (value: string) => void)('prep')
    await nextTick()
    expect(state.parameters.sourceNodeKey).toBe('prep')
    expect(find(view.root, (node) => node.props?.value === 'REPORT_EFFECTIVE')).toBeUndefined()
    expect(find(view.root, (node) => node.props?.value === 'SURVEY_CONFIRMED')).toBeDefined()
    const tree = encodeTree(decodeTree({ predicate: 'BUSINESS_FACT', parameters: state.parameters }))
    expect(JSON.stringify(tree)).toContain('"sourceNodeKey":"prep"')
    state.sources = []
    await nextTick()
    expect(find(view.root, (node) => node.props?.label === '来源节点已移除，请重新选择')).toBeDefined()
    expect(state.parameters.sourceNodeKey).toBe('prep')
    ;(select.props!['onUpdate:modelValue'] as (value: string) => void)('$current')
    await nextTick()
    expect(state.parameters).not.toHaveProperty('sourceNodeKey')
    expect(state.parameters.factCode).toBe('SURVEY_CONFIRMED')
    view.app.unmount()
  })
})
