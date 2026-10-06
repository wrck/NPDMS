import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, reactive, ref } from 'vue'
import { mount, passthrough, tableColumn } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
import SurveyPage from './index.vue'
import BusinessViewHost from '@/components/BusinessView/BusinessViewHost.vue'
import PublicEntityHost from '@/components/BusinessEntity/BusinessEntityHost.vue'
import request from '@/config/axios'
import { resolveStandaloneBusinessEntityView } from '@/components/BusinessView/registry'
import { OperationRejected, OperationClient, routeSelection, selectionClient } from '@/components/BusinessView/operationClient'
import { pinSurveySave } from './surveyReceiptIntent'

const api = vi.hoisted(() => Object.fromEntries(['getSiteSurveyPage', 'getSiteSurvey', 'getDefaultFormSchema', 'saveSiteSurveyReceipt', 'siteSurveyActionReceipt', 'deleteSiteSurvey', 'updateSiteSurvey', 'createSiteSurvey', 'confirmSiteSurvey', 'rejectSiteSurvey', 'archiveSiteSurvey'].map(key => [key, vi.fn()])))
const projects = vi.hoisted(() => ({ getProjectPage: vi.fn(), getProject: vi.fn() }))
const message = vi.hoisted(() => ({ warning: vi.fn(), success: vi.fn(), error: vi.fn(), confirm: vi.fn(), delConfirm: vi.fn() }))
const push = vi.hoisted(() => vi.fn())
const navigation = vi.hoisted(() => ({ leave: vi.fn(), update: vi.fn() }))
// Registry renders other business views too; all network calls remain test doubles during module loading.
vi.mock('@/config/axios/service', () => ({ service: { defaults: { transformResponse: [] } }, isRelogin: { show: false } }))
vi.mock('@/config/axios', () => ({ default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() } }))
vi.mock('@/api/pms/engineering/site-survey/entity', async original => ({ ...(await original<typeof import('@/api/pms/engineering/site-survey/entity')>()), ...api }))
vi.mock('@/api/pms/project/projects', () => ({ __v_isRef: false, ...projects }))
vi.mock('@/api/pms/project/execution-operations', () => ({ inspectOperationCapabilities: async (query: { projectId: unknown; nodeKind: string; nodeId: unknown }) => ({ node: { projectId: query.projectId, kind: query.nodeKind, id: query.nodeId, status: 'RUNNING' }, reason: 'LEGACY_BINDING', actions: [] }) }))
vi.mock('@/api/pms/project/task-business', () => ({ getTaskBusinessContext: vi.fn() }))
vi.mock('@/api/pms/project/stage-business', () => ({ getStageBusinessContext: vi.fn() }))
vi.mock('@/api/system/user', () => ({ getSimpleUserList: async () => [] }))
vi.mock('@/store/modules/user', () => ({ useUserStore: () => ({ getUser: { id: 8 } }) }))
vi.mock('@/api/pms/platform/dynamic-form', () => ({}))
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => message }))
vi.mock('@/utils/permission', () => ({ checkPermi: () => true, checkRole: () => true }))
vi.mock('@/utils/dict', () => ({ DICT_TYPE: {}, getIntDictOptions: () => [] }))
vi.mock('vue-router', () => ({ onBeforeRouteLeave: navigation.leave, onBeforeRouteUpdate: navigation.update, useRoute: () => ({ query: {} }), useRouter: () => ({ push }) }))
vi.mock('@/views/pms/delivery-business/requirement-analysis/entity/EntityPanel.vue', () => ({ default: { render: () => null } }))
vi.mock('@/views/pms/platform/dynamic-form/instance/DynamicFormInstanceContent.vue', () => ({ default: { render: () => null } }))
vi.mock('./SiteSurveyDynamicForm.vue', () => ({ default: { render: () => null } }))
vi.mock('@/views/pms/acceptance/acceptance-report/index.vue', () => ({ default: { render: () => null } }))

// Keep the real registry and Host, while unrelated business pages have no module side effects.
vi.mock('./OwnerCompletionEntry.vue', () => ({ default: { render: () => null } }))
vi.mock('./ProjectMembersBusinessView.vue', () => ({ default: { render: () => null } }))
vi.mock('@/views/pms/project/project-master-detail/components/ProjectDurationPanel.vue', () => ({ default: { render: () => null } }))
vi.mock('@/views/pms/project/project-master-detail/components/ProjectSchedulePanel.vue', () => ({ default: { render: () => null } }))
vi.mock('@/views/pms/engineering/solution-reviewed/index.vue', () => ({ default: { render: () => null } }))

const apps: { unmount: () => void }[] = []
const flush = async () => { for (let i = 0; i < 12; i++) { await nextTick(); await Promise.resolve() } }
const components = Object.fromEntries(['ElLink', 'ElTable', 'ElRow', 'ElCol', 'ElInput', 'ElSelect', 'ElOption', 'ElDatePicker', 'ElCheckbox', 'ElPagination', 'PmsEntitySelect', 'PmsLocationSelector', 'Editor', 'DictTag'].map(name => [name, passthrough]))
// Repeated refusals rerender the form ref; keep the real ElForm validation interface on its test double.
const testForm = defineComponent({ setup: (_, { slots, expose }) => { expose({ validate: async () => true }); return () => h('div', slots.default?.()) } })
const options = { ...components, ElTableColumn: tableColumn, ElForm: testForm }
const render = (props: Record<string, any> = {}) => {
  const input = reactive(props)
  const page = ref<any>()
  const mounted = mount(defineComponent({ setup: () => () => h(SurveyPage, { ...input, ref: page }) }), {}, options)
  apps.push(mounted.app)
  return { state: page.value.$.setupState, input, exposed: page.value }
}
const record = (projectId: number | string = 7, id: number | string = 12) => ({ id, projectId, code: 'SS', name: 'old', status: 0, version: 0, location: 'onsite' })
const records = new Map<string, any>()
const makeReceipt = (row: any, state = '0', deleted = false) => ({ outcome: ['1','3'].includes(state) ? 'EFFECTED' : 'SAVED',
  entityRef: { tenantId: 1, ownerModule: 'SOL', entityType: 'siteSurvey', entityId: row.id }, newConcurrencyBasis: row.version,
  references: [{ kind: 'COMMAND', ownerModule: 'SOL', value: JSON.stringify({ id: String(row.id), projectId: String(row.projectId), version: row.version, state, deleted }) }] })
beforeEach(() => {
  vi.resetAllMocks()
  api.getSiteSurveyPage.mockResolvedValue({ list: [], total: 0 })
  records.clear()
  api.getSiteSurvey.mockImplementation(async (id: unknown) => records.get(String(id)) || record())
  api.saveSiteSurveyReceipt.mockImplementation(async (data: any) => {
    const row = { ...data, id: data.id ?? 12, code: 'SS', version: (data.version ?? 0) + 1, status: 0 }
    records.set(String(row.id), row)
    return makeReceipt(row)
  })
  api.siteSurveyActionReceipt.mockImplementation(async (name: string, id: any, version: number, projectId: any) => {
    const state = { confirm: '1', reject: '2', archive: '3', delete: 'DELETED' }[name]!
    const row = { ...record(projectId, id), version: version + 1, status: Number(state) }
    records.set(String(id), row)
    return makeReceipt(row,state,name==='delete')
  })
  api.getDefaultFormSchema.mockResolvedValue({ revisionId: 9, revisionVersion: 1, formRulesJson: [], fieldBindings: {}, fieldCatalog: [] })
  projects.getProject.mockResolvedValue({ id: 7, projectCode: 'P-7', projectName: '宿主项目' })
  message.confirm.mockResolvedValue(undefined)
})
afterEach(() => apps.splice(0).forEach(app => app.unmount()))

describe('SOL site-survey shared project and standalone context', () => {
  it('freezes the opened task execution and preserves unsaved content when a new round arrives', async () => {
    const execution = { projectId: 7, taskId: 4, executionId: '2099999999999999999', executionVersion: 1, roundNo: 1, writable: true }
    const { state, input } = render({ projectId: 7, taskId: 4, taskExecution: execution, allowedActions: ['QUERY', 'UPDATE'] })
    await flush()
    await state.openForm(record())
    state.form.name = '本轮未保存内容'
    input.taskExecution = { ...execution, executionId: '2099999999999999998', roundNo: 2 }
    await flush()
    expect(state.formVisible).toBe(true)
    expect(state.form.name).toBe('本轮未保存内容')
    state.formRef = { validate: vi.fn() }
    api.saveSiteSurveyReceipt.mockRejectedValueOnce(new Error('stale execution'))
    expect(await state.save()).toBe(false)
    expect(api.saveSiteSurveyReceipt).toHaveBeenCalledWith(expect.objectContaining({ execution: { task: execution } }), expect.any(String))
    expect(state.formVisible).toBe(true)
    expect(state.form.name).toBe('本轮未保存内容')
  })
  it('carries the stage identity on save and keeps the click-time execution through confirmation', async () => {
    const execution = { projectId: 7, stageId: 5, executionId: '2099999999999999999', executionVersion: 1, roundNo: 2, writable: true }
    const { state, input } = render({ projectId: 7, stageExecution: execution, allowedActions: ['QUERY', 'CREATE', 'CONFIRM'] })
    await flush()
    await state.openForm()
    expect(state.form.name).toBe('宿主项目')
    expect(state.form.surveyorUserId).toBe(8)
    Object.assign(state.form, { code: 'NEW', name: '阶段工勘', location: 'onsite', formRevisionId: undefined })
    state.formRef = { validate: vi.fn() }
    await state.save()
    expect(api.saveSiteSurveyReceipt).toHaveBeenCalledWith(expect.objectContaining({ execution: { stage: execution } }), expect.any(String))
    let accept!: () => void
    message.confirm.mockImplementationOnce(() => new Promise<void>(resolve => { accept = resolve }))
    const pending = state.handleAction(record(), 'confirm')
    input.stageExecution = { ...execution, executionVersion: 2 }
    await flush()
    accept()
    await pending
    expect(api.siteSurveyActionReceipt).toHaveBeenCalledWith('confirm',12,0,7,expect.any(String),{ stage: execution })
  })
  it('pins list and new-form project, even if filter state is changed, without coercing string IDs', async () => {
    const { state } = render({ projectId: '2099999999999999999' })
    await flush()
    state.query.projectId = 8
    await state.load()
    expect(api.getSiteSurveyPage).toHaveBeenLastCalledWith(expect.objectContaining({ projectId: '2099999999999999999' }))
    await state.openForm()
    expect(state.form.projectId).toBe('2099999999999999999')
    expect(state.form.name).toBe('宿主项目')
    expect(state.form.surveyorUserId).toBe(8)
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
  it('keeps task entry and context refresh on the list until the user opens details', async () => {
    const { state, input } = render({ projectId: 7, objectId: 12, taskId: 4, allowedActions: ['QUERY', 'UPDATE'] })
    await flush()
    expect(api.getSiteSurveyPage).toHaveBeenCalled()
    expect(api.getSiteSurvey).not.toHaveBeenCalled()
    expect(state.formVisible).toBe(false)
    await state.load()
    expect(state.formVisible).toBe(false)
    await state.openForm(record(), true)
    expect(api.getSiteSurvey).toHaveBeenCalledWith(12)
    expect(state.formVisible).toBe(true)
    expect(state.detailReadonly).toBe(true)
    input.taskId = 5
    await flush()
    expect(state.formVisible).toBe(false)
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
    expect(api.saveSiteSurveyReceipt).toHaveBeenCalledWith(expect.objectContaining({ name: 'updated' }),expect.any(String))
    expect(saved).toHaveBeenCalledTimes(1)
    expect(changed).toHaveBeenCalledTimes(1)
    expect(api.confirmSiteSurvey).not.toHaveBeenCalled()
  })
  it.each([{ readonly: true }, { allowedActions: ['QUERY'] }, { allowedActions: [] }])('rejects direct mutation handlers under %j', async permission => {
    const { state } = render({ projectId: 7, ...permission })
    await flush()
    await state.openForm()
    await state.openForm(record())
    Object.assign(state.form, record(), { outsourceRequired: true, businessValues: { railTrayRequired: true } })
    await state.save()
    await state.startOutsource()
    await state.performSurveyAction('procurement')
    await state.remove(record())
    for (const action of ['confirm', 'reject', 'archive']) await state.handleAction({ ...record(), status: action === 'archive' ? 1 : 0 }, action)
    for (const name of ['saveSiteSurveyReceipt','siteSurveyActionReceipt','createSiteSurvey', 'updateSiteSurvey', 'deleteSiteSurvey', 'confirmSiteSurvey', 'rejectSiteSurvey', 'archiveSiteSurvey']) expect(api[name]).not.toHaveBeenCalled()
    expect(push).not.toHaveBeenCalled()
    expect(message.confirm).not.toHaveBeenCalled()
  })
  it('intersects action allowlists and rechecks permission after confirmation', async () => {
    const { state, input } = render({ projectId: 7, allowedActions: ['QUERY', 'CONFIRM'] })
    await state.handleAction(record(), 'reject')
    await state.handleAction(record(), 'confirm')
    expect(api.siteSurveyActionReceipt).toHaveBeenCalledTimes(1)
    expect(api.rejectSiteSurvey).not.toHaveBeenCalled()
    let accept!: () => void
    message.confirm.mockImplementationOnce(() => new Promise<void>(resolve => { accept = resolve }))
    const pending = state.handleAction(record(), 'confirm')
    input.readonly = true
    await flush()
    accept()
    await pending
    expect(api.siteSurveyActionReceipt).toHaveBeenCalledTimes(1)
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
    state.form.extensionValues = { note: 'retained' }
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
    expect(state.form.extensionValues).toEqual({ note: 'retained' })
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
    await page.openForm(record())
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
    expect(page.formVisible).toBe(false)
    expect(api.getSiteSurvey).toHaveBeenLastCalledWith(12)
    await page.openForm(record(7, 15))
    expect(api.getSiteSurvey).toHaveBeenLastCalledWith(15)
    expect(host.value.isDirty()).toBe(false)
  })
})

describe('SOL site-survey receipt recovery and exact reopen', () => {
  it('resolves the catalog alias to the same professional project view without coercing project identity', () => {
    const view = resolveStandaloneBusinessEntityView('sol_site_survey')!
    expect(view.component).toBe(SurveyPage)
    expect(view.resolve({ id: '9007199254740993' } as any)).toEqual({ projectId: '9007199254740993' })
  })
  it('opens template-free basic entry if no default dynamic form is available', async () => {
    api.getDefaultFormSchema.mockRejectedValueOnce(new Error('no default form'))
    const { state } = render({ projectId: 7 })
    await state.openForm()
    expect(state.formVisible).toBe(true)
    expect(state.form.formRevisionId).toBeUndefined()
    expect(state.form.name).toBe('宿主项目')
    expect(state.form.businessValues).toEqual({})
  })
  it('reopens the committed Snowflake object at the precise receipt version', async () => {
    const { state } = render({ projectId: 7 })
    await state.openForm(); Object.assign(state.form,{ name:'new',location:'onsite',formRevisionId:undefined })
    state.formRef = { validate: vi.fn() }
    api.saveSiteSurveyReceipt.mockImplementationOnce(async data => {
      const row = {...data,id:'9007199254740993',version:1,status:0,code:'GENERATED'}
      records.set(row.id,row); return makeReceipt(row)
    })
    expect(await state.save()).toBe(true)
    expect(api.getSiteSurvey).toHaveBeenLastCalledWith('9007199254740993')
    expect(state.form.id).toBe('9007199254740993')
    expect(state.form.version).toBe(1)
    expect(state.form.code).toBe('GENERATED')
    expect(state.pendingIntent).toBeUndefined()
    expect(state.receiptResult.id).toBe('9007199254740993')
  })
  it('pins a lost create response, blocks new writes and leaving, and recovers the same key/body', async () => {
    const { state, exposed } = render({ projectId: 7 })
    await state.openForm(); Object.assign(state.form,{ name:'original',location:'onsite',formRevisionId:undefined,businessValues:{powerTypes:['AC']}})
    state.formRef = { validate: vi.fn() }
    api.saveSiteSurveyReceipt.mockRejectedValueOnce(new Error('response lost'))
    expect(await state.save()).toBe(false)
    expect(exposed.isDirty()).toBe(true)
    expect(await exposed.requestLeave()).toBe(false)
    expect(exposed.discardChanges()).toBe(false)
    expect(state.readonly).toBe(true)
    state.form.name = 'later mutation';state.form.businessValues.powerTypes.push('DC')
    expect(await state.save()).toBe(false)
    await state.handleAction(record(),'confirm')
    expect(api.siteSurveyActionReceipt).not.toHaveBeenCalled()
    expect(await state.recoverOperation()).toBe(true)
    const [first,second] = api.saveSiteSurveyReceipt.mock.calls
    expect(second).toEqual(first)
    expect(second[0].name).toBe('original')
    expect(second[0].businessValues.powerTypes).toEqual(['AC'])
    expect(state.form.name).toBe('original')
    expect(state.pendingIntent).toBeUndefined()
    expect(exposed.isDirty()).toBe(false)
  })
  it('coalesces duplicate save and recovery clicks while a request is in flight', async () => {
    const { state } = render({ projectId: 7 })
    await state.openForm(); Object.assign(state.form,{location:'onsite',formRevisionId:undefined});state.formRef={validate:vi.fn()}
    let settle!: (value: unknown)=>void
    api.saveSiteSurveyReceipt.mockImplementationOnce(()=>new Promise(resolve=>{settle=resolve}))
    const first=state.save();await flush()
    expect(await state.save()).toBe(false)
    expect(await state.recoverOperation()).toBe(false)
    expect(api.saveSiteSurveyReceipt).toHaveBeenCalledTimes(1)
    records.set('12',{...record(),version:1})
    settle(makeReceipt({...record(),version:1}));expect(await first).toBe(true)
  })
  it('keeps a committed receipt through read failure and retries only its read', async () => {
    const { state } = render({ projectId: 7 })
    await state.openForm();Object.assign(state.form,{location:'onsite',formRevisionId:undefined});state.formRef={validate:vi.fn()}
    api.getSiteSurvey.mockRejectedValueOnce(new Error('read denied'))
    expect(await state.save()).toBe(false)
    expect(state.receiptResult.id).toBe('12');expect(state.pendingIntent).toBeUndefined()
    expect(state.form.id).toBe('12');expect(state.form.version).toBe(1)
    expect(state.receiptReadError).toContain('操作已完成')
    expect(state.readonly).toBe(true)
    expect(await state.save()).toBe(false)
    expect(await state.reopenFromReceipt()).toBe(true)
    expect(api.saveSiteSurveyReceipt).toHaveBeenCalledTimes(1)
    expect(state.receiptReadError).toBe('')
  })
  it.each([{id:99},{projectId:8},{version:2}])('refuses a different actual object/project/version %j after commit', async mismatch => {
    const { state } = render({projectId:7});await state.openForm()
    Object.assign(state.form,{location:'onsite',formRevisionId:undefined});state.formRef={validate:vi.fn()}
    api.getSiteSurvey.mockResolvedValueOnce({...record(),version:1,...mismatch})
    expect(await state.save()).toBe(false)
    expect(state.receiptResult).toEqual(expect.objectContaining({id:'12',projectId:'7',version:1}))
    expect(state.receiptReadError).toContain('操作已完成')
    expect(state.receiptReopenRequired).toBe(true)
    expect(state.form.projectId).toBe(7)
  })
  it('allows a corrected new intent after a definite refusal, but retains uncertainty after refusal during recovery', async () => {
    const { state } = render({projectId:7});await state.openForm()
    Object.assign(state.form,{location:'onsite',formRevisionId:undefined});state.formRef={validate:vi.fn()}
    api.saveSiteSurveyReceipt.mockRejectedValueOnce(new OperationRejected(400,'invalid input'))
    expect(await state.save()).toBe(false);expect(state.pendingIntent).toBeUndefined();expect(state.readonly).toBe(false)
    api.saveSiteSurveyReceipt.mockRejectedValueOnce(new Error('lost'))
    expect(await state.save()).toBe(false)
    expect(api.saveSiteSurveyReceipt.mock.calls.length, JSON.stringify(message.warning.mock.calls)).toBe(2)
    expect(state.pendingIntent, JSON.stringify(message.warning.mock.calls)).toBeDefined()
    api.saveSiteSurveyReceipt.mockRejectedValueOnce(new OperationRejected(403,'permission revoked'))
    expect(await state.recoverOperation()).toBe(false)
    expect(state.pendingIntent).toBeDefined();expect(state.readonly).toBe(true)
    expect(api.saveSiteSurveyReceipt.mock.calls[1][1]).toBe(api.saveSiteSurveyReceipt.mock.calls[2][1])
    expect(api.saveSiteSurveyReceipt.mock.calls[0][1]).not.toBe(api.saveSiteSurveyReceipt.mock.calls[1][1])
  })
  it('replays deletion with its original object, basis and key without reading a deleted row', async () => {
    const {state}=render({projectId:7,allowedActions:['QUERY','DELETE']});await flush()
    api.siteSurveyActionReceipt.mockRejectedValueOnce(new Error('lost delete response'))
    await state.remove(record())
    expect(state.pendingIntent).toBeDefined()
    expect(await state.recoverOperation()).toBe(true)
    expect(api.siteSurveyActionReceipt.mock.calls[1]).toEqual(api.siteSurveyActionReceipt.mock.calls[0])
    expect(api.getSiteSurvey).not.toHaveBeenCalled()
    expect(state.receiptResult.deleted).toBe(true)
  })
  it('does not reopen or apply an original-project receipt into a forced new project context', async () => {
    const {state,input}=render({projectId:7});await state.openForm()
    Object.assign(state.form,{location:'onsite',formRevisionId:undefined});state.formRef={validate:vi.fn()}
    api.saveSiteSurveyReceipt.mockRejectedValueOnce(new Error('lost'))
    await state.save();input.projectId=8;await flush()
    expect(await state.recoverOperation()).toBe(false)
    expect(api.saveSiteSurveyReceipt.mock.calls[1][0].projectId).toBe(7)
    expect(state.receiptResult.projectId).toBe('7')
    expect(state.formVisible).toBe(false)
    expect(api.getSiteSurvey).not.toHaveBeenCalled()
  })
  it('freezes wire values while retaining the original controlled client routing symbol', () => {
    const selection={task:{projectId:7,taskId:4,executionContractId:10,contractVersion:1,planVersionId:2,executionId:3}}
    const client=new OperationClient({projectId:7,nodeKind:'TASK',nodeId:4},selection,{inspect:vi.fn(),submit:vi.fn(),newKey:()=> 'key'})
    const data:any={projectId:7,name:'original',businessValues:{powerTypes:['AC']},execution:routeSelection(selection,client)}
    const pinned=pinSurveySave(data,'pinned',0)
    if(pinned.operation!=='save') throw new Error('expected save')
    data.businessValues.powerTypes.push('DC');data.execution.task.executionId=4
    expect(pinned.payload.businessValues?.powerTypes).toEqual(['AC'])
    expect(pinned.payload.execution?.task?.executionId).toBe(3)
    expect(selectionClient(pinned.payload.execution)).toBe(client)
  })
  it('the real task Host blocks an object switch while its page has an uncertain request', async () => {
    const input=reactive<any>({registration:{id:1,version:1,status:'PUBLISHED',componentKey:'SOL_SITE_SURVEY',componentVersion:'1',ownerContext:'SOL',entityType:'SITE_SURVEY',viewSource:'PAGE'},resolvedContext:{project:{id:7},businessObjectId:12,taskId:4},allowedActions:['QUERY','UPDATE']})
    const host=ref<any>(),blocked=vi.fn()
    const mounted=mount(defineComponent({setup:()=>()=>h(BusinessViewHost,{...input,ref:host,onSwitchBlocked:blocked})}),{},options);apps.push(mounted.app);await flush()
    const page=host.value.$.setupState.contentRef.$.setupState
    await page.openForm(record());page.form.name='local';page.formRef={validate:vi.fn()}
    api.saveSiteSurveyReceipt.mockRejectedValueOnce(new Error('lost'));await page.save()
    input.resolvedContext={project:{id:7},businessObjectId:13,taskId:4};await flush()
    expect(blocked).toHaveBeenCalledOnce();expect(page.pendingIntent).toBeDefined();expect(page.form.id).toBe(12)
  })
})


vi.mock('@/components/PmsEntitySelect/index.vue', async () => {
  const { defineComponent, h } = await import('vue')
  return { default: defineComponent({ props: ['modelValue','disabled'], emits: ['update:modelValue'], setup: (props, { emit }) => () =>
    h('div', [h('button', { disabled: props.disabled, onClick: () => emit('update:modelValue', 7) }, 'public-project-seven'),
      h('button', { disabled: props.disabled, onClick: () => emit('update:modelValue', 8) }, 'public-project-eight')]) }) }
})
vi.mock('@/components/BusinessEntity/BusinessEntityList.vue', () => ({ default: { render: () => null } }))
vi.mock('@/components/BusinessEntity/BusinessEntityForm.vue', () => ({ default: { render: () => null } }))
vi.mock('@/components/BusinessEntity/DeliveryPanel.vue', () => ({ default: { render: () => null } }))
vi.mock('@/components/BusinessEntity/ApprovalPanel.vue', () => ({ default: { render: () => null } }))
vi.mock('@/components/BusinessEntity/ContentHistoryPanel.vue', () => ({ default: { render: () => null } }))

const renderPublic = async () => {
  vi.mocked(request.get).mockResolvedValue({ownerModule:'SOL',entityType:'siteSurvey',title:'工勘',viewCode:'sol_site_survey',fields:[],operations:[],capabilities:[]})
  const host=ref<any>()
  const mounted=mount(defineComponent({setup:()=>()=>h(PublicEntityHost,{ref:host,ownerModule:'SOL',entityType:'siteSurvey'})}),{},options)
  apps.push(mounted.app);await flush()
  const state=host.value.$.setupState
  expect(await state.selectProfessionalProject(7)).toBe(true);await flush()
  const page=state.professionalRef.$.setupState
  return { state,page,host:host.value }
}
describe('public standalone Host with the actual SiteSurvey professional page',()=>{
  it('keeps an uncertain actual Owner mounted on project clear, project switch and object switch',async()=>{
    const {state,page,host}=await renderPublic()
    await page.openForm(record());page.form.location='原项目未决内容'
    api.saveSiteSurveyReceipt.mockRejectedValueOnce(new Error('response lost'))
    expect(await page.save()).toBe(false)
    const original=state.professionalRef, reads=api.getSiteSurvey.mock.calls.length,projectReads=projects.getProject.mock.calls.length
    expect(await state.selectProfessionalProject(8)).toBe(false)
    expect(await state.selectProfessionalProject(undefined)).toBe(false)
    await page.openForm(record(7,13))
    expect(api.getSiteSurvey).toHaveBeenCalledTimes(reads)
    expect(projects.getProject).toHaveBeenCalledTimes(projectReads)
    expect(state.professionalRef).toBe(original);expect(state.professionalProjectId).toBe(7)
    expect(page.form.id).toBe(12);expect(page.form.location).toBe('原项目未决内容')
    expect(page.pendingIntent).toBeDefined();expect(await host.requestLeave()).toBe(false)
  })
  it('preserves the project and actual object when the user cancels a dirty switch',async()=>{
    const {state,page}=await renderPublic()
    await page.openForm(record());page.form.name='取消切换时保留'
    message.confirm.mockRejectedValueOnce(new Error('user cancelled'))
    const original=state.professionalRef,count=projects.getProject.mock.calls.length
    expect(await state.selectProfessionalProject(8)).toBe(false)
    expect(state.professionalRef).toBe(original);expect(state.professionalProjectId).toBe(7)
    expect(page.form.name).toBe('取消切换时保留');expect(page.formVisible).toBe(true)
    expect(projects.getProject).toHaveBeenCalledTimes(count)
    message.confirm.mockRejectedValueOnce(new Error('user cancelled'))
    await page.openForm(record(7,13));expect(page.form.id).toBe(12);expect(page.form.name).toBe('取消切换时保留')
  })
  it('keeps the old buffer on destination read failure and validates destination identity',async()=>{
    const {state,page}=await renderPublic()
    await page.openForm(record());page.form.name='目的项目加载失败时保留'
    const original=state.professionalRef
    projects.getProject.mockRejectedValueOnce(new Error('destination unavailable'))
    expect(await state.selectProfessionalProject(8)).toBe(false)
    expect(state.professionalRef).toBe(original);expect(page.form.name).toBe('目的项目加载失败时保留')
    expect(page.readonly).toBe(false);expect(state.professionalSwitching).toBe(false)
    projects.getProject.mockResolvedValueOnce({id:9,projectName:'wrong'})
    expect(await state.selectProfessionalProject(8)).toBe(false)
    expect(state.professionalRef).toBe(original);expect(state.professionalProjectId).toBe(7)
    expect(state.operationError).toContain('身份不匹配')
  })
  it('rejects duplicate switches and navigation while awaiting a discard decision',async()=>{
    const {state,page,host}=await renderPublic()
    await page.openForm(record());page.form.name='待确认内容'
    let accept!:()=>void
    message.confirm.mockImplementationOnce(()=>new Promise<void>(resolve=>{accept=resolve}))
    const switching=state.selectProfessionalProject(8);await flush()
    expect(await state.selectProfessionalProject(undefined)).toBe(false)
    expect(await host.requestLeave()).toBe(false)
    expect(state.professionalProjectId).toBe(7);expect(page.form.name).toBe('待确认内容')
    projects.getProject.mockResolvedValueOnce({id:8,projectName:'目的项目'})
    accept();expect(await switching).toBe(true);await flush()
    expect(state.professionalProjectId).toBe(8);expect(state.professionalProject.id).toBe(8)
    expect(state.professionalRef.$.setupState.query.projectId).toBe(8)
  })
  it('routes both leave and same-record update through the actual pending Owner guard',async()=>{
    const {page}=await renderPublic()
    const leave=navigation.leave.mock.calls.at(-1)![0],update=navigation.update.mock.calls.at(-1)![0]
    await page.openForm(record());page.form.location='需要恢复'
    api.saveSiteSurveyReceipt.mockRejectedValueOnce(new Error('response lost'));await page.save()
    expect(await leave()).toBe(false);expect(await update()).toBe(false)
    expect(await page.recoverOperation()).toBe(true)
    expect(await leave()).toBe(true);expect(await update()).toBe(true)
  })
  it('preserves a lossless project identifier and ignores selecting the active project',async()=>{
    const {state}=await renderPublic()
    const id='2099999999999999999'
    projects.getProject.mockResolvedValueOnce({id,projectName:'字符串项目'})
    expect(await state.selectProfessionalProject(id)).toBe(true);await flush()
    expect(projects.getProject).toHaveBeenLastCalledWith(id)
    expect(state.professionalProjectId).toBe(id)
    expect(state.professionalRef.$.setupState.query.projectId).toBe(id)
    const count=projects.getProject.mock.calls.length
    expect(await state.selectProfessionalProject(Number.MAX_SAFE_INTEGER+1)).toBe(false)
    expect(await state.selectProfessionalProject(id)).toBe(false)
    expect(projects.getProject).toHaveBeenCalledTimes(count)
  })
})
