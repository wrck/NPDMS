import { describe, expect, it } from 'vitest'
import {
  closureConditionLabel,
  closureNodeLabel,
  closureReasonLabel
} from './normalClosurePresentation'
describe('closure display labels', () => {
  it('shows Chinese labels without treating unknown checks as passed', () => {
    expect(closureConditionLabel('TASK_CONTRACT_CURRENT')).toBe('任务执行约定有效')
    expect(closureConditionLabel('TASK_BUSINESS_FACTS')).toBe('任务关联业务结果已核验')
    expect(closureConditionLabel('FUTURE_CODE')).toBe('其他闭环条件')
    expect(closureReasonLabel('OWNER_UNAVAILABLE')).not.toContain('OWNER_UNAVAILABLE')
    expect(closureReasonLabel('文件已失效')).toBe('文件已失效')
    expect(closureReasonLabel()).toBe('—')
    expect(closureNodeLabel('materialReview')).toBe('材料审核')
  })
})
