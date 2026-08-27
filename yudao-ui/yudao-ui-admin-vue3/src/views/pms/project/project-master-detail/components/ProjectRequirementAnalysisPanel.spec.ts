import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const read = (path: string) => readFileSync(new URL(path, import.meta.url), 'utf8')
const panel = read('./ProjectRequirementAnalysisPanel.vue')
const section = read('./RequirementAnalysisSectionCard.vue')
const history = read('./RequirementAnalysisHistoryDrawer.vue')
const compare = read('./RequirementAnalysisCompareDrawer.vue')
const detail = read('../index.vue')
const api = read('../../../../../api/pms/engineering/requirement-analysis/index.ts')

describe('F-SOL-003 requirement analysis workspace', () => {
  it('uses the locked PRE-04 routes and concurrency headers', () => {
    expect(api).toContain("const baseUrl = '/api/v1/pms/preparations'")
    expect(api).toContain("type: 'PRE_04'")
    expect(api).toContain('history: true')
    expect(api).toContain("method: 'PATCH'")
    expect(api).toContain("'If-Match': String(data.expectedPreparationVersion)")
    expect(api).toContain("'Idempotency-Key': idempotencyKey")
    expect(api).toContain("params: { type: 'PRE_04' }")
    expect(api).toContain('RequirementAnalysisCommandResultVO')
    expect(api).not.toMatch(/tenantId|actorRole|projectManagerUserId|completedBy:/)
  })

  it('mounts a permission-controlled project workspace and uses server actions', () => {
    expect(detail).toContain("activeTab === 'requirement-analysis'")
    expect(detail).toContain('<ProjectRequirementAnalysisPanel')
    expect(detail).toContain('pms:requirement-analysis:query')
    expect(panel).toContain('overview.value?.allowedActions || []')
    expect(panel).toContain('detail.value?.allowedActions || []')
    expect(section).toContain('props.section.allowedActions || []')
    expect(panel).toContain("actions.includes('EDIT')")
    expect(section).toContain("sectionActions.value.includes('ATTACH')")
    expect(section).toContain("sectionActions.value.includes('REPLACE')")
    expect(section).toContain("sectionActions.value.includes('DETACH')")
    expect(panel).not.toMatch(/isProjectManager|currentUserRole|managerUserId/)
  })

  it('covers draft, effective, completion, revision, history and compare', () => {
    expect(panel).toContain('当前草稿')
    expect(panel).toContain('当前有效完成版')
    expect(panel).toContain('createInitialDraft')
    expect(panel).toContain('completeDraft')
    expect(panel).toContain('createNextDraft')
    expect(history).toContain('getHistory')
    expect(compare).toContain('compareVersions')
    expect(panel).toContain('createRequirementIntentStore')
    expect(panel).toContain('intentKeys.complete(intent)')
    expect(panel).toContain(':reload="load"')
    expect(section).toContain('patchRequirementSectionAndReload')
  })

  it('renders frozen field types and exact attachment slots without durable file URLs', () => {
    for (const type of [
      'RICH_TEXT',
      'TEXT',
      'NUMBER',
      'BOOLEAN',
      'SINGLE_SELECT',
      'MULTI_SELECT'
    ]) {
      expect(section).toContain(type)
    }
    expect(section).toContain('PmsFileUploader')
    expect(section).toContain('PmsFileReferenceList')
    expect(section).toContain('REQUIREMENT_ANALYSIS_SECTION')
    expect(section).toContain('SECTION_ATTACHMENT')
    expect(section).toContain('fileFactVersion')
    expect(section).toContain('currentActiveFacts')
    expect(section).toContain("attachmentSyncStatus === 'PENDING'")
    expect(section).toContain("attachmentSyncStatus === 'UNKNOWN'")
    expect(section).toContain("sessionStorage.setItem(attachmentSyncKey()")
    expect(section).toContain("sessionStorage.setItem(bodyIntentKey()")
    expect(section).toContain('未确认的正文与附件意图已保留')
    expect(section).toContain('defineExpose({ save, discardBodyChanges })')
    expect(section).not.toMatch(/shortLivedUrl|objectKey|minio/i)
  })

  it('shows server blockers and guards every transition away from editable facts', () => {
    expect(api).toContain('RequirementAnalysisCompletionBlockerVO')
    expect(api).toContain('completionBlockers: RequirementAnalysisCompletionBlockerVO[]')
    expect(api).toContain('attachmentSyncStatus: RequirementAnalysisAttachmentSyncStatus')
    expect(panel).toContain('detail.completionBlockers.length')
    expect(panel).toContain('requirementAnalysisTransitionDecision')
    expect(panel).toContain("guardCurrentSection('切换章节')")
    expect(panel).toContain("guardCurrentSection('切换版本')")
    expect(panel).toContain("guardCurrentSection('查看完成历史')")
    expect(panel).toContain("guardCurrentSection('刷新')")
    expect(panel).toContain("attachmentSyncStatus === 'UNKNOWN'")
    expect(panel).toContain("guardCurrentSection('完成草稿')")
    expect(panel).toContain('@edit-state-change="sectionEditState = $event"')
  })

  it('has loading, empty, error and responsive states at locked breakpoints', () => {
    expect(panel).toContain('el-skeleton')
    expect(panel).toContain('el-empty')
    expect(panel).toContain('errorText')
    for (const component of [panel, section, history, compare]) {
      expect(component).not.toMatch(/\sstyle=/)
      expect(component).not.toMatch(/#[0-9a-f]{3,8}\b/i)
    }
    expect(panel).toContain('@media (width <= 1023px)')
    expect(panel).toContain('@media (width <= 767px)')
    expect(panel).toContain('minmax(0, 1fr)')
    expect(panel).toMatch(
      /@media \(width <= 767px\)[\s\S]*?\.primary-actions\s*\{[^}]*grid-template-columns:\s*1fr/
    )
    expect(panel).toMatch(/\.primary-actions :deep\(\.el-button\)\s*\{[^}]*width:\s*100%/)
    expect(panel).not.toMatch(
      /\.primary-actions\s*\{[^}]*grid-template-columns:\s*repeat\(2,\s*minmax\(0,\s*1fr\)\)/
    )
  })
})
