import { defineComponent, h, nextTick, ref, reactive, shallowRef, watch } from 'vue'
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
  const topologyDetail = (availabilityStatus = 'AVAILABLE', referenceStatus = 'ACTIVE'): View => {
    const view = detail()
    view.form!.formRulesJson = JSON.stringify([
      ...JSON.parse(view.form!.formRulesJson), { type: 'PmsFileArtifact', field: 'NETWORK_TOPOLOGY__ATTACHMENTS' }
    ])
    view.attachments = [{ key: { purposeCode: 'FORM_FIELD_ATTACHMENT/NETWORK_TOPOLOGY__ATTACHMENTS' }, scopeVersion: '31',
      activeFacts: [{ artifactId: '71', versionNo: 1, referenceKey: 'topology', availabilityStatus, referenceStatus } as any] }]
    return view
  }

  it('validates a just-uploaded topology against authoritative facts without losing ordinary edits', async () => {
    const current = topologyDetail(), stale = { ...current, attachments: [] }, form = ref<any>()
    const reload = vi.fn(async () => current)
    const mounted = mount(defineComponent({ setup: () => () => h(EntityForm, { detail: stale, reload, ref: form }) }), {}, { 'form-create': renderer })
    try {
      await nextTick()
      await (findByTestId(mounted.root, 'edit')!.props!.onClick as Function)(); await nextTick()
      expect(await form.value.validateCompletion()).toBe(true)
      expect(reload).toHaveBeenCalledOnce()
      expect(stale.attachments).toEqual([])
      expect(form.value.isDirty()).toBe(true)
      expect(api.save).not.toHaveBeenCalled()
    } finally { mounted.app.unmount() }
  })

  it.each(['missing', 'unavailable', 'detached'])('rejects stale topology evidence when the Owner now reports it %s', async (state) => {
    const stale = topologyDetail(), form = ref<any>()
    const current = state === 'missing' ? { ...stale, attachments: [] }
      : topologyDetail(state === 'unavailable' ? 'UNAVAILABLE' : 'AVAILABLE', state === 'detached' ? 'DETACHED' : 'ACTIVE')
    const reload = vi.fn(async () => current)
    const mounted = mount(defineComponent({ setup: () => () => h(EntityForm, { detail: stale, reload, ref: form }) }), {}, { 'form-create': renderer })
    try {
      await nextTick()
      expect(await form.value.validateCompletion()).toBe(false)
      expect(reload).toHaveBeenCalledOnce()
    } finally { mounted.app.unmount() }
  })

  it('refuses completion when authoritative attachment refresh fails', async () => {
    const form = ref<any>(), reload = vi.fn(async () => { throw new Error('Owner unavailable') })
    const mounted = mount(defineComponent({ setup: () => () => h(EntityForm, { detail: topologyDetail(), reload, ref: form }) }), {}, { 'form-create': renderer })
    try {
      await nextTick()
      expect(await form.value.validateCompletion()).toBe(false)
      expect(reload).toHaveBeenCalledOnce()
      expect(form.value.isSaving()).toBe(false)
    } finally { mounted.app.unmount() }
  })

  it('never consumes attachment facts from another selected revision', async () => {
    const original = topologyDetail(), form = ref<any>()
    const reload = vi.fn(async () => ({ ...original, revision: { ...original.revision,
      ref: { ...original.revision.ref, revisionId: '99' } } }))
    const mounted = mount(defineComponent({ setup: () => () => h(EntityForm, { detail: original, reload, ref: form }) }), {}, { 'form-create': renderer })
    try {
      await nextTick()
      expect(await form.value.validateCompletion()).toBe(false)
    } finally { mounted.app.unmount() }
  })

  it('blocks repeated checks and host switching until authoritative attachment reads finish', async () => {
    const current = topologyDetail(), form = ref<any>()
    let finish!: (value: View) => void
    const reload = vi.fn(() => new Promise<View>(resolve => { finish = resolve }))
    const mounted = mount(defineComponent({ setup: () => () => h(EntityForm, { detail: current, reload, ref: form }) }), {}, { 'form-create': renderer })
    try {
      await nextTick()
      const checking = form.value.validateCompletion()
      expect(form.value.isSaving()).toBe(true)
      expect(await form.value.validateCompletion()).toBe(false)
      expect(reload).toHaveBeenCalledOnce()
      finish(current)
      expect(await checking).toBe(true)
      expect(form.value.isSaving()).toBe(false)
    } finally { mounted.app.unmount() }
  })

  it('discards pending attachment checks when the selected revision changes', async () => {
    const current = topologyDetail(), state = reactive({ detail: current }), form = ref<any>()
    let finish!: (value: View) => void
    const reload = () => new Promise<View>(resolve => { finish = resolve })
    const mounted = mount(defineComponent({ setup: () => () => h(EntityForm, { detail: state.detail, reload, ref: form }) }), {}, { 'form-create': renderer })
    try {
      await nextTick()
      const checking = form.value.validateCompletion()
      state.detail = { ...current, revision: { ...current.revision, ref: { ...current.revision.ref, revisionId: '99' } } }
      await nextTick()
      finish(current)
      expect(await checking).toBe(false)
      expect(form.value.isSaving()).toBe(false)
      expect(api.save).not.toHaveBeenCalled()
    } finally { mounted.app.unmount() }
  })

  it('reloads authoritative attachment facts when saving without ordinary field edits', async () => {
    const view = detail(), form = ref<any>()
    const reload = vi.fn(async () => view)
    const mounted = mount(defineComponent({ setup: () => () => h(EntityForm, { detail: view, reload, ref: form }) }), {}, { 'form-create': renderer })
    try {
      await nextTick()
      expect(await form.value.save()).toBe(true)
      expect(reload).toHaveBeenCalledOnce()
      expect(api.save).not.toHaveBeenCalled()
    } finally { mounted.app.unmount() }
  })
  it('does not report an unchanged form saved when authoritative reload fails', async () => {
    const form = ref<any>()
    const mounted = mount(defineComponent({ setup: () => () => h(EntityForm, { detail: detail(), reload: async () => { throw new Error('unavailable') }, ref: form }) }), {}, { 'form-create': renderer })
    try {
      await nextTick()
      expect(await form.value.save()).toBe(false)
      expect(form.value.isSaving()).toBe(false)
      expect(api.save).not.toHaveBeenCalled()
    } finally { mounted.app.unmount() }
  })
  it('blocks save and completion guards until every attachment field finishes uploading', async () => {
    const view = detail(), form = ref<any>()
    const rules = JSON.parse(view.form!.formRulesJson)
    rules.push({ type: 'PmsFileArtifact', field: 'NETWORK_TOPOLOGY__ATTACHMENTS' })
    view.form!.formRulesJson = JSON.stringify(rules)
    let rendered: any
    const uploadRenderer = defineComponent({
      props: { modelValue: Object, rule: Array }, emits: ['update:modelValue'],
      setup(props, { emit }) {
        rendered = props
        return () => h('button', { 'data-testid': 'edit', onClick: () => emit('update:modelValue',
          { ...props.modelValue, PROJECT_BACKGROUND: 'changed', count: 0, enabled: false }) }, 'edit')
      }
    })
    vi.mocked(api.save).mockResolvedValue(view.revision)
    const reload = vi.fn(async () => ({ ...view, revision: { ...view.revision, version: 7 },
      values: { projectBackground: 'changed', count: 0, enabled: false } }))
    const mounted = mount(defineComponent({ setup: () => () => h(EntityForm, { detail: view, reload, ref: form }) }),
      {}, { 'form-create': uploadRenderer })
    try {
      await nextTick()
      ;(findByTestId(mounted.root, 'edit')!.props!.onClick as () => void)()
      await nextTick()
      const uploads = rendered.rule.filter((rule: any) => rule.type === 'RequirementRevisionFiles')
      uploads.forEach((rule: any) => rule.props.onUploadBusyChange(true))
      await nextTick()
      expect(findByTestId(mounted.root, 'save-requirement-form')!.props!.disabled).toBe(true)
      expect(form.value.isSaving()).toBe(true)
      expect(await form.value.save()).toBe(false)
      expect(api.save).not.toHaveBeenCalled()
      uploads[0].props.onUploadBusyChange(false)
      expect(await form.value.save()).toBe(false)
      uploads[1].props.onUploadBusyChange(false)
      await nextTick()
      expect(form.value.isSaving()).toBe(false)
      expect(await form.value.save()).toBe(true)
      expect(api.save).toHaveBeenCalledOnce()
      expect(reload).toHaveBeenCalledOnce()
    } finally { mounted.app.unmount() }
  })

  it('uses the latest compatible revision of the configured template for a clean draft', async () => {
    const view = detail()
    const layout = (revisionNo: number, id: string) => ({ ...view.form!, revisionNo,
      binding: { ...view.form!.binding, formRevisionId: id },
      formRulesJson: JSON.stringify([{ type: 'input', field: 'PROJECT_BACKGROUND', title: `revision-${revisionNo}` }]) })
    vi.mocked(api.presentations).mockResolvedValueOnce({ defaultTemplateId: '51', defaultRevisionId: '52', options: [
      { name: 'original', layout: layout(3, '52') }, { name: 'latest', layout: layout(10, '60') }
    ] })
    let rendered: any
    const capture = defineComponent({ props: { rule: Array }, setup(props) { rendered = props; return () => h('div') } })
    const mounted = mount(EntityForm, { detail: view }, { 'form-create': capture })
    try {
      for (let i = 0; i < 6; i++) await nextTick()
      expect(rendered.rule[0].title).toBe('revision-10')
      expect(api.save).not.toHaveBeenCalled()
    } finally { mounted.app.unmount() }
  })
  it('keeps frozen source layout, labels and validation even when a new default template is available', async () => {
    const view = detail()
    view.revision.state = 'FROZEN'; view.allowedActions = []
    view.form!.formConfJson = JSON.stringify({ form: { labelPosition: 'left', labelWidth: '90px' } })
    view.form!.formRulesJson = JSON.stringify([{ type: 'Editor', field: 'PROJECT_BACKGROUND', title: '旧模板标题', props: { height: '240px' }, validate: [{ required: true }] }])
    const next = { ...view.form!, templateId: '99', binding: { ...view.form!.binding, formRevisionId: '100' }, formRulesJson: JSON.stringify(nextTemplate.formRulesJson) }
    vi.mocked(api.presentations).mockResolvedValueOnce({ defaultTemplateId: '99', options: [{ name: '新修订', layout: next }] })
    let rendered: any
    const capture = defineComponent({ props: { rule: Array, option: Object }, setup(props) { rendered = props; return () => h('div') } })
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
    const capture = defineComponent({ props: { rule: Array, modelValue: Object }, setup(props) { rendered = props; return () => h('div') } })
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
    const capture = defineComponent({ props: { rule: Array, modelValue: Object }, setup(props) {
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
  it.each(['parent', 'return-only', 'same-object'])('applies saved attachment rules once for %s reload', async mode => {
    const selected = shallowRef(detail()), form = ref<any>(), ruleChanges = vi.fn()
    const original = selected.value
    const authoritative = { ...original, revision: { ...original.revision, version: 7 },
      values: { projectBackground: 'changed', count: 0, enabled: false } }
    let rendered: any
    const capture = defineComponent({
      props: { modelValue: Object, rule: Array }, emits: ['update:modelValue'],
      setup(props, { emit }) {
        rendered = props
        watch(() => props.rule, ruleChanges)
        return () => h('button', { 'data-testid': 'edit', onClick: () => emit('update:modelValue',
          { ...props.modelValue, PROJECT_BACKGROUND: 'changed', count: 0, enabled: false }) })
      }
    })
    vi.mocked(api.save).mockResolvedValue(original.revision)
    const reload = vi.fn(async () => {
      if (mode === 'parent') { selected.value = authoritative; await nextTick() }
      if (mode === 'same-object') { Object.assign(original, authoritative); return original }
      return authoritative
    })
    const mounted = mount(defineComponent({ setup: () => () => h(EntityForm,
      { detail: selected.value, reload, ref: form }) }), {}, { 'form-create': capture })
    try {
      await nextTick()
      ;(findByTestId(mounted.root, 'edit')!.props!.onClick as () => void)()
      await nextTick()
      ruleChanges.mockClear()
      expect(await form.value.save()).toBe(true)
      await nextTick()
      expect(ruleChanges).toHaveBeenCalledOnce()
      expect(rendered.modelValue).toMatchObject({ PROJECT_BACKGROUND: 'changed', count: 0, enabled: false })
      expect(form.value.isDirty()).toBe(false)
      expect(sessionStorage.getItem('pms:requirement-entity:patch:31')).toBeNull()
    } finally { mounted.app.unmount() }
  })
  it.each([true, false])('applies failed-save readback once and preserves uncommitted edits: %s', async committed => {
    const selected = ref(detail()), form = ref<any>(), ruleChanges = vi.fn()
    const original = selected.value
    const authoritative = { ...original, revision: { ...original.revision, version: 7 },
      values: committed ? { projectBackground: 'changed', count: 0, enabled: false } : original.values }
    const capture = defineComponent({
      props: { modelValue: Object, rule: Array }, emits: ['update:modelValue'],
      setup(props, { emit }) {
        watch(() => props.rule, ruleChanges)
        return () => h('button', { 'data-testid': 'edit', onClick: () => emit('update:modelValue',
          { ...props.modelValue, PROJECT_BACKGROUND: 'changed', count: 0, enabled: false }) })
      }
    })
    vi.mocked(api.save).mockRejectedValue(new Error('response lost'))
    const reload = vi.fn(async () => { selected.value = authoritative; await nextTick(); return authoritative })
    const mounted = mount(defineComponent({ setup: () => () => h(EntityForm,
      { detail: selected.value, reload, ref: form }) }), {}, { 'form-create': capture })
    try {
      await nextTick()
      ;(findByTestId(mounted.root, 'edit')!.props!.onClick as () => void)()
      await nextTick()
      ruleChanges.mockClear()
      expect(await form.value.save()).toBe(committed)
      await nextTick()
      expect(ruleChanges).toHaveBeenCalledOnce()
      expect(form.value.isDirty()).toBe(!committed)
      expect(sessionStorage.getItem('pms:requirement-entity:patch:31') === null).toBe(committed)
    } finally { mounted.app.unmount() }
  })
  it('retains edits and the command intent when both authoritative reads fail', async () => {
    const view = detail(), form = ref<any>()
    vi.mocked(api.save).mockResolvedValue(view.revision)
    const reload = vi.fn(async () => { throw new Error('read unavailable') })
    const mounted = mount(defineComponent({ setup: () => () => h(EntityForm,
      { detail: view, reload, ref: form }) }), {}, { 'form-create': renderer })
    try {
      await nextTick()
      ;(findByTestId(mounted.root, 'edit')!.props!.onClick as () => void)()
      await nextTick()
      expect(await form.value.save()).toBe(false)
      expect(reload).toHaveBeenCalledTimes(2)
      expect(form.value.isDirty()).toBe(true)
      expect(sessionStorage.getItem('pms:requirement-entity:patch:31')).not.toBeNull()
      expect(sessionStorage.getItem('pms:fplt002:requirement-save:31')).not.toBeNull()
    } finally { mounted.app.unmount() }
  })
  it.each(['revision', 'project'])('rejects readback when the selected %s changes during the render tick', async selection => {
    const selected = ref(detail()), form = ref<any>(), saved = vi.fn()
    const original = selected.value
    const authoritative = { ...original, revision: { ...original.revision, version: 7 },
      values: { projectBackground: 'changed', count: 0, enabled: false } }
    const other = { ...detail(), projectId: selection === 'project' ? '8' : original.projectId,
      revision: { ...original.revision, ref: { ...original.revision.ref,
        revisionId: selection === 'revision' ? '99' : original.revision.ref.revisionId } } }
    vi.mocked(api.save).mockResolvedValue(original.revision)
    const reload = vi.fn(async () => {
      void nextTick(() => { selected.value = other })
      return authoritative
    })
    const mounted = mount(defineComponent({ setup: () => () => h(EntityForm,
      { detail: selected.value, reload, ref: form, onSaved: saved }) }), {}, { 'form-create': renderer })
    try {
      await nextTick()
      ;(findByTestId(mounted.root, 'edit')!.props!.onClick as () => void)()
      await nextTick()
      expect(await form.value.save()).toBe(false)
      expect(saved).not.toHaveBeenCalled()
      expect(sessionStorage.getItem('pms:requirement-entity:patch:31')).not.toBeNull()
    } finally { mounted.app.unmount() }
  })
  it('keeps the existing attachment rules while a post-save permission projection becomes read-only', async () => {
    const selected = ref(detail()), permissions = ref(['PATCH_FORM']), form = ref<any>()
    let rendered: any
    const capture = defineComponent({
      props: { modelValue: Object, rule: Array }, emits: ['update:modelValue'],
      setup(props, { emit }) {
        rendered = props
        return () => h('button', { 'data-testid': 'edit', onClick: () => emit('update:modelValue',
          { ...props.modelValue, PROJECT_BACKGROUND: 'changed', count: 0, enabled: false }) })
      }
    })
    vi.mocked(api.save).mockResolvedValue(selected.value.revision)
    const reload = vi.fn(async () => {
      selected.value = { ...selected.value, revision: { ...selected.value.revision, version: 7 },
        values: { projectBackground: 'changed', count: 0, enabled: false } }
      return selected.value
    })
    const mounted = mount(defineComponent({ setup: () => () => h(EntityForm,
      { detail: selected.value, reload, allowedActions: permissions.value, ref: form }) }), {}, { 'form-create': capture })
    try {
      await nextTick()
      ;(findByTestId(mounted.root, 'edit')!.props!.onClick as () => void)()
      await nextTick()
      expect(await form.value.save()).toBe(true)
      await nextTick()
      const rules = rendered.rule
      permissions.value = []
      await nextTick(); await nextTick()
      expect(rendered.rule).toBe(rules)
      expect(rendered.rule.find((rule: any) => rule.type === 'RequirementRevisionFiles').props.allowedActions).toEqual([])
      expect(rendered.modelValue.PROJECT_BACKGROUND).toBe('changed')
      expect(form.value.isDirty()).toBe(false)
      expect(await form.value.save()).toBe(false)
      expect(api.save).toHaveBeenCalledOnce()
    } finally { mounted.app.unmount() }
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
    let resolvePresentations!: (value: any) => void
    vi.mocked(api.presentations).mockImplementationOnce(() => new Promise(resolve => { resolvePresentations = resolve }))
    vi.mocked(api.save).mockResolvedValue(view.revision)
    const reload = async () => ({ ...view, revision: { ...view.revision, version: 7 }, values: { projectBackground: 'changed', count: 0, enabled: false } })
    const mounted = mount(defineComponent({ setup: () => () => h(EntityForm, { detail: view, reload, ref: form }) }), {}, { 'form-create': renderer })
    await nextTick(); await nextTick()
    await (findByTestId(mounted.root, 'edit')!.props!.onClick as Function)(); await nextTick()
    resolvePresentations({ options: [{ name: '简表', layout }] }); await nextTick(); await nextTick()
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
