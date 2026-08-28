import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const read = (path: string) => readFileSync(new URL(path, import.meta.url), 'utf8')
const panel = read('./ProjectRequirementAnalysisPanel.vue')
const runtime = read('./RequirementAnalysisDynamicForm.vue')
const legacySection = read('./RequirementAnalysisSectionCard.vue')
const history = read('./RequirementAnalysisHistoryDrawer.vue')
const compare = read('./RequirementAnalysisCompareDrawer.vue')
const detail = read('../index.vue')
const api = read('../../../../../api/pms/engineering/requirement-analysis/index.ts')

describe('F-SOL-003 dynamic-form requirement analysis workspace', () => {
  it('uses the locked PRE-04 routes and dual concurrency headers', () => {
    expect(api).toContain("const baseUrl = '/api/v1/pms/preparations'")
    expect(api).toContain('url: `${baseUrl}/${preparationId}/form`')
    expect(api).toContain("'If-Match': String(expectedInstanceVersion)")
    expect(api).toContain("'X-SOL-If-Match': String(expectedSolVersion)")
    expect(api).toContain("'Idempotency-Key': idempotencyKey")
    expect(api).toContain("type: 'PRE_04'")
    expect(api).toContain('history: true')
    expect(api).not.toMatch(/tenantId|actorRole|projectManagerUserId|completedBy:/)
  })

  it('uses server actions and never exposes project-side template selection', () => {
    expect(detail).toContain("activeTab === 'requirement-analysis'")
    expect(detail).toContain('<ProjectRequirementAnalysisPanel')
    expect(detail).toContain('pms:requirement-analysis:query')
    expect(panel).toContain('overview.value?.allowedActions || []')
    expect(panel).toContain('detail.value?.allowedActions || []')
    expect(runtime).toContain("allowedActions.includes('PATCH_FORM')")
    expect(runtime).toContain('模板已由项目工作绑定自动确定')
    expect(runtime).not.toContain('选择模板')
    expect(panel).not.toMatch(/isProjectManager|currentUserRole|managerUserId/)
  })

  it('reuses the PLT runtime and keeps the old section candidate intact but unreachable', () => {
    expect(runtime).toContain('decodeDynamicForm')
    expect(runtime).toContain('buildInstanceRuntime')
    expect(runtime).toContain('registerDynamicFormComponents')
    expect(runtime).toContain("allowedActions: editable.value ? ['PATCH_INSTANCE'] : []")
    expect(panel).toContain('<RequirementAnalysisDynamicForm')
    expect(panel).not.toContain('<RequirementAnalysisSectionCard')
    expect(legacySection).toContain('class="section-card"')
  })

  it('covers draft, effective, completion, revision, history and field compare', () => {
    expect(panel).toContain('当前草稿')
    expect(panel).toContain('当前有效完成版')
    expect(panel).toContain('createInitialDraft')
    expect(panel).toContain('completeDraft')
    expect(panel).toContain('createNextDraft')
    expect(history).toContain('getHistory')
    expect(history).toContain('contentVersion')
    expect(history).not.toContain('dynamicFormInstanceVersion')
    expect(compare).toContain('comparison.fields')
    expect(compare).toContain('controlledFilesChanged')
    expect(panel).toContain('createRequirementIntentStore')
    expect(panel).toContain('intentKeys.complete(intent)')
  })

  it('guards dirty values across version, history, refresh, completion and route changes', () => {
    expect(runtime).toContain("emit('dirty-change', value)")
    expect(runtime).toContain('stableRequirementFormIntent')
    expect(runtime).toContain('reconcileRequirementFormPatch')
    expect(panel).toContain("guardCurrentForm('切换版本')")
    expect(panel).toContain("guardCurrentForm('查看完成历史')")
    expect(panel).toContain("guardCurrentForm('刷新')")
    expect(panel).toContain("guardCurrentForm('完成草稿')")
    expect(panel).toContain('onBeforeRouteLeave')
    expect(panel).toContain("window.addEventListener('beforeunload'")
  })

  it('has loading, empty, error and responsive states at locked breakpoints', () => {
    expect(panel).toContain('el-skeleton')
    expect(panel).toContain('el-empty')
    expect(panel).toContain('errorText')
    for (const component of [panel, runtime, history, compare]) {
      expect(component).not.toMatch(/\sstyle=/)
      expect(component).not.toMatch(/#[0-9a-f]{3,8}\b/i)
    }
    expect(panel).toContain('@media (width <= 1023px)')
    expect(panel).toContain('@media (width <= 767px)')
    expect(runtime).toContain('@media (width <= 767px)')
  })
})
