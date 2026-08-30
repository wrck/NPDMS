import { describe, expect, it } from 'vitest'
import type { CreateContextCandidate } from '@/api/pms/cutover/cutover-task'
import { buildCreateRequest, gradeDestination, parseSerials } from './cutoverTaskInteraction'

const candidate: CreateContextCandidate = {
  project: {
    projectId: '9007199254741001', projectVersion: 3, projectCode: 'P-001', projectName: '核心网扩容',
    customerId: '8001', customerCode: 'CUS-01', customerName: '示例客户', officeDepartmentId: '7001',
    officeCode: 'OFF-01', officeName: '华东办事处', projectScopeVersion: '12'
  },
  devices: [{ deviceId: '9007199254742001', serialNumber: 'SN-001', projectAssignmentVersion: '6' }],
  customerServiceLevel: {
    status: 'AVAILABLE', customerId: '8001', customerCode: 'CUS-01', customerName: '示例客户',
    serviceLevelRevisionId: '21', serviceLevelCode: 'GOLD', factVersion: '8',
    effectiveFrom: 1788105600000, effectiveTo: null
  },
  implementationReadiness: {
    snapshotId: '31', snapshotVersion: '4', decision: 'READY', projectId: '9007199254741001',
    deviceIds: ['9007199254742001'], unmetCodes: []
  },
  createAllowed: true
}

describe('cutover task positive interaction', () => {
  it('normalizes serial input and preserves the first owner-facing value', () => {
    expect(parseSerials(' sn-001, SN-002\nsn-001 ')).toEqual(['sn-001', 'SN-002'])
  })

  it('builds the complete expected-fact create request', () => {
    const request = buildCreateRequest(candidate, {
      serialNumbers: ['SN-001'], taskName: ' 核心网割接 ', background: ' 设备替换 ',
      cutoverType: 'CORE_REPLACEMENT', networkMode: 'DUAL_PLANE', scheduledTime: '2026-09-01T01:30:00'
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
})
