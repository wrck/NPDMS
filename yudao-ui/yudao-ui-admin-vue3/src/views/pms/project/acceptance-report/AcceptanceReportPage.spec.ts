import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const read = (path: string) => readFileSync(new URL(path, import.meta.url), 'utf8')

describe('F-ACC-001 acceptance report page contract', () => {
  it('uses every locked public report route and stable concurrency headers', () => {
    const api = read('../../../../api/pms/project/acceptance-report/index.ts')
    expect(api).toContain("const baseUrl = '/api/v1/pms/acceptances'")
    expect(api).toContain('/report-versions')
    expect(api).toContain('/actions/publish')
    expect(api).toContain('/actions/revoke-current-version')
    expect(api).toContain('/attachments/${sequence}/download')
    expect(api).toContain("'If-Match': String(activityVersion)")
    expect(api).toContain("'Idempotency-Key': idempotencyKey")
  })

  it('keeps draft, history and attachment access as separate focused components', () => {
    const page = read('./index.vue')
    const detail = read('./detail.vue')
    const editor = read('./ReportDraftEditor.vue')
    const history = read('./ReportVersionHistoryDrawer.vue')
    expect(page).toContain('<AcceptanceReportDetail')
    expect(detail).toContain('<ReportDraftEditor')
    expect(detail).toContain('<ReportVersionHistoryDrawer')
    expect(editor).toContain('<PmsFileUploader')
    expect(history).toContain('downloadAttachment')
    expect(history).toContain("archiveStatusLabel")
  })

  it('renders server states and responsive layouts without inventing business results', () => {
    const sources = [read('./index.vue'), read('./detail.vue'), read('./ReportDraftEditor.vue')]
    expect(sources.join('\n')).toContain('PENDING_COMPENSATION')
    expect(sources.join('\n')).toContain('DRAFT')
    expect(sources.join('\n')).toContain('EFFECTIVE')
    for (const source of sources) {
      expect(source).not.toMatch(/#[0-9a-f]{3,8}\b/i)
      expect(source).not.toMatch(/tenantId/)
    }
    expect(sources.join('\n')).toContain('@media (width <= 767px)')
  })
})
