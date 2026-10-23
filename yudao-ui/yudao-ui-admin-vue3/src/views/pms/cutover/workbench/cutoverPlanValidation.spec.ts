import { describe, expect, it, vi } from 'vitest'
import type { StandardCutoverPlanContent, CutoverPlanSupportArrangement } from '@/api/pms/cutover'
import { focusPlanSupportValidation, planSupportValidationMessage } from '../shared/cutoverPlanValidation'

const content = (supportArrangements: CutoverPlanSupportArrangement[]): StandardCutoverPlanContent => ({
  editMode: 'ONLINE_TEMPLATE_STANDARD', steps: [], riskMitigations: [], supportArrangements,
  overview: { projectDescription: '', scheduleTable: [], deviceSummary: [],
    preTopologyFile: null, postTopologyFile: null, networkConfigurationFile: null }
})
const completeRoles = (): CutoverPlanSupportArrangement[] =>
  (['CUSTOMER', 'DP_FIRST_LINE', 'DP_SECOND_LINE', 'DP_RND'] as const).map(roleCode => ({
    arrangementId: null, roleCode, personName: '验收人员', dutyDescription: '验收保障', phone: '123', arrivalTime: 1
  }))

describe('plan support rejection explanation', () => {
  it('expands enclosing cards before scrolling and focusing a rejected support section', () => {
    const outer = { open: false, parentElement: null }
    const inner = { open: false, parentElement: { closest: () => outer } }
    const root = { closest: () => inner, scrollIntoView: vi.fn(() => {
      expect(inner.open).toBe(true)
      expect(outer.open).toBe(true)
    }), focus: vi.fn() }
    focusPlanSupportValidation(root as unknown as HTMLElement)
    expect(root.scrollIntoView).toHaveBeenCalledWith({ block: 'start' })
    expect(root.focus).toHaveBeenCalledWith({ preventScroll: true })
  })

  it('reports absent roles even when there are multiple customer rows', () => {
    const customer = completeRoles()[0]
    expect(planSupportValidationMessage(content([customer, customer])))
      .toBe('保障安排缺少角色：迪普一线工程师。请新增人员并补齐保障信息。')
  })

  it('identifies missing fields in an existing row without exposing contact values', () => {
    const rows = completeRoles()
    rows[1] = { ...rows[1], phone: '', arrivalTime: 0 }
    expect(planSupportValidationMessage(content(rows)))
      .toBe('保障安排第2行（迪普一线工程师）缺少：联系电话、到位时间。')
  })

  it('does not infer support failures for complete support, D plans or absent content', () => {
    expect(planSupportValidationMessage(content(completeRoles()))).toBe('')
    expect(planSupportValidationMessage(content(completeRoles().slice(0, 2)))).toBe('')
    expect(planSupportValidationMessage({ editMode: 'ONLINE_TEMPLATE_SIMPLE_D', steps: [] })).toBe('')
    expect(planSupportValidationMessage(null)).toBe('')
  })
})
