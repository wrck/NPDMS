import { defineComponent, h, nextTick } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import type { CreateContextCandidate } from '@/api/pms/cutover/cutover-task'
import CutoverAssessmentPanel from './components/CutoverAssessmentPanel.vue'
import CutoverCreateWizard from './components/CutoverCreateWizard.vue'
import {
  activeCutoverStagePanel,
  buildCreateRequest,
  gradeDestination,
  parseSerials
} from './cutoverTaskInteraction'
import {
  mount,
  findByTestId,
  passthrough,
  textOf,
  type TestNode
} from '../../platform/dynamic-form/components/runtimeTestHarness'

const cutoverApi = vi.hoisted(() => ({
  resolveCreateContext: vi.fn(),
  createCutoverTask: vi.fn()
}))
vi.mock('@/api/pms/cutover/cutover-task', () => cutoverApi)
vi.mock('@/hooks/web/useMessage', () => ({
  useMessage: () => ({ success: vi.fn() })
}))

const dialog = defineComponent({
  setup(_, { slots }) {
    return () => h('section', [slots.default?.(), slots.footer?.()])
  }
})

const candidate: CreateContextCandidate = {
  project: {
    projectId: '9007199254741001',
    projectVersion: 3,
    projectCode: 'P-001',
    projectName: '核心网扩容',
    customerId: '8001',
    customerCode: 'CUS-01',
    customerName: '示例客户',
    officeDepartmentId: '7001',
    officeCode: 'OFF-01',
    officeName: '华东办事处',
    projectScopeVersion: '12'
  },
  devices: [
    { deviceId: '9007199254742001', serialNumber: 'SN-001', projectAssignmentVersion: '6' }
  ],
  customerServiceLevel: {
    status: 'AVAILABLE',
    customerId: '8001',
    customerCode: 'CUS-01',
    customerName: '示例客户',
    serviceLevelRevisionId: '21',
    serviceLevelCode: 'GOLD',
    factVersion: '8',
    effectiveFrom: 1788105600000,
    effectiveTo: null
  },
  implementationReadiness: {
    snapshotId: '31',
    snapshotVersion: '4',
    decision: 'READY',
    projectId: '9007199254741001',
    deviceIds: ['9007199254742001'],
    sourceWatermark: { projectVersion: 3 },
    unmetCodes: []
  },
  createAllowed: true
}

describe('cutover task positive interaction', () => {
  it('normalizes serial input and preserves the first owner-facing value', () => {
    expect(parseSerials(' sn-001, SN-002\nsn-001 ')).toEqual(['sn-001', 'SN-002'])
  })

  it('builds the complete expected-fact create request', () => {
    const request = buildCreateRequest(candidate, {
      configurationCode: 'CORE_STANDARD',
      taskName: ' 核心网割接 ',
      background: ' 设备替换 ',
      cutoverType: 'CORE_REPLACEMENT',
      networkMode: 'DUAL_PLANE',
      scheduledTime: '2026-09-01T01:30:00'
    })
    expect(request.expectedProjectContext).not.toHaveProperty('projectScopeVersion')
    expect(request.configurationCode).toBe('CORE_STANDARD')
    expect(request.expectedProjectScopeVersion).toBe('12')
    expect(request.expectedDeviceScopeWatermark).toEqual(candidate.devices)
    expect(request.serialNumbers).toEqual(['SN-001'])
    expect(request.expectedReadinessSnapshotId).toBe('31')
    expect(request.expectedCustomerServiceLevelCode).toBe('GOLD')
    expect(request.taskName).toBe('核心网割接')
    expect(request.scheduledTime).toBe(new Date('2026-09-01T01:30:00').getTime())
  })

  it('creates only from the devices of the selected project candidate', () => {
    const selected = {
      ...candidate,
      devices: [{ deviceId: '9007199254742002', serialNumber: 'SN-SELECTED', projectAssignmentVersion: '7' }]
    }
    const request = buildCreateRequest(selected, {
      configurationCode: 'CORE_STANDARD',
      taskName: '核心网割接',
      background: '设备替换',
      cutoverType: 'CORE_REPLACEMENT',
      networkMode: null,
      scheduledTime: '2026-09-01T01:30:00'
    })
    expect(request.serialNumbers).toEqual(['SN-SELECTED'])
    expect(request.expectedDeviceScopeWatermark).toEqual(selected.devices)
  })

  it('presents the locked A and D destinations', () => {
    expect(gradeDestination('A')).toBe('P3 现场调研')
    expect(gradeDestination('D')).toBe('P4 方案编制')
  })

  it('selects the workbench panel from the current stage only', () => {
    expect(activeCutoverStagePanel('P2')).toBe('ASSESSMENT')
    expect(activeCutoverStagePanel('P3')).toBe('CHECKLIST')
    expect(activeCutoverStagePanel('P4')).toBe('PLAN')
    expect(activeCutoverStagePanel('P5')).toBe('PLAN')
    expect(activeCutoverStagePanel('P6')).toBe('PLAN')
  })

  it('mounts the P2 assessment and emits the positive save and submit actions', async () => {
    const save = vi.fn()
    const submit = vi.fn()
    const model = {
      answers: {
        businessImportanceLevel: 'HIGH',
        operationComplexityLevel: 'MEDIUM',
        hiddenRiskLevel: 'LOW',
        sparePartApplied: true
      },
      manualGrade: 'A' as const
    }
    const mounted = mount(
      CutoverAssessmentPanel,
      {
        model,
        editable: true,
        submittable: true,
        saving: false,
        submitting: false,
        onSave: save,
        onSubmit: submit
      },
      {
        ElForm: passthrough,
        ElFormItem: passthrough,
        ElSelect: passthrough,
        ElOption: passthrough,
        ElRadioGroup: passthrough,
        ElRadio: passthrough,
        ElRadioButton: passthrough,
        ElTag: passthrough
      }
    )

    const buttons = collect(mounted.root, (node) => node.type === 'button')
    await (
      buttons.find((node) => textOf(node).includes('保存草稿'))!.props!.onClick as () => void
    )()
    await (
      buttons.find((node) => textOf(node).includes('提交人工分级'))!.props!.onClick as () => void
    )()
    await nextTick()

    expect(save).toHaveBeenCalledOnce()
    expect(submit).toHaveBeenCalledOnce()
    expect(textOf(mounted.root)).toContain('P3 现场调研')
    mounted.app.unmount()
  })

  it('resolves reserved owner facts and creates from the explicitly selected project', async () => {
    cutoverApi.resolveCreateContext.mockResolvedValue({
      candidates: [candidate],
      selectionRequired: false,
      configurationChoices: [{
        configurationCode: 'CORE_STANDARD',
        configurationName: '核心网标准配置',
        revisionId: '41',
        revisionNo: 2,
        effectiveFrom: 1788105600000,
        effectiveTo: null
      }],
      configurationSelectionRequired: false
    })
    cutoverApi.createCutoverTask.mockResolvedValue({ id: '101' })
    const created = vi.fn()
    const mounted = mount(
      CutoverCreateWizard,
      { modelValue: true, 'onUpdate:modelValue': vi.fn(), onCreated: created },
      {
        Dialog: dialog,
        ElSteps: passthrough,
        ElStep: passthrough,
        ElInput: passthrough,
        ElRadioGroup: passthrough,
        ElRadio: passthrough,
        ElDescriptions: passthrough,
        ElDescriptionsItem: passthrough,
        ElSelect: passthrough,
        ElOption: passthrough,
        ElDatePicker: passthrough
      }
    )

    await update(findByTestId(mounted.root, 'create-serials')!, ' sn-001 ')
    await clickButton(mounted.root, '解析项目')
    await flush()
    expect(cutoverApi.resolveCreateContext).toHaveBeenCalledWith(['sn-001'])

    await update(findByTestId(mounted.root, 'create-project-candidates')!, String(candidate.project.projectId))
    await clickButton(mounted.root, '下一步')
    await nextTick()
    expect(textOf(mounted.root)).toContain('SN-001')
    expect(textOf(mounted.root)).toContain('READY')

    await update(findByTestId(mounted.root, 'create-task-name')!, '核心网割接')
    await update(findByTestId(mounted.root, 'create-background')!, '设备替换')
    await update(findByTestId(mounted.root, 'create-cutover-type')!, 'CORE_REPLACEMENT')
    await update(findByTestId(mounted.root, 'create-scheduled-time')!, '2026-09-01T01:30:00')
    await clickButton(mounted.root, '创建并进入 P2')
    await flush()

    expect(cutoverApi.createCutoverTask).toHaveBeenCalledWith(
      expect.objectContaining({
        projectId: candidate.project.projectId,
        configurationCode: 'CORE_STANDARD',
        serialNumbers: ['SN-001'],
        scheduledTime: new Date('2026-09-01T01:30:00').getTime()
      }),
      expect.any(String)
    )
    expect(created).toHaveBeenCalledOnce()
    mounted.app.unmount()
  })
})

const collect = (node: TestNode, predicate: (candidate: TestNode) => boolean): TestNode[] => [
  ...(predicate(node) ? [node] : []),
  ...node.children.flatMap((child) => collect(child, predicate))
]

const update = async (node: TestNode, value: unknown) => {
  await (node.props?.['onUpdate:modelValue'] as (next: unknown) => void)(value)
  await nextTick()
}

const clickButton = async (root: TestNode, label: string) => {
  const target = collect(root, (node) => node.type === 'button' && textOf(node).includes(label))[0]
  await (target.props?.onClick as () => void | Promise<void>)()
}

const flush = async () => {
  await Promise.resolve()
  await nextTick()
  await Promise.resolve()
  await nextTick()
}
