import { defineComponent, h, nextTick, ref, reactive } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as api from '@/api/pms/engineering/requirement-analysis/entity'
import type { View } from '@/api/pms/engineering/requirement-analysis/entity'
import EntityForm from './EntityForm.vue'
import nextTemplate from '../template-20261013.json'
import { formValues, businessPatch } from './entityForm'
import { mount, findByTestId } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

vi.mock('@/api/pms/engineering/requirement-analysis/entity', () => ({ save: vi.fn(), presentations: vi.fn(async () => ({ options: [] })) }))
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
  it('keeps frozen source layout, labels and validation even when a new default template is available', async () => {
    const view = detail()
    view.revision.state = 'FROZEN'; view.allowedActions = []
    view.form!.formConfJson = JSON.stringify({ form: { labelPosition: 'left', labelWidth: '90px' } })
    view.form!.formRulesJson = JSON.stringify([{ type: 'Editor', field: 'PROJECT_BACKGROUND', title: '旧模板标题', props: { height: '240px' }, validate: [{ required: true }] }])
    const next = { ...view.form!, templateId: '99', binding: { ...view.form!.binding, formRevisionId: '100' }, formRulesJson: JSON.stringify(nextTemplate.formRulesJson) }
    vi.mocked(api.presentations).mockResolvedValueOnce({ defaultTemplateId: '99', options: [{ name: '新修订', layout: next }] })
    let rendered: any
    const capture = defineComponent({ props: ['rule', 'option'], setup(props) { rendered = props; return () => h('div') } })
    const mounted = mount(EntityForm, { detail: view }, { 'form-create': capture })
    try {
      for (let i = 0; i < 6; i++) await nextTick()
      expect(rendered.option.form).toMatchObject({ labelPosition: 'left', labelWidth: '90px' })
      expect(rendered.rule[0]).toMatchObject({ title: '旧模板标题', props: { height: '240px', readonly: true }, validate: [{ required: true }] })
    } finally { mounted.app.unmount() }
  })
  it.each(['DRAFT', 'FROZEN'] as const)('renders the original %s template without hiding historical fields or references', async state => {
    const view = detail()
    view.revision.state = state
    view.allowedActions = state === 'DRAFT' ? ['PATCH_FORM'] : []
    const rules = [
      { type: 'input', field: 'PROJECT_BACKGROUND' },
      { type: 'Editor', field: 'NETWORK_TOPOLOGY' },
      { type: 'PmsFileArtifact', field: 'PROJECT_BACKGROUND__ATTACHMENTS' },
      { type: 'PmsFileArtifact', field: 'NETWORK_TOPOLOGY__ATTACHMENTS' },
      { type: 'div', children: [{ type: 'PmsFileArtifact', field: 'TRAFFIC_REQUIREMENT__ATTACHMENTS' }] }
    ]
    view.form!.binding.fieldBindings.NETWORK_TOPOLOGY = 'networkTopology'
    view.values.networkTopology = '<p>历史拓扑文本</p>'
    view.form!.formRulesJson = JSON.stringify(rules)
    view.attachments = [{ key: { purposeCode: 'FORM_FIELD_ATTACHMENT/PROJECT_BACKGROUND__ATTACHMENTS' }, scopeVersion: 3, activeFacts: [] }]
    const before = JSON.stringify(view)
    let rendered: any
    const capture = defineComponent({ props: ['rule', 'modelValue'], setup(props) { rendered = props; return () => h('div') } })
    const mounted = mount(EntityForm, { detail: view }, { 'form-create': capture })
    try {
      await nextTick()
      expect(rendered.rule.map((rule: any) => rule.field).filter(Boolean)).toEqual(['PROJECT_BACKGROUND', 'NETWORK_TOPOLOGY', 'PROJECT_BACKGROUND__ATTACHMENTS', 'NETWORK_TOPOLOGY__ATTACHMENTS'])
      expect(rendered.rule[1].type).toBe('Editor')
      expect(rendered.rule[2].type).toBe('RequirementRevisionFiles')
      expect(rendered.rule[4].children[0].field).toBe('TRAFFIC_REQUIREMENT__ATTACHMENTS')
      expect(rendered.rule[2].props.allowedActions).toEqual(state === 'DRAFT' ? ['PATCH_FORM'] : [])
      expect(JSON.stringify(view)).toBe(before)
      expect(api.save).not.toHaveBeenCalled()
    } finally { mounted.app.unmount() }
  })
  it('renders running business rows as a table and preserves values when permissions become read-only', async () => {
    const view = detail()
    view.fieldCatalog.push({ code: 'businessDeviceDetails', type: 'OBJECT_LIST', required: false })
    view.form!.binding.fieldBindings.BUSINESS_DEVICE_DETAILS = 'businessDeviceDetails'
    view.form!.formRulesJson = JSON.stringify([nextTemplate.formRulesJson.find(rule => rule.field === 'BUSINESS_DEVICE_DETAILS')])
    const rows = [{ deviceName: '设备A', serialNumber: 'SN-A', remark: '保留备注' }]
    view.values.businessDeviceDetails = rows
    let rendered: any
    const capture = defineComponent({ props: ['rule', 'modelValue'], setup(props) {
      rendered = props
      return () => h('div')
    } })
    const permissions = ref(['PATCH_FORM'])
    const mounted = mount(defineComponent({ setup: () => () => h(EntityForm, { detail: view, allowedActions: permissions.value }) }), {}, { 'form-create': capture })
    try {
      await nextTick()
      expect(rendered.rule[0]).toMatchObject({ type: 'RequirementBusinessTable', field: 'BUSINESS_DEVICE_DETAILS', props: { disabled: false } })
      expect(rendered.modelValue.BUSINESS_DEVICE_DETAILS).toEqual(rows)
      permissions.value = []; await nextTick(); await nextTick()
      expect(rendered.rule[0].props.disabled).toBe(true)
      expect(rendered.rule[0].props.columns).toEqual(nextTemplate.formRulesJson.find(rule => rule.field === 'BUSINESS_DEVICE_DETAILS')!.props!.columns)
      expect(rendered.modelValue.BUSINESS_DEVICE_DETAILS).toEqual(rows)
    } finally { mounted.app.unmount() }
  })
  it('initializes unpublished values for R2 multi-select and device rows as arrays and saves them as extensions', () => {
    const view = detail()
    view.form!.binding.fieldBindings.tags = 'tags'
    view.form!.binding.fieldBindings.devices = 'devices'
    view.form!.fields = [
      { fieldKey: 'tags', componentType: 'checkbox', controlledFile: false, required: false },
      { fieldKey: 'devices', componentType: 'group', controlledFile: false, required: false }
    ]
    expect(formValues(view)).toMatchObject({ tags: [], devices: [] })
    expect(businessPatch(view, { tags: ['IPv6'], devices: [{ serialNumber: 'SN001' }] }).extensionValues)
      .toMatchObject({ tags: ['IPv6'], devices: [{ serialNumber: 'SN001' }] })
  })
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
  it('reopens nested reactive extension rows without cloning a Vue proxy', async () => {
    const view = reactive(detail()), form = ref<any>()
    view.form!.binding.fieldBindings.devices = 'devices'
    view.values.devices = [{ serialNumber: 'SN001' }]
    const mounted = mount(defineComponent({ setup: () => () => h(EntityForm, { detail: view, ref: form }) }), {}, { 'form-create': renderer })
    await nextTick()
    expect(form.value.isDirty()).toBe(false)
    mounted.app.unmount()
  })
  it('switches presentation while retaining hidden unsaved business values and frozen read-only state', async () => {
    const view = detail(), form = ref<any>()
    const layout = { ...view.form!, binding: { ...view.form!.binding, formRevisionId: '61', fieldBindings: { count: 'count' } },
      formRulesJson: JSON.stringify([{ type: 'inputNumber', field: 'count' }]) }
    vi.mocked(api.presentations).mockResolvedValue({ options: [{ name: '简表', layout }] })
    vi.mocked(api.save).mockResolvedValue(view.revision)
    const reload = async () => ({ ...view, revision: { ...view.revision, version: 7 }, values: { projectBackground: 'changed', count: 0, enabled: false } })
    const mounted = mount(defineComponent({ setup: () => () => h(EntityForm, { detail: view, reload, ref: form }) }), {}, { 'form-create': renderer })
    await nextTick(); await nextTick()
    await (findByTestId(mounted.root, 'edit')!.props!.onClick as Function)(); await nextTick()
    form.value.switchPresentation('61'); await nextTick()
    expect(form.value.isDirty()).toBe(true)
    expect(api.save).not.toHaveBeenCalled()
    expect(await form.value.save()).toBe(true)
    expect(api.save).toHaveBeenCalledWith(view.revision, { values: { projectBackground: 'changed' }, expectedExtensionVersion: 3,
      extensionDefinitionRevisionId: '80', extensionValues: { count: 0, enabled: false } }, expect.any(String))
    mounted.app.unmount()
    const frozen = { ...view, revision: { ...view.revision, state: 'FROZEN' as const }, allowedActions: [] }
    const readonly = mount(defineComponent({ setup: () => () => h(EntityForm, { detail: frozen, ref: form }) }), {}, { 'form-create': renderer })
    await nextTick(); form.value.switchPresentation('base'); await nextTick()
    expect(await form.value.save()).toBe(false)
    expect(api.save).toHaveBeenCalledTimes(1)
    readonly.app.unmount()
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
