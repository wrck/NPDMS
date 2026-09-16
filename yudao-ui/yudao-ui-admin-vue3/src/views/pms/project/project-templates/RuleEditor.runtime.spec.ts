import { defineComponent, h, nextTick, reactive } from 'vue'
import { expect, it, vi } from 'vitest'
import RuleEditor from './RuleEditor.vue'
import { constantRule } from './versionRuleModel'
import { mount, textOf } from '../../platform/dynamic-form/components/runtimeTestHarness'

const catalogs = vi.hoisted(() => ({ fields: vi.fn(), facts: vi.fn() }))
vi.mock('@/api/pms/project/project-templates/rules', () => ({ getRuleFields: catalogs.fields }))
vi.mock('@/api/pms/project/project-templates', () => ({ getCompletionFactCatalog: catalogs.facts }))
vi.mock('./RuleTreeGroup.vue', () => ({ default: defineComponent({
  props: { fields: { type: Array, required: true }, facts: { type: Array, required: true }, disabled: Boolean },
  setup: props => () => h('div', `${props.disabled}:${props.fields.length}:${props.facts.length}`)
}) }))

it('loads read-only catalogs before shared editing is authorized without changing the condition', async () => {
  catalogs.fields.mockResolvedValue([{ code: 'projectName', label: '项目名称', valueType: 'TEXT', availableAtCreation: true }])
  catalogs.facts.mockResolvedValue([{ factCode: 'SURVEY_CONFIRMED' }])
  const state = reactive({ disabled: true })
  const update = vi.fn()
  const page = mount(defineComponent({ setup: () => () => h(RuleEditor, {
    modelValue: constantRule(true), disabled: state.disabled, 'onUpdate:modelValue': update
  }) }))
  try {
    for (let i = 0; i < 5; i++) { await Promise.resolve(); await nextTick() }
    expect(textOf(page.root)).toContain('true:1:1')
    state.disabled = false; await nextTick()
    expect(textOf(page.root)).toContain('false:1:1')
    expect(catalogs.fields).toHaveBeenCalledOnce()
    expect(update).not.toHaveBeenCalled()
  } finally { page.app.unmount() }
})
