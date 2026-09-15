import { computed, defineComponent, h, nextTick, provide, reactive } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import type { JsonObject } from '@/api/pms/project/project-templates'
import RulePredicateEditor from './RulePredicateEditor.vue'
import { ruleBusinessSourcesKey, type RuleBusinessSource } from './ruleBusinessSources'
import { ruleNativeOptionsKey } from './ruleNativeOptions'
import { relativeTimeOptionsKey } from './relativeTimeModel'
import { decodeTree, encodeTree } from './ruleTreeModel'
import { mount, passthrough, type TestNode } from '../../platform/dynamic-form/components/runtimeTestHarness'

vi.mock('./DecisionTableConditionEditor.vue', () => ({ default: { render: () => null } }))
const find = (node: TestNode, predicate: (node: TestNode) => boolean): TestNode | undefined =>
  predicate(node) ? node : node.children.map((child) => find(child, predicate)).find(Boolean)

describe('business fact source editing', () => {
  it('defaults admission waits to an explicit source and prevents use for template matching', async () => {
    const state = reactive({ available: true, activation: false, sources: [] })
    const change = vi.fn()
    const host = defineComponent({ setup() {
      provide(relativeTimeOptionsKey, computed(() => state))
      return () => h(RulePredicateEditor, { predicate: 'CONSTANT', parameters: { value: false }, fields: [], facts: [], onChange: change })
    } })
    const view = mount(host, {}, { ElSelect: passthrough, ElOption: passthrough })
    const selector = find(view.root, node => node.props?.['aria-label'] === '条件类型')!
    const select = selector.props!['onUpdate:modelValue'] as (value: string) => void
    select('WAIT_ELAPSED')
    expect(change).toHaveBeenCalledWith('WAIT_ELAPSED', { anchor: 'NODE_COMPLETED', sourceNodeKey: '', duration: '' })
    change.mockClear(); state.available = false; await nextTick()
    expect(find(view.root, node => node.props?.value === 'WAIT_ELAPSED')?.props?.disabled).toBe(true)
    select('WAIT_ELAPSED'); expect(change).not.toHaveBeenCalled()
    view.app.unmount()
  })
  it('disables incompatible native choices and retains the existing condition when context changes', async () => {
    const state = reactive({ allowed: ['TASK_NATIVE_STATUS'], disabled: false })
    const change = vi.fn()
    const host = defineComponent({ setup() {
      provide(ruleNativeOptionsKey, computed(() => state.allowed))
      return () => h(RulePredicateEditor, { predicate: 'TASK_NATIVE_STATUS', parameters: { requiredStatus: 'DONE' },
        fields: [], facts: [], disabled: state.disabled, onChange: change })
    } })
    const view = mount(host, {}, { ElSelect: passthrough, ElOption: passthrough })
    expect(find(view.root, node => node.props?.value === 'STAGE_NATIVE_STATUS')?.props?.disabled).toBe(true)
    expect(find(view.root, node => node.props?.value === 'TASK_NATIVE_STATUS')?.props?.disabled).toBe(false)
    const selector = find(view.root, node => node.props?.['aria-label'] === '条件类型')!
    const select = selector.props!['onUpdate:modelValue'] as (value: string) => void
    select('STAGE_NATIVE_STATUS'); expect(change).not.toHaveBeenCalled()
    state.allowed = []; await nextTick()
    expect(find(view.root, node => node.props?.role === 'alert')).toBeDefined()
    expect(selector.props?.['model-value']).toBe('TASK_NATIVE_STATUS')
    expect(change).not.toHaveBeenCalled()
    select('TASK_NATIVE_STATUS'); expect(change).not.toHaveBeenCalled()
    select('CONSTANT'); expect(change).toHaveBeenCalledWith('CONSTANT', { value: false })
    change.mockClear(); state.disabled = true; await nextTick()
    select('CONSTANT'); expect(change).not.toHaveBeenCalled()
    view.app.unmount()
  })
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
