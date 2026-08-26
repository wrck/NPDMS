import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const read = (path: string) => readFileSync(new URL(path, import.meta.url), 'utf8')
const panel = read('./ProjectDurationPanel.vue')
const form = read('./ProjectDurationFormDrawer.vue')
const history = read('./ProjectDurationHistoryDrawer.vue')
const detail = read('../index.vue')
const api = read('../../../../../api/pms/engineering/construction-plan/index.ts')
const oldBackwardApi = read('../../../../../api/pms/project/schedule-backward/index.ts')
const oldChangeApi = read('../../../../../api/pms/project/plan-change/index.ts')
const oldBackwardPage = read('../../schedule-backward/index.vue')
const oldChangePage = read('../../plan-change/index.vue')

describe('F-SOL-001 project duration panel', () => {
  it('uses the locked construction plan routes and concurrency headers', () => {
    expect(api).toContain("const baseUrl = '/api/v1/pms/construction-plans'")
    expect(api).toContain("'Idempotency-Key': idempotencyKey")
    expect(api).toContain("'If-Match': String(planVersion)")
    expect(api).toContain("'If-Match': String(changeVersion)")
    expect(api).toContain('expectedProjectVersion')
    expect(api).not.toMatch(/tenantId|applicantId|approverId/)
  })

  it('connects the project detail mainline and obeys server allowed actions', () => {
    expect(detail).toContain("activeTab === 'duration'")
    expect(detail).toContain('<ProjectDurationPanel')
    expect(panel).toContain("plan.allowedActions.includes('CREATE_CHANGE')")
    expect(form).toContain('createInitial')
    expect(form).toContain('createChange')
    expect(panel).toContain('submitChange')
  })

  it('builds a field-presence PATCH and clears nullable fields with null', () => {
    expect(form).toContain('const patchPayload')
    expect(form).toContain('patch.endDate = form.endDate || null')
    expect(form).toContain('patch.durationDays = form.durationDays || null')
    expect(form).toContain('patch.reasonDetail = form.reasonDetail || null')
    expect(form).toContain(
      'patch.customerEvidenceReferenceKey = form.customerEvidenceReferenceKey || null'
    )
    expect(form).toContain('Object.keys(patch).length === 1')
  })

  it('connects customer-delay evidence without treating a URL as file identity', () => {
    expect(form).toContain("form.reasonType === 'CUSTOMER_DELAY'")
    expect(form).toContain('customerEvidenceFileId')
    expect(form).toContain('customerEvidenceFileVersion')
    expect(form).toContain('customerEvidenceReferenceKey')
    expect(panel).toContain('customerEvidenceRequired && !draft.value.customerEvidenceFileId')
    expect(form).not.toMatch(/customerEvidenceUrl|storagePath/)
  })

  it('uses platform BPM for approval and applicant cancellation without SOL terminal writes', () => {
    expect(panel).toContain("name: 'BpmProcessInstanceDetail'")
    expect(panel).toContain('cancelProcessInstanceByStartUser')
    expect(panel).not.toMatch(/approveChange|rejectChange|withdrawChange/)
    expect(api).not.toMatch(/actions\/(?:approve|reject|cancel)/)
  })

  it('shows current, pending and cursor-paged history facts', () => {
    expect(panel).toContain('plan.currentRevision')
    expect(panel).toContain('plan.pendingChangeSummary')
    expect(panel).toContain('PENDING_RECALCULATION')
    expect(history).toContain('nextCursor')
    expect(history).toContain('hasMore')
  })

  it('uses Element Plus theme tokens and responsive breakpoints without inline styles', () => {
    for (const component of [panel, form, history]) {
      expect(component).not.toMatch(/\sstyle=/)
      expect(component).not.toMatch(/#[0-9a-f]{3,8}\b/i)
    }
    expect(panel).toMatch(/var\(--el-(?:text|border|fill|color)-/)
    expect(history).toMatch(/var\(--el-(?:text|border|fill|color)-/)
    expect(panel).toContain('@media (width <= 1023px)')
    expect(panel).toContain('@media (width <= 767px)')
    expect(form).toContain("narrow.value ? '100%' : '560px'")
  })

  it('retires V1.7 PRE-01 writes while preserving historical reads', () => {
    expect(oldBackwardApi).toContain('getScheduleBackwardPage')
    expect(oldBackwardApi).toContain('getScheduleBackwardItems')
    expect(oldBackwardApi).not.toMatch(
      /createScheduleBackward|calculateScheduleBackward|applyScheduleBackward|deleteScheduleBackward/
    )
    expect(oldChangeApi).toContain('getPlanChangePage')
    expect(oldChangeApi).toContain('getPlanChangeSnapshots')
    expect(oldChangeApi).not.toMatch(
      /createPlanChange|submitPlanChange|approvePlanChange|withdrawPlanChange|deletePlanChange/
    )
    expect(oldBackwardPage).toContain('仅保留历史查询')
    expect(oldChangePage).toContain('仅保留历史查询')
  })
})
