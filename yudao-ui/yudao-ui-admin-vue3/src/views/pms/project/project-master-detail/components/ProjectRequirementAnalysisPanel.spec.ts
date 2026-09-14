import { readFileSync } from 'node:fs'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, reactive } from 'vue'
import ProjectRequirementAnalysisPanel from './ProjectRequirementAnalysisPanel.vue'
import * as RequirementAnalysisApi from '@/api/pms/engineering/requirement-analysis'
import { mount, textOf, findByTestId, passthrough, tableColumn } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

const formLifecycle = vi.hoisted(() => ({
  reload: undefined as undefined | (() => Promise<unknown>),
  mounts: 0,
  unmounts: 0
}))
vi.mock('@/api/pms/engineering/requirement-analysis', () => ({
  getCurrent: vi.fn(),
  getDetail: vi.fn()
}))
vi.mock('vue-router', () => ({ onBeforeRouteLeave: vi.fn() }))
vi.mock('@vueuse/core', async () => ({ useWindowSize: () => ({ width: 1440 }) }))
vi.mock('./RequirementAnalysisHistoryDrawer.vue', () => ({ default: { render: () => null } }))
vi.mock('./RequirementAnalysisCompareDrawer.vue', () => ({ default: { render: () => null } }))
vi.mock('./RequirementAnalysisDynamicForm.vue', async () => {
  const { defineComponent, h, onUnmounted } = await import('vue')
  return {
    default: defineComponent({
      props: ['detail', 'reload'],
      setup(props) {
        formLifecycle.reload = props.reload
        formLifecycle.mounts++
        onUnmounted(() => formLifecycle.unmounts++)
        return () => h('div', `正文版本 ${props.detail.contentVersion}`)
      }
    })
  }
})

const read = (path: string) => readFileSync(new URL(path, import.meta.url), 'utf8')
const panel = read('./ProjectRequirementAnalysisPanel.vue')
const runtime = read('./RequirementAnalysisDynamicForm.vue')
const legacySection = read('./RequirementAnalysisSectionCard.vue')
const history = read('./RequirementAnalysisHistoryDrawer.vue')
const compare = read('./RequirementAnalysisCompareDrawer.vue')
const detail = read('../index.vue')
const api = read('../../../../../api/pms/engineering/requirement-analysis/index.ts')
const deferred = <T,>() => {
  let resolve!: (value: T) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
const flushLoad = async () => { for (let i = 0; i < 10; i++) { await Promise.resolve(); await nextTick() } }
const version = (preparationId = 31, projectId = 7) => ({
  preparationId, projectId, businessVersion: 1, contentVersion: preparationId,
  status: 'COMPLETED', currentEffective: true, completionBlockers: [], allowedActions: []
}) as any
const summary = (value = version()) => ({ projectId: value.projectId, draft: null, currentEffective: value, allowedActions: [] }) as any
const renderPanel = (props: Record<string, unknown>) => mount(
  defineComponent({ setup: () => () => h(ProjectRequirementAnalysisPanel, props as any) }), {},
  { ElTable: passthrough, ElTableColumn: tableColumn, ElDescriptions: passthrough, ElDescriptionsItem: passthrough, ElInput: passthrough }
)

describe('F-SOL-003 dynamic-form requirement analysis workspace', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    formLifecycle.mounts = 0
    formLifecycle.unmounts = 0
    vi.stubGlobal('window', { addEventListener: vi.fn(), removeEventListener: vi.fn() })
  })

  it.each([31, undefined])('uses complete workspace content without a duplicate detail read for selection %s', async (preparationId) => {
    const overview = deferred<any>()
    vi.mocked(RequirementAnalysisApi.getCurrent).mockReturnValueOnce(overview.promise)
    const view = renderPanel({ project: { id: 7 }, preparationId })
    try {
      await flushLoad()
      expect(RequirementAnalysisApi.getDetail).not.toHaveBeenCalled()
      expect(formLifecycle.mounts).toBe(0)
      overview.resolve({ ...summary(), draft: { ...version(32), status: 'DRAFT', currentDraft: true, currentEffective: false } })
      await flushLoad()
      expect(textOf(view.root)).toContain(`正文版本 ${preparationId == null ? 32 : 31}`)
      expect(formLifecycle.mounts).toBe(1)
      expect(RequirementAnalysisApi.getCurrent).toHaveBeenCalledTimes(1)
      expect(RequirementAnalysisApi.getDetail).not.toHaveBeenCalled()
    } finally { view.app.unmount() }
  })

  it('reads an explicitly selected historical version instead of replacing it with the current version', async () => {
    vi.mocked(RequirementAnalysisApi.getCurrent).mockResolvedValueOnce(summary())
    vi.mocked(RequirementAnalysisApi.getDetail).mockResolvedValueOnce({ ...version(30), currentEffective: false })
    const view = renderPanel({ project: { id: 7 }, preparationId: 30 })
    try {
      await flushLoad()
      expect(RequirementAnalysisApi.getDetail).toHaveBeenCalledTimes(1)
      expect(RequirementAnalysisApi.getDetail).toHaveBeenCalledWith(30)
      expect(textOf(view.root)).toContain('正文版本 30')
      expect((findByTestId(view.root, 'requirement-version-table')!.props!.data as any[])[0].preparationId).toBe(31)
    } finally { view.app.unmount() }
  })

  it.each(['overview', 'detail'])('does not display partial content when the %s read fails', async (failed) => {
    vi.mocked(RequirementAnalysisApi.getCurrent).mockImplementationOnce(() => failed === 'overview'
      ? Promise.reject(new Error('403')) : Promise.resolve(summary()))
    vi.mocked(RequirementAnalysisApi.getDetail).mockImplementationOnce(() => failed === 'detail'
      ? Promise.reject(new Error('403')) : Promise.resolve(version()))
    const view = renderPanel({ project: { id: 7 }, preparationId: failed === 'detail' ? 30 : 31 })
    try {
      await flushLoad()
      expect(textOf(view.root)).toContain('需求分析工作区加载失败')
      expect(formLifecycle.mounts).toBe(0)
    } finally { view.app.unmount() }
  })

  it('rejects mismatched detail identity and ignores obsolete loads after a project switch', async () => {
    const old = deferred<any>()
    vi.mocked(RequirementAnalysisApi.getCurrent).mockReturnValueOnce(old.promise).mockResolvedValueOnce(summary(version(32, 8)))
    const props = reactive({ project: { id: 7 }, preparationId: 31 })
    const view = renderPanel(props)
    try {
      await flushLoad()
      props.project = { id: 8 }
      props.preparationId = 32
      await flushLoad()
      old.reject(new Error('old request failed'))
      await flushLoad()
      expect(textOf(view.root)).toContain('正文版本 32')
      expect(textOf(view.root)).not.toContain('加载失败')
      vi.mocked(RequirementAnalysisApi.getCurrent).mockResolvedValueOnce({ ...summary(version(33, 9)), projectId: 8 })
      props.preparationId = 33
      await flushLoad()
      expect(textOf(view.root)).toContain('需求分析工作区加载失败')
      expect(textOf(view.root)).not.toContain('正文版本 33')
      expect(RequirementAnalysisApi.getDetail).not.toHaveBeenCalled()
    } finally { view.app.unmount() }
  })

  it('refreshes summary and detail together without unmounting the saving form; read failure preserves both', async () => {
    const original = {
      preparationId: 31,
      projectId: 7,
      businessVersion: 1,
      contentVersion: 1,
      version: 1,
      status: 'DRAFT',
      currentDraft: true,
      dynamicFormInstanceVersion: 1,
      dynamicFormRevisionNo: 1,
      completionBlockers: [],
      allowedActions: ['PATCH_FORM']
    } as any
    const initial = {
      projectId: 7,
      draft: original,
      currentEffective: null,
      allowedActions: []
    } as any
    vi.mocked(RequirementAnalysisApi.getCurrent).mockResolvedValue(initial)
    vi.mocked(RequirementAnalysisApi.getDetail).mockResolvedValue(original)
    const mounted = mount(ProjectRequirementAnalysisPanel, { project: { id: 7 } }, { ElTable: passthrough, ElTableColumn: tableColumn, ElDescriptions: passthrough, ElDescriptionsItem: passthrough, ElInput: passthrough })
    const summaryVersion = () => (findByTestId(mounted.root, 'requirement-version-table')!.props!.data as any[])[0].contentVersion
    const flush = async () => {
      for (let i = 0; i < 6; i++) await nextTick()
    }
    try {
      await flush()
      expect(summaryVersion()).toBe(1)
      expect(formLifecycle.mounts).toBe(1)
      const updated = { ...original, contentVersion: 2, version: 2 }
      let finishOverview!: (value: any) => void
      vi.mocked(RequirementAnalysisApi.getCurrent).mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            finishOverview = resolve
          })
      )
      const pending = formLifecycle.reload!()
      await flush()
      expect(formLifecycle.unmounts).toBe(0)
      expect(textOf(mounted.root)).toContain('正文版本 1')
      finishOverview({ ...initial, draft: updated })
      await pending
      await flush()
      expect(summaryVersion()).toBe(2)
      expect(textOf(mounted.root)).toContain('正文版本 2')
      expect(formLifecycle.mounts).toBe(1)
      vi.mocked(RequirementAnalysisApi.getCurrent).mockRejectedValueOnce(
        new Error('overview unavailable')
      )
      await expect(formLifecycle.reload!()).rejects.toThrow('overview unavailable')
      await flush()
      expect(summaryVersion()).toBe(2)
      expect(textOf(mounted.root)).toContain('正文版本 2')
      expect(formLifecycle.unmounts).toBe(0)
      expect(RequirementAnalysisApi.getDetail).not.toHaveBeenCalled()
    } finally {
      mounted.app.unmount()
    }
  })

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
    expect(runtime).not.toContain('getTemplateSelection')
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
