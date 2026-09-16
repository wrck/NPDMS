import { defineComponent, h, nextTick, ref } from 'vue'
import { afterEach, expect, it, vi } from 'vitest'
import formCreateEngine from '@form-create/element-ui'
import { getFormSchema } from '@/api/pms/engineering/site-survey/entity'
import type { SiteSurveyVO } from '@/api/pms/engineering/site-survey/entity'
import Form from './SiteSurveyDynamicForm.vue'
import { mount, findByTestId, passthrough } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

vi.mock('@/api/pms/engineering/site-survey/entity', () => ({ getFormSchema: vi.fn() }))
vi.mock('@/views/pms/platform/dynamic-form/components/dynamicFormCodec', () => ({ decodeDynamicForm: (option: any, rule: any) => ({ option, rule }) }))
vi.mock('./SurveyMaterialSelector.vue', () => ({ default: defineComponent(() => () => null) }))
vi.mock('./SurveyProjectEndDate.vue', () => ({ default: defineComponent(() => () => null) }))
const flush = async () => { for (let i = 0; i < 15; i++) { await nextTick(); await Promise.resolve() } }
afterEach(() => vi.unstubAllGlobals())

// Keep the actual FormCreate engine and checkbox adapter; replace only DOM controls.
const checkboxGroup = defineComponent({ props: ['modelValue'], emits: ['update:modelValue'],
  setup(props, { emit, slots }) { return () => h('button', { 'data-testid': 'choices',
    onClick: () => emit('update:modelValue', props.modelValue.includes('AC') ? [] : ['AC', 'DC']) }, slots.default?.()) } })
const formItem = defineComponent({ setup(_, { slots, expose }) {
  expose({ validate: async () => true, clearValidate: () => {} })
  return () => h('section', slots.default?.())
} })

it('selects, saves and clears multiple choices through the real form engine', async () => {
  vi.stubGlobal('document', { cookie: '', head: { appendChild: vi.fn(), removeChild: vi.fn() },
    createElement: () => ({ style: {}, appendChild: vi.fn() }) })
  vi.mocked(getFormSchema).mockResolvedValue({ revisionId: 6, revisionVersion: 2, formConfJson: {},
    formRulesJson: [{ type: 'checkbox', field: 'extra_powerTypes', options: [{ value: 'AC', label: 'AC' }, { value: 'DC', label: 'DC' }] }],
    fieldBindings: { extra_powerTypes: 'powerTypes' }, fieldCatalog: [{ code: 'powerTypes', type: 'TEXT_LIST', required: false }] })
  const model = ref<SiteSurveyVO>({ projectId: 7, code: 'S', name: 'survey', formRevisionId: 6, formRevisionVersion: 2,
    businessValues: {}, extensionValues: {} })
  const view = mount(defineComponent({ setup: () => () => h(Form, { modelValue: model.value, readonly: false,
    'onUpdate:modelValue': (value: SiteSurveyVO) => { model.value = value } }) }), {}, {
    'form-create': formCreateEngine.$form(), ElRow: passthrough, ElCol: passthrough,
    ElCheckboxGroup: checkboxGroup, ElCheckbox: passthrough, ElFormItem: formItem
  })
  await flush()
  await (findByTestId(view.root, 'choices')!.props!.onClick as Function)(); await flush()
  expect(model.value.businessValues?.powerTypes).toEqual(['AC', 'DC'])
  expect(model.value.extensionValues).toEqual({})
  await (findByTestId(view.root, 'choices')!.props!.onClick as Function)(); await flush()
  expect(model.value.businessValues?.powerTypes).toEqual([])
  view.app.unmount()
})
