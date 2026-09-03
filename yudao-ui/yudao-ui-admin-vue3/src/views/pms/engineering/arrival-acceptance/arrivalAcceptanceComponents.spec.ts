import { defineComponent, h, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ArrivalAcceptanceForm from './components/ArrivalAcceptanceForm.vue'
import ArrivalDifferencePanel from './components/ArrivalDifferencePanel.vue'
import ArrivalEvidencePanel from './components/ArrivalEvidencePanel.vue'
import ArrivalLineEditor from './components/ArrivalLineEditor.vue'
import {
  findByTestId,
  mount,
  passthrough,
  type TestNode
} from '../../platform/dynamic-form/components/runtimeTestHarness'

const fileApi = vi.hoisted(() => ({ getArtifact: vi.fn(), getVersions: vi.fn() }))
vi.mock('@/api/pms/platform/file', () => fileApi)
vi.mock('@/api/pms/project/projects', () => ({
  __esModule: true,
  __v_isRef: false,
  getProjectPage: vi.fn()
}))
vi.mock('@/components/PmsFileArtifact', () => ({
  PmsFileUploader: defineComponent({
    inheritAttrs: false,
    emits: ['completed'],
    setup(_, { attrs, emit }) {
      return () =>
        h(
          'button',
          {
            ...attrs,
            'data-testid': 'file-uploader',
            onClick: () =>
              emit('completed', {
                artifactId: '9007199254740993',
                referenceKey: 'receipt-slot',
                versionNo: 4
              })
          },
          'upload'
        )
    }
  })
}))

const dialog = defineComponent({
  inheritAttrs: false,
  setup(_, { attrs, slots }) {
    return () => h('section', attrs, [slots.default?.(), slots.footer?.()])
  }
})
const validatedForm = defineComponent({
  inheritAttrs: false,
  setup(_, { attrs, slots, expose }) {
    expose({ validate: () => Promise.resolve(true) })
    return () => h('form', attrs, slots.default?.())
  }
})

const controls = {
  Dialog: dialog,
  ElForm: validatedForm,
  ElFormItem: passthrough,
  ElInput: passthrough,
  ElInputNumber: passthrough,
  ElDatePicker: passthrough,
  ElSelect: passthrough,
  ElOption: passthrough,
  ElSwitch: passthrough,
  PmsEntitySelect: passthrough,
  ElTag: passthrough,
  ElEmpty: passthrough
}

const update = async (root: TestNode, testId: string, value: unknown) => {
  const node = findByTestId(root, testId)
  expect(node, `${testId} should be mounted`).toBeDefined()
  await (node!.props?.['onUpdate:modelValue'] as (value: unknown) => unknown)(value)
  await nextTick()
}

const click = async (root: TestNode, testId: string) => {
  const node = findByTestId(root, testId)
  expect(node, `${testId} should be mounted`).toBeDefined()
  await (node!.props?.onClick as () => unknown)()
  await nextTick()
}

const findNode = (
  node: TestNode,
  predicate: (candidate: TestNode) => boolean
): TestNode | undefined => {
  if (predicate(node)) return node
  for (const child of node.children) {
    const found = findNode(child, predicate)
    if (found) return found
  }
}

const evidenceRevision = {
  artifactId: '9007199254740993',
  referenceKey: 'receipt-slot',
  versionNo: 4,
  scopeVersion: 17,
  fileFactVersion: { artifactVersion: 2, referenceVersion: 3, availabilityVersion: 5 },
  hash: 'sha256-value'
}

const quantityDifference = {
  id: 31,
  arrivalLineId: 21,
  differenceNo: 1,
  revisionNo: 2,
  differenceType: 'QUANTITY_MISMATCH',
  resolutionStatus: 'REJECTED',
  reason: '短少',
  riskDescription: '影响上线',
  scopeSnapshot: {
    scopeType: 'ORDER_MODEL_QUANTITY' as const,
    orderLineId: 11,
    productCode: 'P-1',
    modelCode: null,
    quantity: 5,
    unitCode: '台'
  },
  approvedBy: null,
  approvedAt: null,
  exemptionExpiresAt: null,
  evidenceId: null,
  evidenceRevision: null,
  current: true,
  projectFactVersion: null,
  factImpactType: null,
  version: 7
}

describe('F-IMP-002 arrival acceptance mounted components', () => {
  beforeEach(() => {
    fileApi.getArtifact.mockReset()
    fileApi.getVersions.mockReset()
  })

  it('emits epoch milliseconds from create and correction forms', async () => {
    const creates: any[] = []
    const create = mount(
      ArrivalAcceptanceForm,
      { modelValue: true, detail: null, onCreate: (value: unknown) => creates.push(value) },
      controls
    )
    await update(create.root, 'project-id', '9007199254740993')
    await update(create.root, 'batch-code', 'ARR-01')
    await update(create.root, 'logistics-no', 'LOG-01')
    await update(create.root, 'signer-name', '张三')
    await update(create.root, 'arrival-time', '1788055200000')
    await update(create.root, 'delivery-scope-version', 17)
    await click(create.root, 'save-arrival')
    expect(creates[0]).toMatchObject({
      projectId: '9007199254740993',
      arrivedAt: 1788055200000
    })
    expect(typeof creates[0].arrivedAt).toBe('number')
    create.app.unmount()

    const patches: any[] = []
    const patch = mount(
      ArrivalAcceptanceForm,
      {
        modelValue: true,
        detail: {
          id: 1,
          projectId: 2,
          batchCode: 'ARR-01',
          logisticsNo: 'LOG-01',
          signerName: '张三',
          arrivedAt: 1788055200000,
          deliveryScopeVersion: 17,
          currentLines: []
        },
        onPatch: (value: unknown) => patches.push(value)
      },
      controls
    )
    await update(patch.root, 'arrival-time', '1788057000000')
    await click(patch.root, 'save-arrival')
    expect(patches[0].arrivedAt).toBe(1788057000000)
    patch.app.unmount()

    const corrections: any[] = []
    const correction = mount(
      ArrivalAcceptanceForm,
      {
        modelValue: true,
        correction: true,
        detail: {
          id: 1,
          projectId: 2,
          batchCode: 'ARR-01',
          logisticsNo: 'LOG-01',
          signerName: '张三',
          arrivedAt: '1788055200000',
          deliveryScopeVersion: 17,
          currentLines: []
        },
        onCorrect: (value: unknown) => corrections.push(value)
      },
      controls
    )
    await update(correction.root, 'arrival-time', '1788058800000')
    await update(correction.root, 'correction-reason', '纠正到货时间')
    await click(correction.root, 'save-arrival')
    expect(corrections[0].patch.arrivedAt).toBe(1788058800000)
    correction.app.unmount()
  })

  it('mounts server action matrix, partial supplement, exemption epoch and mobile dialog width', async () => {
    const readonly = mount(
      ArrivalDifferencePanel,
      {
        differences: [quantityDifference],
        aggregateStatus: 'CONFIRMED',
        canResolve: false,
        evidenceRevision
      },
      controls
    )
    expect(findByTestId(readonly.root, 'resolve-SUPPLEMENT')).toBeUndefined()
    readonly.app.unmount()

    const resolved: any[] = []
    const panel = mount(
      ArrivalDifferencePanel,
      {
        differences: [quantityDifference],
        aggregateStatus: 'CONFIRMED',
        canResolve: true,
        evidenceRevision,
        onResolve: (value: unknown) => resolved.push(value)
      },
      controls
    )
    expect(findByTestId(panel.root, 'resolve-KEEP_REJECTED')).toBeUndefined()
    await click(panel.root, 'resolve-SUPPLEMENT')
    await update(panel.root, 'supplement-quantity', 2)
    await update(panel.root, 'resolution-reason', '补签两台')
    await click(panel.root, 'submit-resolution')
    expect(resolved[0].supplementScope.quantity).toBe(2)

    await click(panel.root, 'resolve-EXEMPT')
    await update(panel.root, 'exemption-expiry', '1788141600000')
    await update(panel.root, 'resolution-reason', '经理批准具体豁免')
    await click(panel.root, 'submit-resolution')
    expect(resolved[1].expiresAt).toBe(1788141600000)
    const dialog = findNode(panel.root, (node) => node.props?.title === '处理到货差异')
    expect(dialog?.props?.width).toBe('min(560px, 94vw)')
    panel.app.unmount()
  })

  it('keeps PLT Snowflake identity and emits only stable reference facts', async () => {
    fileApi.getArtifact.mockResolvedValue({
      name: '签收单.pdf',
      artifactVersion: 2,
      reference: { scopeVersion: 17, referenceVersion: 3 }
    })
    fileApi.getVersions.mockResolvedValue({
      items: [{ versionNo: 4, availabilityVersion: 5, sha256: 'sha256-value' }]
    })
    const revisions: any[] = []
    const evidence = mount(
      ArrivalEvidencePanel,
      {
        acceptanceId: '9007199254740995',
        evidence: null,
        editable: true,
        onRevision: (value: unknown) => revisions.push(value)
      },
      controls
    )
    await click(evidence.root, 'file-uploader')
    await Promise.resolve()
    await Promise.resolve()
    await Promise.resolve()
    await nextTick()
    expect(fileApi.getArtifact).toHaveBeenCalledWith(
      '9007199254740993',
      expect.objectContaining({ objectId: '9007199254740995' })
    )
    expect(revisions[0]).toEqual(evidenceRevision)
    expect(JSON.stringify(revisions[0])).not.toContain('url')
    evidence.app.unmount()
  })

  it('mounts line editing without changing a Snowflake string device id', async () => {
    const updates: any[] = []
    const lines = mount(
      ArrivalLineEditor,
      {
        modelValue: [],
        editable: true,
        'onUpdate:modelValue': (value: unknown) => updates.push(value)
      },
      controls
    )
    await click(lines.root, 'append-line')
    expect(updates[0][0]).toMatchObject({ scopeType: 'DEVICE', deviceId: '' })
    lines.app.unmount()
  })
})
