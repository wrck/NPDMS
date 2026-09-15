import { computed, defineComponent, h, nextTick, provide, reactive } from 'vue'
import { expect, it, vi } from 'vitest'
import type { JsonObject } from '@/api/pms/project/project-templates'
import RelativeTimeConditionEditor from './RelativeTimeConditionEditor.vue'
import { relativeTimeOptionsKey, type RelativeTimeOptions } from './relativeTimeModel'
import { decodeTree, encodeTree } from './ruleTreeModel'
import { mount, passthrough, textOf, type TestNode } from '../../platform/dynamic-form/components/runtimeTestHarness'

const find = (node: TestNode, predicate: (node: TestNode) => boolean): TestNode | undefined =>
  predicate(node) ? node : node.children.map(child => find(child, predicate)).find(Boolean)
const setup = (parameters: JsonObject) => {
  const state = reactive<{ parameters: JsonObject; disabled: boolean; options: RelativeTimeOptions }>({
    parameters, disabled: false, options: { available: true, activation: true, sources: [
      { key: 'survey', label: '任务 · 现场工勘', disabled: false }, { key: 'self', label: '任务 · 本节点', disabled: true }
    ] }
  })
  const changed = vi.fn((value: JsonObject) => { state.parameters = value })
  const view = mount(defineComponent({ setup() {
    provide(relativeTimeOptionsKey, computed(() => state.options))
    return () => h(RelativeTimeConditionEditor, { parameters: state.parameters, disabled: state.disabled, onChange: changed })
  } }), {}, { ElSelect: passthrough, ElOption: passthrough, ElInputNumber: passthrough })
  const field = (label: string) => {
    const node = find(view.root, item => item.props?.['aria-label'] === label)
    expect(node, label).toBeDefined(); return node!
  }
  const set = async (label: string, value: unknown) => {
    ;(field(label).props!['onUpdate:modelValue'] as (value: unknown) => void)(value)
    await nextTick()
  }
  return { ...view, state, changed, field, set }
}

it('reopens a mixed duration unchanged and changes units without changing its meaning', async () => {
  const original = { anchor: 'NODE_ACTIVATED', duration: 'P1DT2H30M' }
  const view = setup(original)
  expect(view.field('等待时长').props?.['model-value']).toBe(1590)
  await view.set('等待时长单位', 'hours')
  expect(view.field('等待时长').props?.['model-value']).toBe(26.5)
  expect(view.changed).not.toHaveBeenCalled(); expect(view.state.parameters).toEqual(original)
  await view.set('等待时长', 2)
  expect(view.state.parameters).toEqual({ anchor: 'NODE_ACTIVATED', duration: 'PT7200S' })
  expect(encodeTree(decodeTree({ predicate: 'WAIT_ELAPSED', parameters: view.state.parameters })).parameters).toEqual(view.state.parameters)
  await view.set('等待时长', undefined); expect(view.state.parameters.duration).toBe('')
  view.app.unmount()
})

it('keeps version sources explicit and removes obsolete parameters only on intentional anchor changes', async () => {
  const view = setup({ anchor: 'NODE_ACTIVATED', duration: 'PT30M' })
  await view.set('等待起算点', 'NODE_COMPLETED')
  expect(view.state.parameters).toEqual({ anchor: 'NODE_COMPLETED', sourceNodeKey: '', duration: 'PT30M' })
  await view.set('等待来源节点', 'self'); expect(view.state.parameters.sourceNodeKey).toBe('')
  await view.set('等待来源节点', 'survey'); expect(view.state.parameters.sourceNodeKey).toBe('survey')
  view.state.options.sources = []; await nextTick()
  expect(textOf(view.root)).toContain('请选择本版本中其他来源')
  expect(find(view.root, node => node.props?.label === '来源节点已移除，请重新选择')).toBeDefined()
  expect(view.state.parameters.sourceNodeKey).toBe('survey')
  await view.set('等待起算点', 'NODE_ACTIVATED')
  expect(view.state.parameters).toEqual({ anchor: 'NODE_ACTIVATED', duration: 'PT30M' })
  view.app.unmount()
})

it('retains invalid/shared-context conditions and rejects disabled or stale editing events', async () => {
  const view = setup({ anchor: 'NODE_ACTIVATED', duration: 'P1M' })
  expect(textOf(view.root)).toContain('无法按经过时长显示')
  view.state.options.activation = false; await nextTick()
  expect(textOf(view.root)).toContain('准入或收口不能等待自身激活')
  await view.set('等待起算点', 'NODE_ACTIVATED'); expect(view.changed).not.toHaveBeenCalled()
  view.state.disabled = true; await nextTick()
  await view.set('等待起算点', 'NODE_COMPLETED'); await view.set('等待时长', 60)
  expect(view.changed).not.toHaveBeenCalled()
  view.state.disabled = false; view.state.options.available = false; await nextTick()
  await view.set('等待起算点', 'NODE_COMPLETED'); await view.set('等待时长', 60)
  expect(view.changed).not.toHaveBeenCalled(); expect(view.state.parameters.duration).toBe('P1M')
  view.app.unmount()
})
