import { defineComponent, h, nextTick, onMounted, reactive } from 'vue'
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
    expect(textOf(mounted.root)).toContain('冻结模板修订 3')
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
    authoritative.dynamicFormInstanceVersion = 9
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
})
