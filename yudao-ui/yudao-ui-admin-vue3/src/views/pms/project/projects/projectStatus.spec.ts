import { describe, expect, it } from 'vitest'
import { projectStatus } from './projectStatus'

describe('project status presentation', () => {
  it.each([
    ['NORMAL_CLOSED', '正常闭环', 'success'],
    ['NO_TRACKING_CLOSED', '不予跟踪闭环', 'info'],
    ['EXCEPTION_CLOSED', '异常关闭', 'warning']
  ])('prioritizes %s over retained stage and stale status', (lifecycleStatus, label, type) => {
    const source = { lifecycleStatus, currentStage: 'S1', status: 'S0' }
    expect(projectStatus(source)).toEqual({ value: lifecycleStatus, label, type, stage: false })
    expect(source.currentStage).toBe('S1')
    expect(source.status).toBe('S0')
  })

  it('uses actual stage for active projects', () => {
    expect(projectStatus({ lifecycleStatus: 'ACTIVE', currentStage: 'S4', status: 'S0' }))
      .toEqual({ value: 'S4', stage: true })
  })

  it('does not turn an active S6 project into closed', () => {
    expect(projectStatus({ lifecycleStatus: 'ACTIVE', currentStage: 'S6' }))
      .toEqual({ value: 'S6', stage: true })
  })

  it('supports legacy missing fields without inventing a stage', () => {
    expect(projectStatus({ status: 'S2' })).toEqual({ value: 'S2', stage: true })
    expect(projectStatus(null)).toEqual({ value: '', stage: true })
  })

  it('does not mislabel an unknown lifecycle as successful closure', () => {
    expect(projectStatus({ lifecycleStatus: 'UNKNOWN', currentStage: 'S0' }))
      .toEqual({ value: 'UNKNOWN', label: 'UNKNOWN', type: 'info', stage: false })
  })
})
