import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, reactive, ref } from 'vue'
import {
  findByTestId,
  mount,
  passthrough,
  textOf
} from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
import type { PreparationVO, SurveyMetadata } from '@/api/pms/engineering/preparation'
import type { ProjectMasterVO } from '@/api/pms/project/projects'
import Panel from './PreparationSurveyMetadataPanel.vue'

const api = vi.hoisted(() => ({
  getSurvey: vi.fn(),
  saveSurvey: vi.fn(),
  getAssignmentCandidates: vi.fn()
}))
const locationApi = vi.hoisted(() => ({
  getSitePage: vi.fn(),
  getSiteLocationTree: vi.fn(),
  maintainLocation: vi.fn()
}))
vi.mock('@/api/pms/engineering/preparation', () => api)
vi.mock('@/api/pms/asset/location', () => locationApi)

// PRE-02 / F-SOL-002：Owner API 为替身，面板和唯一地点选择器使用真实组件逻辑。
const metadata = (overrides: Partial<SurveyMetadata> = {}): SurveyMetadata => ({
  preparationId: 101,
  version: 12,
  allowedActions: ['UPDATE_SURVEY'],
  surveyDate: '2026-09-08',
  surveyorUserId: 17,
  location: '现场 A / 机房',
  locationResolutionStatus: 'RESOLVED',
  addressId: 11,
  addressVersion: 2,
  siteId: 22,
  siteVersion: 3,
  siteLocationId: 33,
  siteLocationVersion: 4,
  grounding: '接地正常',
  constructionResource: '已具备',
  conclusion: '原结论',
  ...overrides
})
const preparation = (projectId = 1, preparationId = 101): PreparationVO =>
  ({
    projectId,
    preparationId,
    version: 2,
    allowedActions: []
  }) as unknown as PreparationVO
const deferred = <T>() => {
  let resolve!: (value: T) => void
  let reject!: (error: Error) => void
  const promise = new Promise<T>((done, fail) => {
    resolve = done
    reject = fail
  })
  return { promise, resolve, reject }
}
const flush = async () => {
  for (let i = 0; i < 8; i++) {
    await nextTick()
    await Promise.resolve()
  }
}
const apps: { unmount: () => void }[] = []
const render = (
  readonly = false,
  initialPreparation = preparation(),
  project: ProjectMasterVO = { id: 1, version: 7 }
) => {
  const props = reactive({ preparation: initialPreparation, project, readonly })
  const child = ref<any>()
  const saved = vi.fn()
  const dirtyChange = vi.fn()
  const wrapper = defineComponent({
    setup: () => () =>
      h(Panel, { ...props, ref: child, onSaved: saved, onDirtyChange: dirtyChange })
  })
  const components = Object.fromEntries(
    [
      'ElSkeleton',
      'ElDatePicker',
      'ElSelect',
      'ElOption',
      'ElInput',
      'ElRadioGroup',
      'ElRadioButton',
      'ElTreeSelect',
      'ElDivider',
      'ElRow',
      'ElCol'
    ].map((name) => [name, passthrough])
  )
  const mounted = mount(wrapper, {}, components)
  apps.push(mounted.app)
  const node = (id: string) => findByTestId(mounted.root, id)
  return {
    ...mounted,
    props,
    child,
    saved,
    dirtyChange,
    node,
    state: () => child.value.$.setupState,
    async click(id: string) {
      const target = node(id)
      expect(target, `missing ${id}`).toBeDefined()
      await (target?.props?.onClick as () => unknown)()
      await flush()
    },
    async input(id: string, value: unknown) {
      const target = node(id)
      expect(target, `missing ${id}`).toBeDefined()
      ;(target?.props?.['onUpdate:modelValue'] as (value: unknown) => void)(value)
      await flush()
    }
  }
}

beforeEach(() => {
  vi.resetAllMocks()
  api.getSurvey.mockResolvedValue(metadata())
  api.saveSurvey.mockResolvedValue(metadata({ version: 13 }))
  api.getAssignmentCandidates.mockResolvedValue({
    list: [
      {
        userId: 17,
        nickname: '张工',
        username: 'zhang',
        employeeNo: '0017',
        departmentName: '交付部'
      }
    ],
    total: 1
  })
  locationApi.getSitePage.mockResolvedValue({
    list: [{ id: 22, code: 'SITE-A', name: '现场 A', version: 3 }]
  })
  locationApi.getSiteLocationTree.mockResolvedValue([
    { id: 33, code: 'ROOM', name: '机房', locationType: 'ROOM', version: 4, treeSort: 0 }
  ])
})
afterEach(() => apps.splice(0).forEach((app) => app.unmount()))

describe('PRE-02 survey metadata embedded panel', () => {
  it('reads authoritative metadata and keeps readonly rendering free of management queries', async () => {
    const view = render()
    await flush()
    expect(api.getSurvey).toHaveBeenCalledWith(101)
    expect(textOf(view.root)).toContain('2026-09-08')
    expect(textOf(view.root)).toContain('现场 A / 机房')
    expect(textOf(view.root)).toContain('工勘人员 #17')
    expect(view.node('survey-date')).toBeUndefined()
    expect(api.getAssignmentCandidates).not.toHaveBeenCalled()
    expect(locationApi.getSitePage).not.toHaveBeenCalled()
    expect(locationApi.getSiteLocationTree).not.toHaveBeenCalled()
    expect(view.child.value.isDirty()).toBe(false)
  })

  it('uses only UPDATE_SURVEY from the Owner response, not frontend roles or preparation actions', async () => {
    const view = render()
    await flush()
    expect(view.props.preparation.allowedActions).toEqual([])
    await view.click('survey-edit')
    expect(view.node('survey-date')).toBeDefined()
    expect(locationApi.getSitePage).toHaveBeenCalled()
    expect(locationApi.maintainLocation).not.toHaveBeenCalled()
    expect(view.child.value.isDirty()).toBe(false)
  })

  it.each([true, false])(
    'rejects writes when readonly=%s or UPDATE_SURVEY is missing',
    async (readonly) => {
      if (!readonly) api.getSurvey.mockResolvedValue(metadata({ allowedActions: [] }))
      const current = preparation()
      current.allowedActions = ['UPDATE_SURVEY', 'MANAGE']
      const view = render(readonly, current)
      await flush()
      expect(view.node('survey-edit')).toBeUndefined()
      view.state().beginEdit()
      await view.state().save()
      expect(view.state().editing).toBe(false)
      expect(api.saveSurvey).not.toHaveBeenCalled()
      expect(locationApi.getSitePage).not.toHaveBeenCalled()
    }
  )

  it('uses date-only input and metadata CAS, and emits saved only after authoritative GET', async () => {
    const view = render()
    await flush()
    await view.click('survey-edit')
    expect(view.node('survey-date')?.props?.['value-format']).toBe('YYYY-MM-DD')
    await view.input('survey-date', '2026-09-09')
    const readback = deferred<SurveyMetadata>()
    api.getSurvey.mockReturnValueOnce(readback.promise)
    const pending = view.state().save()
    await flush()
    expect(api.saveSurvey).toHaveBeenCalledWith(101, 12, {
      expectedProjectVersion: 7,
      surveyDate: '2026-09-09'
    })
    expect(api.getSurvey).toHaveBeenCalledTimes(2)
    expect(view.saved).not.toHaveBeenCalled()
    expect(view.child.value.discardChanges()).toBe(false)
    readback.resolve(
      metadata({ version: 14, surveyDate: '2026-09-10', conclusion: '权威回读结论' })
    )
    await pending
    await flush()
    expect(view.state().metadata.version).toBe(14)
    expect(textOf(view.root)).toContain('2026-09-10')
    expect(textOf(view.root)).toContain('权威回读结论')
    expect(view.saved).toHaveBeenCalledTimes(1)
    expect(view.child.value.isDirty()).toBe(false)
    expect(view.dirtyChange).toHaveBeenLastCalledWith(false)
  })

  it('preserves 不涉及 text and does not manufacture item false/availability fields in metadata PATCH', async () => {
    const view = render()
    await flush()
    await view.click('survey-edit')
    await view.input('survey-grounding', '不涉及')
    await view.input('survey-resource', '无需新增施工资源')
    await view.click('survey-save')
    expect(api.saveSurvey).toHaveBeenCalledWith(101, 12, {
      expectedProjectVersion: 7,
      grounding: '不涉及',
      constructionResource: '无需新增施工资源'
    })
    expect(api.saveSurvey.mock.calls[0][2]).not.toHaveProperty('surveyResult')
    expect(api.saveSurvey.mock.calls[0][2]).not.toHaveProperty('outsourced')
  })

  it('clears date and person explicitly with null rather than losing the PATCH fields', async () => {
    const view = render()
    await flush()
    await view.click('survey-edit')
    await view.input('survey-date', null)
    await view.input('survey-person', null)
    await view.click('survey-save')
    expect(api.saveSurvey.mock.calls[0][2]).toEqual({
      expectedProjectVersion: 7,
      surveyDate: null,
      surveyorUserId: null
    })
  })

  it('does not send locationCommand after merely mounting the real selector or changing other fields', async () => {
    const view = render()
    await flush()
    await view.click('survey-edit')
    await flush()
    expect(view.child.value.isDirty()).toBe(false)
    await view.input('survey-conclusion', '仅调整结论')
    await view.click('survey-save')
    expect(api.saveSurvey.mock.calls[0][2]).toEqual({
      expectedProjectVersion: 7,
      conclusion: '仅调整结论'
    })
    expect(locationApi.maintainLocation).not.toHaveBeenCalled()
  })

  it('builds reference versions without numeric coercion and sends location only for an actual edit', async () => {
    api.getSurvey.mockResolvedValue(
      metadata({
        preparationId: '00101',
        surveyorUserId: '00017',
        addressId: '00011',
        siteId: '00022',
        siteLocationId: '00033'
      } as unknown as Partial<SurveyMetadata>)
    )
    const view = render(
      false,
      preparation('00001' as unknown as number, '00101' as unknown as number),
      { id: '00001' as unknown as number, version: 7 }
    )
    await flush()
    await view.click('survey-edit')
    expect(view.state().locationDraft).toEqual({
      projectId: '00001',
      fallbackLocation: '现场 A / 机房',
      address: { id: '00011', expectedVersion: 2 },
      site: { id: '00022', expectedVersion: 3 },
      siteLocation: { id: '00033', expectedVersion: 4 }
    })
    await view.input('survey-person', '00018')
    view.state().locationDraft = { projectId: '00001', fallbackLocation: '新现场，待维护' }
    await view.state().save()
    expect(api.saveSurvey).toHaveBeenCalledWith('00101', 12, {
      expectedProjectVersion: 7,
      surveyorUserId: '00018',
      locationCommand: { projectId: '00001', fallbackLocation: '新现场，待维护' }
    })
    expect(api.getSurvey).toHaveBeenCalledWith('00101')
  })

  it('does not PATCH when the location is changed then restored with reordered keys', async () => {
    const view = render()
    await flush()
    await view.click('survey-edit')
    const original = JSON.parse(JSON.stringify(view.state().locationDraft))
    view.state().locationDraft = { projectId: 1, fallbackLocation: '临时位置' }
    expect(view.child.value.isDirty()).toBe(true)
    view.state().locationDraft = Object.fromEntries(Object.entries(original).reverse())
    await flush()
    expect(view.child.value.isDirty()).toBe(false)
    await view.state().save()
    expect(api.saveSurvey).not.toHaveBeenCalled()
  })

  it('shows unresolved location as 待维护 without mounting the selector', async () => {
    api.getSurvey.mockResolvedValue(
      metadata({
        locationResolutionStatus: 'UNRESOLVED',
        location: '现场口述位置',
        siteId: undefined,
        addressId: undefined,
        siteLocationId: undefined
      })
    )
    const view = render()
    await flush()
    expect(textOf(view.root)).toContain('待维护')
    expect(textOf(view.root)).toContain('现场口述位置')
    expect(locationApi.getSitePage).not.toHaveBeenCalled()
  })

  it('retains editing and dirty state after PATCH rejection, with no false saved event', async () => {
    api.saveSurvey.mockRejectedValueOnce(new Error('VALIDATION_FAILED'))
    const view = render()
    await flush()
    await view.click('survey-edit')
    await view.input('survey-conclusion', '未保存现场说明')
    await view.click('survey-save')
    expect(view.state().form.conclusion).toBe('未保存现场说明')
    expect(view.state().editing).toBe(true)
    expect(view.child.value.isDirty()).toBe(true)
    expect(textOf(view.root)).toContain('保存失败，输入已保留')
    expect(api.getSurvey).toHaveBeenCalledTimes(1)
    expect(view.saved).not.toHaveBeenCalled()
    expect(view.child.value.discardChanges()).toBe(true)
    expect(view.child.value.isDirty()).toBe(false)
    expect(view.state().form.conclusion).toBe('原结论')
  })

  it('distinguishes successful PATCH with failed GET and retries only GET before releasing dirty protection', async () => {
    const view = render()
    await flush()
    await view.click('survey-edit')
    await view.input('survey-conclusion', '已提交说明')
    api.getSurvey.mockRejectedValueOnce(new Error('READ_UNAVAILABLE'))
    await view.click('survey-save')
    expect(textOf(view.root)).toContain('保存已成功，但最新信息回读失败')
    expect(view.state().form.conclusion).toBe('已提交说明')
    expect(view.state().editing).toBe(true)
    expect(view.child.value.isDirty()).toBe(true)
    expect(view.child.value.discardChanges()).toBe(false)
    expect(view.node('survey-save')?.props?.disabled).toBe(true)
    await view.state().save()
    expect(api.saveSurvey).toHaveBeenCalledTimes(1)
    expect(view.saved).not.toHaveBeenCalled()
    api.getSurvey.mockResolvedValueOnce(metadata({ version: 15, conclusion: '权威已提交说明' }))
    await view.click('survey-reload')
    expect(api.saveSurvey).toHaveBeenCalledTimes(1)
    expect(view.state().metadata.version).toBe(15)
    expect(view.saved).toHaveBeenCalledTimes(1)
    expect(view.child.value.isDirty()).toBe(false)
  })

  it('blocks duplicate saves and discards during an in-flight PATCH', async () => {
    const patch = deferred<SurveyMetadata>()
    api.saveSurvey.mockReturnValueOnce(patch.promise)
    const view = render()
    await flush()
    await view.click('survey-edit')
    await view.input('survey-conclusion', '处理中')
    const pending = view.state().save()
    await view.state().save()
    expect(view.child.value.discardChanges()).toBe(false)
    expect(api.saveSurvey).toHaveBeenCalledTimes(1)
    patch.resolve(metadata())
    await pending
  })

  it('preserves edits on readonly and same-object version changes instead of refilling the form', async () => {
    const view = render()
    await flush()
    await view.click('survey-edit')
    await view.input('survey-conclusion', '保留编辑')
    view.props.readonly = true
    view.props.preparation.version++
    await flush()
    expect(view.state().form.conclusion).toBe('保留编辑')
    expect(api.getSurvey).toHaveBeenCalledTimes(1)
    await view.state().save()
    expect(api.saveSurvey).not.toHaveBeenCalled()
    expect(view.child.value.isDirty()).toBe(true)
    expect(view.child.value.discardChanges()).toBe(true)
  })

  it('preserves dirty input when parent refresh replaces same-ID props objects', async () => {
    const view = render()
    await flush()
    await view.click('survey-edit')
    await view.input('survey-conclusion', '父组件刷新时保留')
    view.props.project = { ...view.props.project, version: 8 }
    view.props.preparation = { ...view.props.preparation, version: 3 }
    await flush()
    expect(view.state().editing).toBe(true)
    expect(view.state().form.conclusion).toBe('父组件刷新时保留')
    expect(view.child.value.isDirty()).toBe(true)
    expect(api.getSurvey).toHaveBeenCalledTimes(1)
    await view.state().save()
    expect(api.saveSurvey).toHaveBeenCalledWith(101, 12, {
      expectedProjectVersion: 8,
      conclusion: '父组件刷新时保留'
    })
  })

  it('loads remote candidates and retains the selected ID when search results do not contain it', async () => {
    const view = render()
    await flush()
    await view.click('survey-edit')
    expect(api.getAssignmentCandidates).not.toHaveBeenCalled()
    await view.state().searchCandidates('张')
    expect(api.getAssignmentCandidates).toHaveBeenCalledWith(101, {
      keyword: '张',
      pageNo: 1,
      pageSize: 20
    })
    expect(view.state().candidateLabel(view.state().candidateOptions[0])).toBe(
      '张工 · 0017 · 交付部'
    )
    api.getAssignmentCandidates.mockResolvedValueOnce({ list: [], total: 0 })
    await view.state().searchCandidates('李')
    expect(view.state().candidateOptions[0].userId).toBe(17)
    expect(view.state().form.surveyorUserId).toBe(17)
  })

  it('ignores out-of-order candidate search responses and exposes search failures', async () => {
    const older = deferred<any>()
    const view = render()
    await flush()
    await view.click('survey-edit')
    api.getAssignmentCandidates.mockReturnValueOnce(older.promise)
    const pending = view.state().searchCandidates('旧')
    await view.state().searchCandidates('新')
    older.resolve({ list: [{ userId: 99, nickname: '旧候选' }], total: 1 })
    await pending
    expect(view.state().candidates[0].nickname).toBe('张工')
    api.getAssignmentCandidates.mockRejectedValueOnce(new Error('UNAVAILABLE'))
    await view.state().searchCandidates('错误')
    expect(view.state().candidateError).toContain('请重新搜索')
    expect(view.state().candidateLoading).toBe(false)
  })

  it.each(['project', 'preparation'])('isolates late metadata when switching %s', async (scope) => {
    const old = deferred<SurveyMetadata>()
    api.getSurvey.mockReturnValueOnce(old.promise)
    const view = render()
    await flush()
    api.getSurvey.mockResolvedValue(metadata({ preparationId: 202, conclusion: '新对象' }))
    if (scope === 'project') view.props.project = { id: 2, version: 8 }
    view.props.preparation = preparation(scope === 'project' ? 2 : 1, 202)
    await flush()
    old.resolve(metadata({ conclusion: '旧响应' }))
    await flush()
    expect(view.state().metadata.preparationId).toBe(202)
    expect(textOf(view.root)).toContain('新对象')
    expect(textOf(view.root)).not.toContain('旧响应')
  })

  it('isolates candidate responses after switching projects', async () => {
    const old = deferred<any>()
    api.getAssignmentCandidates.mockReturnValueOnce(old.promise)
    const view = render()
    await flush()
    await view.click('survey-edit')
    const pending = view.state().searchCandidates('旧项目')
    api.getSurvey.mockResolvedValue(metadata({ preparationId: 202 }))
    view.props.project = { id: 2, version: 8 }
    view.props.preparation = preparation(2, 202)
    await flush()
    old.resolve({ list: [{ userId: 88, nickname: '旧项目候选' }], total: 1 })
    await pending
    expect(view.state().candidates).toEqual([])
    expect(view.state().candidateLoading).toBe(false)
    expect(view.state().metadata.preparationId).toBe(202)
  })

  it.each(['PATCH', 'GET'])(
    'isolates late save %s across project changes without an old saved event',
    async (stage) => {
      const old = deferred<SurveyMetadata>()
      const view = render()
      await flush()
      await view.click('survey-edit')
      await view.input('survey-conclusion', '旧项目编辑')
      if (stage === 'PATCH') api.saveSurvey.mockReturnValueOnce(old.promise)
      else api.getSurvey.mockReturnValueOnce(old.promise)
      const pending = view.state().save()
      await flush()
      api.getSurvey.mockResolvedValue(metadata({ preparationId: 202, conclusion: '新项目结论' }))
      view.props.project = { id: 2, version: 8 }
      view.props.preparation = preparation(2, 202)
      await flush()
      old.resolve(metadata({ conclusion: '旧项目回读' }))
      await pending
      await flush()
      expect(view.state().metadata.preparationId).toBe(202)
      expect(view.state().metadata.conclusion).toBe('新项目结论')
      expect(view.state().editing).toBe(false)
      expect(view.saved).not.toHaveBeenCalled()
      expect(api.getSurvey.mock.calls.filter(([id]) => id === 101)).toHaveLength(
        stage === 'PATCH' ? 1 : 2
      )
    }
  )

  it('renders a recoverable load error and rejects mismatched Owner readback', async () => {
    api.getSurvey.mockRejectedValueOnce(new Error('UNAVAILABLE'))
    const view = render()
    await flush()
    expect(textOf(view.root)).toContain('工勘基本信息加载失败')
    expect(view.node('survey-edit')).toBeUndefined()
    api.getSurvey.mockResolvedValueOnce(metadata({ preparationId: 999 }))
    await view.click('survey-reload')
    expect(view.state().metadata).toBeNull()
    await view.click('survey-reload')
    expect(view.state().metadata.preparationId).toBe(101)
  })

  it('does not query or write with a preparation from another project', async () => {
    const view = render(false, preparation(2))
    await flush()
    expect(api.getSurvey).not.toHaveBeenCalled()
    expect(textOf(view.root)).toContain('工勘准备与当前项目不一致')
    await view.state().save()
    expect(api.saveSurvey).not.toHaveBeenCalled()
  })

  it('does not apply late responses or emit saved after unmount', async () => {
    const old = deferred<SurveyMetadata>()
    api.saveSurvey.mockReturnValueOnce(old.promise)
    const view = render()
    await flush()
    await view.click('survey-edit')
    await view.input('survey-conclusion', '离开前保存')
    const pending = view.state().save()
    view.app.unmount()
    apps.splice(apps.indexOf(view.app), 1)
    old.resolve(metadata())
    await pending
    expect(api.getSurvey).toHaveBeenCalledTimes(1)
    expect(view.saved).not.toHaveBeenCalled()
  })
})
