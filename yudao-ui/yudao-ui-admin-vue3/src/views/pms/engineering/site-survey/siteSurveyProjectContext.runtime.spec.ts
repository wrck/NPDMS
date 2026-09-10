import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, reactive, ref } from 'vue'
import { mount, passthrough, tableColumn } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
import SurveyPage from './index.vue'
import BusinessViewHost from '@/components/BusinessView/BusinessViewHost.vue'

const api = vi.hoisted(() => Object.fromEntries(['getSiteSurveyPage', 'getSiteSurvey', 'getDefaultFormSchema', 'deleteSiteSurvey', 'updateSiteSurvey', 'createSiteSurvey', 'confirmSiteSurvey', 'rejectSiteSurvey', 'archiveSiteSurvey'].map(key => [key, vi.fn()])))
const message = vi.hoisted(() => ({ warning: vi.fn(), success: vi.fn(), error: vi.fn(), confirm: vi.fn(), delConfirm: vi.fn() }))
const push = vi.hoisted(() => vi.fn())
vi.mock('@/api/pms/engineering/site-survey', () => api)
vi.mock('@/api/pms/project/projects', () => ({ __v_isRef: false, getProjectPage: vi.fn() }))
vi.mock('@/api/system/user', () => ({ getSimpleUserList: async () => [] }))
vi.mock('@/api/pms/platform/dynamic-form', () => ({}))
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => message }))
vi.mock('@/utils/dict', () => ({ DICT_TYPE: {}, getIntDictOptions: () => [] }))
vi.mock('vue-router', () => ({ onBeforeRouteLeave: vi.fn(), useRoute: () => ({ query: {} }), useRouter: () => ({ push }) }))
vi.mock('@/views/pms/project/project-master-detail/components/ProjectRequirementAnalysisPanel.vue', () => ({ default: { render: () => null } }))
vi.mock('@/views/pms/platform/dynamic-form/instance/DynamicFormInstanceContent.vue', () => ({ default: { render: () => null } }))
vi.mock('./SiteSurveyDynamicForm.vue', () => ({ default: { render: () => null } }))
vi.mock('@/views/pms/project/acceptance-report/index.vue', () => ({ default: { render: () => null } }))

const apps: { unmount: () => void }[] = []
const flush = async () => { for (let i = 0; i < 12; i++) { await nextTick(); await Promise.resolve() } }
const components = Object.fromEntries(['ElLink', 'ElTable', 'ElRow', 'ElCol', 'ElInput', 'ElSelect', 'ElOption', 'ElDatePicker', 'ElCheckbox', 'ElPagination', 'PmsEntitySelect', 'PmsLocationSelector', 'Editor', 'DictTag'].map(name => [name, passthrough]))
const options = { ...components, ElTableColumn: tableColumn }
const render = (props: Record<string, any> = {}) => {
  const input = reactive(props)
  const page = ref<any>()
  const mounted = mount(defineComponent({ setup: () => () => h(SurveyPage, { ...input, ref: page }) }), {}, options)
  apps.push(mounted.app)
  return { state: page.value.$.setupState, input, exposed: page.value }
}
const record = (projectId: number | string = 7, id: number | string = 12) => ({ id, projectId, code: 'SS', name: 'old', status: 0, location: 'onsite' })
beforeEach(() => {
  vi.clearAllMocks()
  api.getSiteSurveyPage.mockResolvedValue({ list: [], total: 0 })
  api.getSiteSurvey.mockResolvedValue(record())
  api.getDefaultFormSchema.mockResolvedValue({ revisionId: 9, revisionVersion: 1, formRulesJson: [] })
  message.confirm.mockResolvedValue(undefined)
})
afterEach(() => apps.splice(0).forEach(app => app.unmount()))

describe('SOL site-survey shared project and standalone context', () => {
  it('pins list and new-form project, even if filter state is changed, without coercing string IDs', async () => {
    const { state } = render({ projectId: '2099999999999999999' })
    await flush()
    state.query.projectId = 8
    await state.load()
    expect(api.getSiteSurveyPage).toHaveBeenLastCalledWith(expect.objectContaining({ projectId: '2099999999999999999' }))
    await state.openForm()
    expect(state.form.projectId).toBe('2099999999999999999')
    expect(state.form.formRevisionId).toBe(9)
    state.form.projectId = 8
    expect(await state.save()).toBe(false)
    expect(api.createSiteSurvey).not.toHaveBeenCalled()
  })
  it('does not open an object from another project', async () => {
    api.getSiteSurvey.mockResolvedValue(record(8))
    const { state } = render({ projectId: 7, objectId: 12 })
    await flush()
    expect(state.formVisible).toBe(false)
    expect(message.warning).toHaveBeenCalledWith('该工勘不属于当前项目')
  })
  it('opens a precise string object in its current project and keeps same-ID refreshes intact', async () => {
    api.getSiteSurvey.mockResolvedValue(record('7', '2099999999999999999'))
    const { state, input } = render({ projectId: 7, objectId: '2099999999999999999', allowedActions: ['QUERY', 'UPDATE'] })
    await flush()
    expect(api.getSiteSurvey).toHaveBeenCalledWith('2099999999999999999')
    expect(state.formVisible).toBe(true)
    state.form.name = 'unsaved'
    input.projectId = '7'
    input.allowedActions = ['QUERY', 'UPDATE', 'CONFIRM']
    await flush()
    expect(state.form.name).toBe('unsaved')
    expect(api.getSiteSurvey).toHaveBeenCalledTimes(1)
  })
  it('retains standalone editing, project selection, linked deletion protection and saved/changed events', async () => {
    const saved = vi.fn(), changed = vi.fn()
    const { state } = render({ onSaved: saved, onChanged: changed })
    state.query.projectId = 8
    await state.load()
    expect(api.getSiteSurveyPage).toHaveBeenLastCalledWith(expect.objectContaining({ projectId: 8 }))
    await state.remove({ id: 12, outsourceRequestId: 99 })
    expect(message.delConfirm).not.toHaveBeenCalled()
    expect(api.deleteSiteSurvey).not.toHaveBeenCalled()
    await state.openForm(record())
    state.form.name = 'updated'
    state.formRef = { validate: vi.fn() }
    expect(await state.save()).toBe(true)
    expect(api.updateSiteSurvey).toHaveBeenCalledWith(expect.objectContaining({ name: 'updated' }))
    expect(saved).toHaveBeenCalledTimes(1)
    expect(changed).toHaveBeenCalledTimes(1)
    expect(api.confirmSiteSurvey).not.toHaveBeenCalled()
  })
  it.each([{ readonly: true }, { allowedActions: ['QUERY'] }, { allowedActions: [] }])('rejects direct mutation handlers under %j', async permission => {
    const { state } = render({ projectId: 7, ...permission })
    await flush()
    await state.openForm()
    await state.openForm(record())
    Object.assign(state.form, record(), { outsourceRequired: true, formExtraValues: { extra_railTrayRequired: true } })
    await state.save()
    await state.startOutsource()
    await state.performSurveyAction('procurement')
    await state.remove(record())
    for (const action of ['confirm', 'reject', 'archive']) await state.handleAction({ ...record(), status: action === 'archive' ? 1 : 0 }, action)
    for (const name of ['createSiteSurvey', 'updateSiteSurvey', 'deleteSiteSurvey', 'confirmSiteSurvey', 'rejectSiteSurvey', 'archiveSiteSurvey']) expect(api[name]).not.toHaveBeenCalled()
    expect(push).not.toHaveBeenCalled()
    expect(message.confirm).not.toHaveBeenCalled()
  })
  it('intersects action allowlists and rechecks permission after confirmation', async () => {
    const { state, input } = render({ projectId: 7, allowedActions: ['QUERY', 'CONFIRM'] })
    await state.handleAction(record(), 'reject')
    await state.handleAction(record(), 'confirm')
    expect(api.confirmSiteSurvey).toHaveBeenCalledTimes(1)
    expect(api.rejectSiteSurvey).not.toHaveBeenCalled()
    let accept!: () => void
    message.confirm.mockImplementationOnce(() => new Promise<void>(resolve => { accept = resolve }))
    const pending = state.handleAction(record(), 'confirm')
    input.readonly = true
    await flush()
    accept()
    await pending
    expect(api.confirmSiteSurvey).toHaveBeenCalledTimes(1)
  })
  it('isolates delayed list and detail responses after project changes', async () => {
    let oldList!: (value: any) => void, oldDetail!: (value: any) => void
    api.getSiteSurveyPage.mockImplementationOnce(() => new Promise(resolve => { oldList = resolve }))
    const { state, input } = render({ projectId: 7 })
    api.getSiteSurvey.mockImplementationOnce(() => new Promise(resolve => { oldDetail = resolve }))
    const opening = state.openForm(record())
    await flush()
    api.getSiteSurveyPage.mockResolvedValue({ list: [record(8, 13)], total: 1 })
    input.projectId = 8
    await flush()
    oldList({ list: [record()], total: 99 })
    oldDetail(record())
    await opening
    await flush()
    expect(state.rows).toEqual([record(8, 13)])
    expect(state.total).toBe(1)
    expect(state.formVisible).toBe(false)
  })
  it('preserves dirty values across permission changes and confirms without discarding until phase two', async () => {
    const dirty = vi.fn()
    const { state, input, exposed } = render({ projectId: 7, onDirtyChange: dirty })
    await state.openForm(record())
    state.form.name = 'local'
    state.form.formExtraValues = { note: 'retained' }
    input.allowedActions = ['QUERY']
    await flush()
    expect(state.readonly).toBe(true)
    state.updateDynamicForm({ name: 'bad' })
    expect(state.form.name).toBe('local')
    expect(exposed.isDirty()).toBe(true)
    expect(dirty).toHaveBeenLastCalledWith(true)
    message.confirm.mockRejectedValueOnce(new Error('cancel'))
    expect(await exposed.confirmLeave()).toBe(false)
    expect(state.form.name).toBe('local')
    expect(await exposed.requestLeave()).toBe(true)
    expect(state.form.formExtraValues).toEqual({ note: 'retained' })
    expect(exposed.isDirty()).toBe(true)
    expect(exposed.discardChanges()).toBe(true)
    expect(exposed.isDirty()).toBe(false)
  })
  it('Host cancels a dirty object switch, keeps stale approved switches intact, then changes exact object', async () => {
    const input = reactive<any>({ registration: { id: 1, version: 1, status: 'PUBLISHED', componentKey: 'SOL_SITE_SURVEY', componentVersion: '1', ownerContext: 'SOL', entityType: 'SITE_SURVEY', viewSource: 'PAGE' }, resolvedContext: { project: { id: 7 }, businessObjectId: 12, taskId: 4 }, allowedActions: ['QUERY', 'UPDATE'] })
    const host = ref<any>(), blocked = vi.fn()
    const mounted = mount(defineComponent({ setup: () => () => h(BusinessViewHost, { ...input, ref: host, onSwitchBlocked: blocked }) }), {}, options)
    apps.push(mounted.app)
    await flush()
    const page = host.value.$.setupState.contentRef.$.setupState
    page.form.name = 'local'
    await flush()
    message.confirm.mockRejectedValueOnce(new Error('cancel'))
    input.resolvedContext.businessObjectId = 13
    await flush()
    expect(blocked).toHaveBeenCalledOnce()
    expect(page.form.name).toBe('local')
    input.resolvedContext.businessObjectId = 12
    await flush()
    let approve!: () => void
    message.confirm.mockImplementationOnce(() => new Promise<void>(resolve => { approve = resolve }))
    input.resolvedContext = { project: { id: 7 }, businessObjectId: 14, taskId: 4 }
    await flush()
    input.resolvedContext = { project: { id: 7 }, businessObjectId: 12, taskId: 4 }
    await flush()
    approve()
    await flush()
    expect(page.form.name).toBe('local')
    expect(host.value.isDirty()).toBe(true)
    api.getSiteSurvey.mockResolvedValue(record(7, 15))
    input.resolvedContext = { project: { id: 7 }, businessObjectId: 15, taskId: 4 }
    await flush()
    expect(api.getSiteSurvey).toHaveBeenLastCalledWith(15)
    expect(host.value.isDirty()).toBe(false)
  })
})
