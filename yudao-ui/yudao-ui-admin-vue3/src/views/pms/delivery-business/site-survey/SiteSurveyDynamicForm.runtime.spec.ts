import { defineComponent, h, nextTick, ref } from 'vue'
import { expect, it, vi } from 'vitest'
import { getFormSchema } from '@/api/pms/engineering/site-survey/entity'
import type { SiteSurveyVO } from '@/api/pms/engineering/site-survey/entity'
import Form from './SiteSurveyDynamicForm.vue'
import { mount, findByTestId } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
vi.mock('@/api/pms/engineering/site-survey/entity', () => ({ getFormSchema: vi.fn() }))
vi.mock('@form-create/element-ui', () => ({ default: { component: vi.fn() } }))
vi.mock('@/views/pms/platform/dynamic-form/components/dynamicFormCodec', () => ({ decodeDynamicForm: (option: any, rule: any) => ({ option, rule }) }))
vi.mock('./SurveyMaterialSelector.vue', () => ({ default: defineComponent(() => () => null) }))
vi.mock('./SurveyProjectEndDate.vue', () => ({ default: defineComponent(() => () => null) }))
const renderer = defineComponent({ props: ['modelValue', 'rule', 'disabled'], emits: ['update:modelValue'],
  setup(props, { emit }) { return () => h('button', { 'data-testid': 'edit', onClick: () => emit('update:modelValue', { ...props.modelValue, customCabinet: false, count: 0 }) }, JSON.stringify(props.modelValue)) } })
const flush = async () => { for (let i = 0; i < 10; i++) { await nextTick(); await Promise.resolve() } }
it('loads the new schema while using the stored object binding and preserving untouched extension values', async () => {
  vi.mocked(getFormSchema).mockResolvedValue({ revisionId: 6, revisionVersion: 2, formConfJson: {}, formRulesJson: [],
    fieldBindings: { customCabinet: 'other' }, fieldCatalog: [{ code: 'cabinetReady', type: 'BOOLEAN', required: false }] })
  const model = ref<SiteSurveyVO>({ id: 3, projectId: 7, code: 'S', name: 'survey', formRevisionId: 6, formRevisionVersion: 2,
    businessValues: { cabinetReady: true }, extensionValues: { count: 2, retained: 'keep' }, fieldBindings: { customCabinet: 'cabinetReady', count: 'count' } })
  const view = mount(defineComponent({ setup: () => () => h(Form, { modelValue: model.value, readonly: false,
    'onUpdate:modelValue': (value: SiteSurveyVO) => { model.value = value } }) }), {}, { 'form-create': renderer })
  await flush()
  expect(getFormSchema).toHaveBeenCalledWith(6, 2)
  await (findByTestId(view.root, 'edit')!.props!.onClick as Function)(); await flush()
  expect(model.value.businessValues).toEqual({ cabinetReady: false })
  expect(model.value.extensionValues).toEqual({ count: 0, retained: 'keep' })
  view.app.unmount()
})
