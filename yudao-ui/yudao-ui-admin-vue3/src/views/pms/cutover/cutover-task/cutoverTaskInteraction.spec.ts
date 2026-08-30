import { nextTick } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import type { CreateContextCandidate } from '@/api/pms/cutover/cutover-task'
import CutoverAssessmentPanel from './components/CutoverAssessmentPanel.vue'
import { buildCreateRequest, gradeDestination, parseSerials } from './cutoverTaskInteraction'
import {
  mount,
  passthrough,
  textOf,
  type TestNode
} from '../../platform/dynamic-form/components/runtimeTestHarness'

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
      serialNumbers: ['SN-001'],
      taskName: ' 核心网割接 ',
      background: ' 设备替换 ',
      cutoverType: 'CORE_REPLACEMENT',
      networkMode: 'DUAL_PLANE',
      scheduledTime: '2026-09-01T01:30:00'
    })
    expect(request.expectedProjectContext).not.toHaveProperty('projectScopeVersion')
    expect(request.expectedProjectScopeVersion).toBe('12')
    expect(request.expectedDeviceScopeWatermark).toEqual(candidate.devices)
    expect(request.expectedReadinessSnapshotId).toBe('31')
    expect(request.expectedCustomerServiceLevelCode).toBe('GOLD')
    expect(request.taskName).toBe('核心网割接')
  })

  it('presents the locked A and D destinations', () => {
    expect(gradeDestination('A')).toBe('P3 现场调研')
    expect(gradeDestination('D')).toBe('P4 方案编制')
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
})

const collect = (node: TestNode, predicate: (candidate: TestNode) => boolean): TestNode[] => [
  ...(predicate(node) ? [node] : []),
  ...node.children.flatMap((child) => collect(child, predicate))
]
