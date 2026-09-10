import { defineComponent, h, nextTick, onMounted, reactive, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as RequirementAnalysisApi from '@/api/pms/engineering/requirement-analysis'
import {
  buildRequirementFormPatch,
  reconcileRequirementFormPatch,
  requirementFormHasChanges,
  stableRequirementFormIntent
} from './requirementAnalysisInteraction'
import RequirementAnalysisDynamicForm from './RequirementAnalysisDynamicForm.vue'
import {
  findByTestId,
  mount,
  passthrough,
  textOf
} from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

vi.mock('@/api/pms/engineering/requirement-analysis', () => ({ patchForm: vi.fn() }))
vi.mock('./formCreateKeyboardRows', () => ({ vFormCreateKeyboardRows: {} }))
vi.mock('@/views/pms/platform/dynamic-form/components/registerDynamicFormComponents', () => ({
  registerDynamicFormComponents: vi.fn()
}))

const detail = () =>
  ({
    preparationId: 31,
    projectId: 7,
    businessVersion: 2,
    status: 'DRAFT',
    currentDraft: true,
    currentEffective: false,
    contentVersion: 5,
    version: 6,
    dynamicFormInstanceId: 41,
    dynamicFormInstanceVersion: 8,
    dynamicFormTemplateId: 51,
    dynamicFormTemplateRevisionId: 52,
    dynamicFormRevisionNo: 3,
    engineCode: 'FORM_CREATE_ELEMENT_PLUS',
    designerVersion: '3.4.0',
    rendererVersion: '3.2.38',
    formConfJson: {},
    formRulesJson: [
      { type: 'input', field: 'PROJECT_BACKGROUND' },
      { type: 'switch', field: 'requiresCutover' },
      { type: 'inputNumber', field: 'machineCount' },
      { type: 'PmsFileArtifact', field: 'PROJECT_BACKGROUND__ATTACHMENTS' }
    ],
    values: { PROJECT_BACKGROUND: '<p>背景</p>', requiresCutover: false, machineCount: 0 },
    controlledFiles: { PROJECT_BACKGROUND__ATTACHMENTS: [] },
    declarativeValidationResult: 'VALID',
    completionBlockers: [],
    allowedActions: ['PATCH_FORM', 'COMPLETE']
  }) as any

describe('F-SOL-003 requirement analysis dynamic form workspace', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    const values = new Map<string, string>()
    vi.stubGlobal('sessionStorage', {
      getItem: (key: string) => values.get(key) ?? null,
      setItem: (key: string, value: string) => values.set(key, value),
      removeItem: (key: string) => values.delete(key)
    })
  })

  it('keeps unsaved body and renderer rules while host and Owner permissions change, including during save', async () => {
    const state = reactive({ detail: detail(), allowedActions: ['PATCH_FORM'] })
    state.detail.formRulesJson[0].type = 'Editor'
    state.detail.formRulesJson[0].props = { editorConfig: { readOnly: false, placeholder: '背景' } }
    state.detail.formRulesJson.push({ type: 'Editor', field: 'LOCKED_NOTE', props: { readonly: true } })
    const form = ref<any>()
    const rendered: any[] = []
    const FormCreate = defineComponent({
      props: {
        modelValue: { type: Object, required: true },
        rule: { type: Array, required: true },
        disabled: Boolean
      },
      emits: ['update:modelValue'],
      setup(props, { emit }) {
        return () => {
          rendered.push(props.rule)
          return h('div', [
            h('span', `count:${props.modelValue.machineCount}; readonly:${props.disabled}`),
            h(
              'button',
              {
                'data-testid': 'edit-body',
                onClick: () => emit('update:modelValue', { ...props.modelValue, machineCount: 9 })
              },
              'edit'
            )
          ])
        }
      }
    })
    const mounted = mount(
      defineComponent({
        setup: () => () => h(RequirementAnalysisDynamicForm, { ...state, ref: form,
          reload: async () => ({ ...state.detail, contentVersion: 6, version: 7,
            values: { ...state.detail.values, machineCount: 9 } }) })
      }),
      {},
      { 'form-create': FormCreate }
    )
    await nextTick()
    await (findByTestId(mounted.root, 'edit-body')!.props!.onClick as Function)()
    await nextTick()
    const rules = rendered[rendered.length - 1]
    expect(rules.find((rule: any) => rule.field === 'PROJECT_BACKGROUND').props.readonly).toBe(false)
    expect(rules.find((rule: any) => rule.field === 'LOCKED_NOTE').props.readonly).toBe(true)
    state.allowedActions = []
    state.detail = { ...state.detail, allowedActions: [] }
    await nextTick()
    expect(textOf(mounted.root)).toContain('count:9; readonly:true')
    expect(rendered[rendered.length - 1]).toBe(rules)
    expect(rules.find((rule: any) => rule.field === 'PROJECT_BACKGROUND').props.readonly).toBe(true)
    expect(rules.find((rule: any) => rule.field === 'PROJECT_BACKGROUND').props.editorConfig).toMatchObject({ readOnly: true, placeholder: '背景' })
    expect(rules.find((rule: any) => rule.type === 'PmsFileArtifact').props.allowedActions).toEqual(
      []
    )
    expect(form.value.isDirty()).toBe(true)
    expect(await form.value.save()).toBe(false)
    expect(RequirementAnalysisApi.patchForm).not.toHaveBeenCalled()
    state.allowedActions = ['PATCH_FORM']
    state.detail = { ...state.detail, allowedActions: ['PATCH_FORM'] }
    await nextTick()
    expect(textOf(mounted.root)).toContain('count:9; readonly:false')
    expect(rules.find((rule: any) => rule.field === 'PROJECT_BACKGROUND').props.readonly).toBe(false)
    expect(rules.find((rule: any) => rule.field === 'LOCKED_NOTE').props.readonly).toBe(true)
    let finish!: (value: any) => void
    vi.mocked(RequirementAnalysisApi.patchForm).mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          finish = resolve
        })
    )
    const pending = form.value.save()
    await Promise.resolve()
    await nextTick()
    expect(form.value.isSaving()).toBe(true)
    expect(form.value.discardChanges()).toBe(false)
    expect(textOf(mounted.root)).toContain('count:9')
    state.allowedActions = []
    await nextTick()
    expect(form.value.isDirty()).toBe(true)
    finish({ operationId: 'saved' })
    await pending
    expect(RequirementAnalysisApi.patchForm).toHaveBeenCalledTimes(1)
    mounted.app.unmount()
  })
  it('builds a genuine partial PATCH and keeps false and zero as submitted values', () => {
    expect(
      buildRequirementFormPatch(
        { requiresCutover: false, machineCount: 0, untouched: 'same' },
        { requiresCutover: true, machineCount: 4, untouched: 'same' },
        new Set(['requiresCutover', 'machineCount', 'untouched'])
      )
    ).toEqual({ values: { requiresCutover: false, machineCount: 0 } })
  })

  it('retains one idempotency key after an unknown response and reconciles authoritative values', () => {
    const first = stableRequirementFormIntent(31, { machineCount: 0 })
    const retry = stableRequirementFormIntent(31, { machineCount: 0 })
    expect(retry.key).toBe(first.key)
    expect(reconcileRequirementFormPatch({ machineCount: 3 }, { machineCount: 0 })).toEqual({
      committed: false,
      values: { machineCount: 0 }
    })
    expect(reconcileRequirementFormPatch({ machineCount: 0 }, { machineCount: 0 }).committed).toBe(
      true
    )
  })

  it('renders the frozen PLT revision without a project-side template selector and emits dirty state', async () => {
    const dirtyChange = vi.fn()
    const FormCreate = defineComponent({
      props: { modelValue: { type: Object, required: true } },
      emits: ['update:modelValue'],
      setup: (props, { emit, expose }) => {
        expose({ validate: () => Promise.resolve() })
        onMounted(() => emit('update:modelValue', { ...props.modelValue, machineCount: 1 }))
        return () => h('div', { 'data-testid': 'form-create-runtime' }, 'rendered-form')
      }
    })
    const mounted = mount(
      RequirementAnalysisDynamicForm,
      { detail: detail(), onDirtyChange: dirtyChange },
      {
        ElAlert: passthrough,
        ElEmpty: passthrough,
        'form-create': FormCreate
      }
    )
    await nextTick()
    expect((findByTestId(mounted.root, 'form-create-runtime')!.props!.rule as any[]).map(rule => rule.field))
      .toEqual(detail().formRulesJson.map((rule: any) => rule.field))
    expect(textOf(mounted.root)).not.toContain('选择模板')
    expect(findByTestId(mounted.root, 'form-create-runtime')).toBeTruthy()
    expect(
      requirementFormHasChanges({ machineCount: 0 }, { machineCount: 1 }, new Set(['machineCount']))
    ).toBe(true)
    expect(dirtyChange).toHaveBeenCalledWith(true)
    mounted.app.unmount()
  })

  it('renders values received through a reactive parent without cloning Vue proxies', async () => {
    const renderedRules = vi.fn()
    const FormCreate = defineComponent({
      props: {
        modelValue: { type: Object, required: true },
        rule: { type: Array, required: true }
      },
      setup: (props) => {
        onMounted(() => renderedRules(props.rule))
        return () => h('div', { 'data-testid': 'reactive-form-runtime' })
      }
    })
    const Parent = defineComponent({
      setup() {
        const current = detail()
        current.controlledFiles = {
          'FORM_FIELD_ATTACHMENT/PROJECT_BACKGROUND__ATTACHMENTS': [
            { artifactId: 91, versionNo: 1, referenceKey: 'slot-1' }
          ]
        }
        const state = reactive({ detail: current })
        return () => h(RequirementAnalysisDynamicForm, { detail: state.detail })
      }
    })
    const mounted = mount(Parent, {}, { ElAlert: passthrough, 'form-create': FormCreate })
    await nextTick()
    expect(findByTestId(mounted.root, 'reactive-form-runtime')).toBeTruthy()
    expect(
      renderedRules.mock.calls[0][0].find((rule) => rule.type === 'PmsFileArtifact').props
        .currentFacts
    ).toHaveLength(1)
    mounted.app.unmount()
  })

  it('sends both CAS versions and reloads only after a confirmed PATCH', async () => {
    vi.mocked(RequirementAnalysisApi.patchForm).mockResolvedValue({ operationId: 'op-1' })
    const authoritative = detail()
    authoritative.values.machineCount = 1
    authoritative.contentVersion = 6
    authoritative.version = 7
    const reload = vi.fn().mockResolvedValue(authoritative)
    const FormCreate = defineComponent({
      props: { modelValue: { type: Object, required: true } },
      emits: ['update:modelValue'],
      setup: (props, { emit, expose }) => {
        expose({ validate: () => Promise.resolve() })
        onMounted(() => emit('update:modelValue', { ...props.modelValue, machineCount: 1 }))
        return () => h('div')
      }
    })
    const mounted = mount(
      RequirementAnalysisDynamicForm,
      { detail: detail(), reload },
      { ElAlert: passthrough, 'form-create': FormCreate }
    )
    await nextTick()
    const save = findByTestId(mounted.root, 'save-requirement-form')
    await (save?.props?.onClick as () => Promise<void>)()
    expect(RequirementAnalysisApi.patchForm).toHaveBeenCalledWith(31, 8, 6, {
      values: { machineCount: 1 }
    })
    expect(reload).toHaveBeenCalledTimes(1)
    mounted.app.unmount()
  })

  const editableRenderer = defineComponent({
    props: { modelValue: { type: Object, required: true } },
    emits: ['update:modelValue'],
    setup: (props, { emit }) => () => h('div', [
      h('span', `count:${props.modelValue.machineCount}`),
      h('button', {
        'data-testid': 'edit-entity',
        onClick: () => emit('update:modelValue', { ...props.modelValue, machineCount: 1 })
      }, 'edit')
    ])
  })

  it('does not send a write when the host cannot reload the owning entity', async () => {
    const mounted = mount(RequirementAnalysisDynamicForm, { detail: detail() }, {
      'form-create': editableRenderer
    })
    await (findByTestId(mounted.root, 'edit-entity')!.props!.onClick as Function)()
    await nextTick()
    expect(await (findByTestId(mounted.root, 'save-requirement-form')!.props!.onClick as Function)()).toBe(false)
    expect(RequirementAnalysisApi.patchForm).not.toHaveBeenCalled()
    expect(textOf(mounted.root)).toContain('count:1')
    mounted.app.unmount()
  })

  it('uses canonical reloaded data after save and retains the edit if both reload attempts fail', async () => {
    vi.mocked(RequirementAnalysisApi.patchForm).mockResolvedValue({ operationId: 'op-1' })
    const reload = vi.fn().mockRejectedValue(new Error('unavailable'))
    const form = ref<any>()
    const mounted = mount(defineComponent({
      setup: () => () => h(RequirementAnalysisDynamicForm, { detail: detail(), reload, ref: form })
    }), {}, { 'form-create': editableRenderer })
    await (findByTestId(mounted.root, 'edit-entity')!.props!.onClick as Function)()
    await nextTick()
    expect(await form.value.save()).toBe(false)
    expect(form.value.isDirty()).toBe(true)
    expect(sessionStorage.getItem('pms:fsol003:requirement-form-patch:31')).toContain('machineCount')
    expect(reload).toHaveBeenCalledTimes(2)
    const canonical = { ...detail(), contentVersion: 6, version: 7, values: { machineCount: 2 } }
    reload.mockResolvedValue(canonical)
    expect(await form.value.save()).toBe(true)
    expect(textOf(mounted.root)).toContain('count:2')
    expect(form.value.isDirty()).toBe(false)
    expect(sessionStorage.getItem('pms:fsol003:requirement-form-patch:31')).toBeNull()
    mounted.app.unmount()
  })

  it('loads an entity content update without a PLT version change and ignores pending edits on completion', async () => {
    const state = reactive({ detail: detail() })
    const mounted = mount(defineComponent({
      setup: () => () => h(RequirementAnalysisDynamicForm, { detail: state.detail })
    }), {}, { 'form-create': editableRenderer })
    state.detail = { ...state.detail, contentVersion: 6, values: { machineCount: 3 } }
    await nextTick()
    expect(textOf(mounted.root)).toContain('count:3')
    sessionStorage.setItem('pms:fsol003:requirement-form-patch:31', '{"machineCount":99}')
    state.detail = { ...state.detail, status: 'COMPLETED', allowedActions: [], values: { machineCount: 4 } }
    await nextTick()
    expect(textOf(mounted.root)).toContain('count:4')
    expect(textOf(mounted.root)).not.toContain('count:99')
    mounted.app.unmount()
  })
})
