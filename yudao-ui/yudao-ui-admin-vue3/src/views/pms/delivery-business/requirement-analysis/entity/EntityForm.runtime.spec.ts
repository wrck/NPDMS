import { defineComponent, h, nextTick, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as api from '@/api/pms/engineering/requirement-analysis/entity'
import type { View } from '@/api/pms/engineering/requirement-analysis/entity'
import EntityForm from './EntityForm.vue'
import { formValues, businessPatch } from './entityForm'
import { mount, findByTestId } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

vi.mock('@/api/pms/engineering/requirement-analysis/entity', () => ({ save: vi.fn() }))
vi.mock('@form-create/element-ui', () => ({ default: { component: vi.fn() } }))
vi.mock('./RevisionFiles.vue', () => ({ default: defineComponent(() => () => h('div')) }))
vi.mock('@/views/pms/platform/dynamic-form/components/registerDynamicFormComponents', () => ({ registerDynamicFormComponents: vi.fn() }))
vi.mock('@/views/pms/platform/dynamic-form/components/dynamicFormCodec', () => ({ decodeDynamicForm: (option: any, rule: any) => ({ option, rule }) }))
vi.mock('@/views/pms/project/project-master-detail/components/formCreateKeyboardRows', () => ({ vFormCreateKeyboardRows: {} }))

const detail = (): View => ({
  projectId: '7', revision: { ref: { entity: { tenantId: '1', ownerModule: 'SOL', entityType: 'REQUIREMENT_ANALYSIS', entityId: '20' }, revisionId: '31' }, revisionNo: 2, state: 'DRAFT', effective: false, version: 6 },
  entityVersion: 2, extensionDefinitionRevisionId: '80', extensionValueVersion: 3,
  fieldCatalog: [{ code: 'projectBackground', type: 'TEXT', required: true }],
  form: { binding: { formRevisionId: '52', version: 1, extensionDefinitionRevisionId: '80', fieldBindings: { PROJECT_BACKGROUND: 'projectBackground', count: 'count', enabled: 'enabled' } }, templateId: '51', revisionNo: 3, formVersion: 1, engineCode: 'FORM_CREATE_ELEMENT_PLUS', designerVersion: '3.4.0', rendererVersion: '3.2.38', formConfJson: '{}', formRulesJson: JSON.stringify([{ type: 'input', field: 'PROJECT_BACKGROUND' }, { type: 'inputNumber', field: 'count' }, { type: 'switch', field: 'enabled' }, { type: 'PmsFileArtifact', field: 'PROJECT_BACKGROUND__ATTACHMENTS' }]), fields: [] },
  values: { projectBackground: 'original', count: 4, enabled: true }, attachments: [], allowedActions: ['PATCH_FORM']
})
const renderer = defineComponent({
  props: { modelValue: { type: Object, required: true }, rule: { type: Array, required: true } }, emits: ['update:modelValue'],
  setup(props, { emit }) { return () => h('button', { 'data-testid': 'edit', onClick: () => emit('update:modelValue', { ...props.modelValue, PROJECT_BACKGROUND: 'changed', count: 0, enabled: false }) }, 'edit') }
})
beforeEach(() => {
  vi.clearAllMocks()
  const values = new Map<string,string>()
  vi.stubGlobal('sessionStorage', { getItem: (key: string) => values.get(key) ?? null, setItem: (key: string, value: string) => values.set(key,value), removeItem: (key: string) => values.delete(key) })
})
describe('independent requirement entity form', () => {
  it('maps fixed and extension fields without losing false, zero or untouched extensions', () => {
    const view = detail()
    expect(formValues(view)).toEqual({ PROJECT_BACKGROUND: 'original', count: 4, enabled: true })
    expect(businessPatch(view, { PROJECT_BACKGROUND: 'changed', count: 0 })).toEqual({ values: { projectBackground: 'changed' }, extensionDefinitionRevisionId: '80', expectedExtensionVersion: 3, extensionValues: { count: 0, enabled: true } })
    expect(businessPatch(view, { enabled: false }).extensionValues).toEqual({ count: 4, enabled: false })
    expect(() => businessPatch(view, { version: 99 })).toThrow('字段未绑定')
  })
  it('saves through entity and revision identity and confirms authoritative content', async () => {
    const view = detail(), form = ref<any>()
    const reload = vi.fn(async () => ({ ...view, revision: { ...view.revision, version: 7 }, values: { projectBackground: 'changed', count: 0, enabled: false } }))
    const mounted = mount(defineComponent({ setup: () => () => h(EntityForm, { detail: view, reload, ref: form }) }), {}, { 'form-create': renderer })
    await nextTick()
    await (findByTestId(mounted.root, 'edit')!.props!.onClick as Function)(); await nextTick()
    expect(await form.value.save()).toBe(true)
    expect(api.save).toHaveBeenCalledWith(view.revision, { values: { projectBackground: 'changed' }, expectedExtensionVersion: 3, extensionDefinitionRevisionId: '80', extensionValues: { count: 0, enabled: false } }, expect.any(String))
    expect(reload).toHaveBeenCalledOnce(); expect(form.value.isDirty()).toBe(false)
    mounted.app.unmount()
  })
  it('keeps local changes on failed save and prevents writing a frozen revision', async () => {
    const view = detail(), form = ref<any>()
    vi.mocked(api.save).mockRejectedValue(new Error('conflict'))
    const mounted = mount(defineComponent({ setup: () => () => h(EntityForm, { detail: view, reload: async () => view, ref: form }) }), {}, { 'form-create': renderer })
    await nextTick(); await (findByTestId(mounted.root, 'edit')!.props!.onClick as Function)(); await nextTick()
    expect(await form.value.save()).toBe(false); expect(form.value.isDirty()).toBe(true)
    mounted.app.unmount()
    const frozen = { ...detail(), revision: { ...view.revision, state: 'FROZEN' as const }, allowedActions: [] }
    const readonly = mount(defineComponent({ setup: () => () => h(EntityForm, { detail: frozen, ref: form }) }), {}, { 'form-create': renderer })
    await nextTick(); expect(await form.value.save()).toBe(false); expect(api.save).toHaveBeenCalledTimes(1)
    readonly.app.unmount()
  })
})
