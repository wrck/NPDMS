import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const read = (path: string) => readFileSync(new URL(path, import.meta.url), 'utf8')
const panel = read('./ProjectPreparationPanel.vue')
const item = read('./PreparationItemDrawer.vue')
const waiver = read('./PreparationWaiverDrawer.vue')
const readiness = read('./PreparationReadinessDrawer.vue')
const detail = read('../index.vue')
const api = read('../../../../../api/pms/engineering/preparation/index.ts')
const oldApi = read('../../../../../api/pms/engineering/site-survey/index.ts')
const oldPage = read('../../../engineering/site-survey/index.vue')
const legacyDetail = read('../../project-detail/index.vue')

describe('F-SOL-002 project preparation panel', () => {
  it('uses the locked PRE-02 routes and concurrency headers', () => {
    expect(api).toContain("const baseUrl = '/api/v1/pms/preparations'")
    expect(api).toContain("'Idempotency-Key': idempotencyKey")
    expect(api).toContain("'If-Match': String")
    expect(api).toContain('expectedPreparationVersion')
    expect(api).toContain('expectedInputVersion')
    expect(api).toContain('expectedReadinessVersion')
    expect(api).toContain('expectedProjectVersion')
    expect(api).not.toMatch(/tenantId|actorId/)
  })

  it('mounts the project workspace and obeys server allowed actions', () => {
    expect(detail).toContain("activeTab === 'preparation'")
    expect(detail).toContain('<ProjectPreparationPanel')
    expect(panel).toContain("preparation.allowedActions.includes('SUBMIT')")
    expect(panel).toContain("preparation.allowedActions.includes('EVALUATE_READINESS')")
    expect(panel).toContain("actions.includes('CONFIRM_ITEM')")
    expect(panel).toContain("actions.includes('CONFIRM_NOT_APPLICABLE_ITEM')")
    expect(panel).toContain("actions.includes('RETURN_ITEM')")
    expect(panel).not.toContain("item.allowedActions.includes('REVIEW_ITEM')")
    expect(panel).not.toMatch(/currentUserRole|isProjectManager/)
  })

  it('supports fixed forms, evidence, sources, waivers and readiness history', () => {
    expect(item).toContain('formValueSnapshot')
    expect(item).toContain('evidenceReferences')
    expect(item).toContain('PmsFileUploader')
    expect(item).toContain('item.sourceItemId || item.itemId')
    expect(item).toContain(':editable="canAssignee"')
    expect(item).toContain('@detached="captureDetachedEvidence"')
    expect(item).toContain('getAssignmentCandidates')
    expect(item).toContain('NOT_APPLICABLE_PENDING')
    expect(panel).toContain('refreshSource')
    expect(panel).toContain('const sources = item.sources || []')
    expect(panel).toContain('const actions = item.allowedActions || []')
    expect(panel).toContain("JSON.parse(item.waiverPolicySnapshot || '{}').allowed === true")
    expect(panel).toContain("actions.includes('CREATE_WAIVER') ? '豁免' : '豁免记录'")
    expect(waiver).toContain("item?.allowedActions?.includes('CREATE_WAIVER')")
    expect(waiver).toContain("row.allowedActions?.includes('APPROVE')")
    expect(waiver).toContain('value-format="x"')
    expect(waiver).not.toContain('value-format="YYYY-MM-DDTHH:mm:ss"')
    expect(waiver).toContain('formatDateTime(row.validFrom)')
    expect(waiver).toContain('createWaiver')
    expect(waiver).toContain('actWaiver')
    expect(readiness).toContain('getReadinessSnapshots')
    expect(readiness).toContain('blockers')
  })

  it('shows unavailable bindings and source failures without offering initialization or stale READY', () => {
    expect(panel).toContain('WORK_BINDING_NOT_AVAILABLE')
    expect(panel).not.toMatch(/initializePreparation|初始化工勘/)
    expect(panel).toContain('不可用于就绪')
    expect(panel).toContain('snapshotCurrent')
  })

  it('uses theme tokens and mobile breakpoints without inline styles', () => {
    for (const component of [panel, item, waiver, readiness]) {
      expect(component).not.toMatch(/\sstyle=/)
      expect(component).not.toMatch(/#[0-9a-f]{3,8}\b/i)
    }
    expect(panel).toMatch(/var\(--el-(?:text|border|fill|color)-/)
    expect(panel).toContain('@media (width <= 1023px)')
    expect(panel).toContain('@media (width <= 767px)')
    expect(item).toContain("narrow.value ? '100%' : '640px'")
  })

  it('retires V1.7 writes while preserving historical reads and AST location maintenance', () => {
    expect(oldApi).toContain('getSiteSurveyPage')
    expect(oldApi).toContain('getSiteSurvey')
    expect(oldApi).not.toMatch(
      /createSiteSurvey|updateSiteSurvey|deleteSiteSurvey|confirmSiteSurvey|rejectSiteSurvey|archiveSiteSurvey/
    )
    expect(oldPage).toContain('仅保留历史查询')
    expect(oldPage).toContain('维护地点')
    expect(legacyDetail).not.toMatch(
      /SiteSurveyApi\.(?:create|update|delete|confirm|reject|archive)SiteSurvey/
    )
  })
})
