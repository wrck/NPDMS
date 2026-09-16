import { describe, expect, it } from 'vitest'
import { projectStatus } from './projectStatus'

describe('project status presentation', () => {
  it('shows the active lifecycle without inventing one current stage for parallel projects', () => {
    expect(projectStatus({ lifecycleStatus: 'ACTIVE', currentStage: null, status: 'ACTIVE' }))
      .toEqual({ value: 'ACTIVE', label: '进行中', type: 'primary', stage: false })
  })
  it.each([
    ['NORMAL_CLOSED', '正常闭环', 'success'],
    ['NO_TRACKING_CLOSED', '不予跟踪闭环', 'info'],
    ['EXCEPTION_CLOSED', '异常闭环', 'warning']
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

  it('prioritizes primary service manager, then primary project manager, then active stage names', () => {
    const source = { lifecycleStatus: 'ACTIVE', serviceManagerAssigned: false,
      projectManagerAssigned: false, activeStageNames: ['工前准备', '施工计划'] }
    expect(projectStatus(source).label).toBe('待指派服务经理')
    source.serviceManagerAssigned = true
    expect(projectStatus(source).label).toBe('待指派项目经理')
    source.projectManagerAssigned = true
    expect(projectStatus(source).label).toBe('工前准备、施工计划')
    expect(projectStatus({ ...source, lifecycleStatus: 'NORMAL_CLOSED', serviceManagerAssigned: false }).label)
      .toBe('正常闭环')
    expect(projectStatus({ ...source, lifecycleStatus: 'EXCEPTION_CLOSED', projectManagerAssigned: false }).label)
      .toBe('异常闭环')
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
