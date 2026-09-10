import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, reactive, ref, type Component } from 'vue'
import { mount, passthrough, tableColumn, textOf } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
import Page from './index.vue'
import Editor from './ReportDraftEditor.vue'
import History from './ReportVersionHistoryDrawer.vue'
import type { AcceptanceActivityVO, AcceptanceReportVersionVO } from '@/api/pms/project/acceptance-report'

const api = vi.hoisted(() => ({ getActivities: vi.fn(), getActivity: vi.fn(), getReportVersions: vi.fn(), createDraft: vi.fn(), updateDraft: vi.fn(), publishVersion: vi.fn(), revokeCurrentVersion: vi.fn(), downloadAttachment: vi.fn() }))
const message = vi.hoisted(() => ({ confirm: vi.fn(), success: vi.fn(), warning: vi.fn() }))
const permissions = vi.hoisted(() => ({ denied: [] as string[] }))
const route = vi.hoisted(() => ({ query: {} as Record<string, unknown> }))
vi.mock('@/api/pms/project/acceptance-report', () => api)
vi.mock('@/api/pms/project/projects', () => ({ __v_isRef: false, getProjectPage: vi.fn() }))
vi.mock('@/api/pms/platform/file', () => ({ createAccessTicket: vi.fn() }))
vi.mock('@/utils/permission', () => ({ checkPermi: (keys: string[]) => keys.every(key => !permissions.denied.includes(key)) }))
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => message }))
vi.mock('vue-router', () => ({ useRoute: () => route, onBeforeRouteLeave: vi.fn(), onBeforeRouteUpdate: vi.fn() }))
vi.mock('@/components/PmsFileArtifact', async () => {
  const { defineComponent, ref } = await import('vue')
  return { PmsFileUploader: defineComponent({ setup(_, { expose }) {
    const uploading = ref(false), selected = ref(false)
    expose({ isBusy: () => uploading.value, hasPendingFile: () => selected.value, uploading, selected })
    return () => null
  } }) }
})

// Only presentation/file transport are stubbed; index -> detail -> editor/history run their real setup logic.
type State = Record<string, any>
const apps: { unmount: () => void }[] = []
const flush = async () => { for (let i = 0; i < 20; i++) { await nextTick(); await Promise.resolve() } }
const components = Object.fromEntries(['ElSkeleton', 'ElRow', 'ElCol', 'ElInput', 'ElSelect', 'ElOption', 'ElDatePicker', 'ElDescriptions', 'ElDescriptionsItem', 'ElTimeline', 'ElTimelineItem', 'PmsEntitySelect'].map(name => [name, passthrough]))
const render = (component: Component = Page, props: State = {}) => {
  const input = reactive(props), instance = ref<State>()
  const mounted = mount(defineComponent({ setup: () => () => h(component, { ...input, ref: instance }) }), {}, { ...components, ElTable: passthrough, ElTableColumn: tableColumn })
  apps.push(mounted.app)
  return { input, exposed: instance.value!, state: instance.value!.$.setupState as State, root: mounted.root }
}
const activity = (projectId = 7, id = 12): AcceptanceActivityVO => ({ id, projectId, projectTaskId: 4, executionContractId: 6, acceptanceType: 'FINAL', activityStatus: 'PENDING', version: 1 })
const draft = (): AcceptanceReportVersionVO => ({ id: 31, acceptanceId: 12, reportVersionNo: 1, reportStatus: 'DRAFT', uploaderUserId: 1, attachments: [], acceptorName: 'saved' })
const deferred = <T,>() => { let resolve!: (value: T) => void; const promise = new Promise<T>(done => { resolve = done }); return { promise, resolve } }
const detailState = (state: State): State => state.detailRef.$.setupState
const editorState = (state: State): State => detailState(state).editorRef.$.setupState
beforeEach(() => {
  vi.clearAllMocks(); permissions.denied = []; route.query = {}
  vi.stubGlobal('window', { addEventListener: vi.fn(), removeEventListener: vi.fn() })
  message.confirm.mockResolvedValue(undefined)
  api.getActivities.mockResolvedValue([activity()])
  api.getActivity.mockResolvedValue(activity())
  api.getReportVersions.mockResolvedValue([draft()])
  api.createDraft.mockResolvedValue({ reportVersionId: 31, reportVersionNo: 1 })
  api.updateDraft.mockResolvedValue({ reportVersionId: 31, reportVersionNo: 2 })
})
afterEach(() => { apps.splice(0).forEach(app => app.unmount()); vi.unstubAllGlobals() })

describe('ACC-03 / PM-03 existing report embedding', () => {
  it('locks project and opens only exact project-owned activity, preserving Long IDs', async () => {
    const id = '2099999999999999999'
    api.getActivity.mockResolvedValue({ ...activity(), id, projectId: '7' })
    api.getReportVersions.mockResolvedValue([{ ...draft(), acceptanceId: id }])
    const { state, root } = render(Page, { projectId: '7', objectId: id, allowedActions: ['QUERY', 'UPDATE'] })
    await flush()
    expect(api.getActivity).toHaveBeenCalledWith(id)
    expect(detailState(state).visible).toBe(true)
    state.query.projectId = 99
    await state.load()
    expect(api.getActivities).toHaveBeenLastCalledWith('7')
    expect(textOf(root)).toContain('报告发布/附件办理尚未接入当前任务上下文')
  })
  it('rejects foreign object before fetching its versions and invalid canonical IDs before API', async () => {
    api.getActivity.mockResolvedValue(activity(8))
    const { state } = render(Page, { projectId: 7, objectId: 12 })
    await flush()
    expect(detailState(state).visible).toBe(false)
    expect(api.getReportVersions).not.toHaveBeenCalled()
    expect(message.warning).toHaveBeenCalledWith('该验收活动不属于当前项目')
    await detailState(state).open('01', 7)
    await detailState(state).open('9223372036854775808', 7)
    expect(api.getActivity).toHaveBeenCalledTimes(1)
  })
  it.each([{ readonly: true }, { allowedActions: ['QUERY'] }, { allowedActions: ['MANAGE'] }, { allowedActions: [] }])('direct handlers fail closed under %j', async permission => {
    const { state } = render(Editor, permission)
    await state.open(activity(), draft())
    state.form.acceptorName = 'local'
    expect(await state.saveDraft()).toBe(false)
    expect(await state.publish()).toBe(false)
    expect(state.canUpload).toBe(false)
    expect(api.updateDraft).not.toHaveBeenCalled()
    expect(api.publishVersion).not.toHaveBeenCalled()
  })
  it('requires Owner action AND original permission and PENDING; UPDATE never implies publish/upload', async () => {
    const { state } = render(Editor, { allowedActions: ['QUERY', 'UPDATE'] })
    await state.open(activity(), draft())
    expect(state.canPublish).toBe(false); expect(state.canUpload).toBe(false)
    permissions.denied = ['pms:acceptance:report:write']
    expect(await state.saveDraft()).toBe(false)
    permissions.denied = []
    state.activity.activityStatus = 'COMPLETED'
    expect(await state.saveDraft()).toBe(false)
    expect(api.updateDraft).not.toHaveBeenCalled()
  })
  it('uses real dirty state, forwards events, preserves form on permission refresh and cancelled close/leave', async () => {
    const dirty = vi.fn(), changed = vi.fn()
    const { state, input, exposed } = render(Page, { projectId: 7, objectId: 12, allowedActions: ['QUERY', 'UPDATE'], onDirtyChange: dirty, onChanged: changed })
    await flush(); await detailState(state).openEditor()
    const editor = editorState(state)
    expect(exposed.isDirty()).toBe(false)
    editor.form.acceptorName = 'local'
    await flush(); expect(exposed.isDirty()).toBe(true); expect(dirty).toHaveBeenLastCalledWith(true)
    input.allowedActions = ['QUERY']; input.projectId = '7'
    await flush(); expect(editor.form.acceptorName).toBe('local'); expect(api.getActivity).toHaveBeenCalledTimes(1)
    message.confirm.mockRejectedValueOnce(new Error('cancel'))
    expect(await exposed.requestLeave()).toBe(false)
    message.confirm.mockRejectedValueOnce(new Error('cancel'))
    const done = vi.fn(); await editor.beforeClose(done)
    expect(done).not.toHaveBeenCalled(); expect(editor.visible).toBe(true)
    expect(await exposed.requestLeave()).toBe(true)
    expect(editor.form.acceptorName).toBe('local'); expect(exposed.isDirty()).toBe(true)
    input.allowedActions = ['QUERY', 'UPDATE']; await flush()
    await editor.saveDraft(); await flush()
    expect(changed).toHaveBeenCalledOnce(); expect(exposed.isDirty()).toBe(false)
    editor.form.acceptorName = 'discard'
    expect(exposed.discardChanges()).toBe(true)
    expect(editor.form.acceptorName).toBeUndefined()
  })
  it('keeps dirty original context when a stale approved project switch returns to it', async () => {
    const { state, input, exposed } = render(Page, { projectId: 7, objectId: 12, allowedActions: ['QUERY', 'UPDATE'] })
    await flush(); await detailState(state).openEditor()
    const editor = editorState(state); editor.form.acceptorName = 'retained'
    const confirmation = deferred<void>(); message.confirm.mockReturnValueOnce(confirmation.promise)
    input.projectId = 8; await flush()
    input.projectId = 7; await flush()
    confirmation.resolve(); await flush()
    expect(editor.form.acceptorName).toBe('retained')
    expect(exposed.isDirty()).toBe(true)
    expect(state.contextBlocked).toBe(false)
    expect(api.getActivity).toHaveBeenCalledTimes(1)
    message.confirm.mockRejectedValueOnce(new Error('cancel'))
    input.objectId = 13; await flush()
    expect(editor.form.acceptorName).toBe('retained')
    expect(state.contextBlocked).toBe(true)
  })
  it('rejects phase two during save and upload; pending file is dirty', async () => {
    const pending = deferred<{ reportVersionId: number; reportVersionNo: number }>()
    api.updateDraft.mockReturnValue(pending.promise)
    const { state, exposed } = render(Editor)
    await state.open(activity(), draft()); await flush()
    state.form.acceptorName = 'local'
    const save = state.saveDraft()
    expect(await exposed.requestLeave()).toBe(false); expect(exposed.discardChanges()).toBe(false)
    pending.resolve({ reportVersionId: 31, reportVersionNo: 2 }); await save
    state.uploaderRef.selected = true
    expect(exposed.isDirty()).toBe(true)
    state.uploaderRef.uploading = true
    expect(await exposed.requestLeave()).toBe(false); expect(exposed.discardChanges()).toBe(false)
  })
  it('isolates delayed list/detail/history responses across project/object switches', async () => {
    const oldList = deferred<AcceptanceActivityVO[]>(), oldDetail = deferred<AcceptanceActivityVO>()
    api.getActivities.mockReturnValueOnce(oldList.promise)
    const { state, input } = render(Page, { projectId: 7 })
    await flush()
    api.getActivity.mockReturnValueOnce(oldDetail.promise)
    const opening = state.openDetail(12); await flush()
    api.getActivities.mockResolvedValue([activity(8, 13)])
    api.getActivity.mockResolvedValue(activity(8, 13)); api.getReportVersions.mockResolvedValue([])
    input.projectId = 8; input.objectId = 13
    await flush()
    oldList.resolve([activity()]); oldDetail.resolve(activity()); await opening; await flush()
    expect(state.activities).toEqual([activity(8, 13)])
    expect(detailState(state).activity.id).toBe(13)
    const history = render(History).state
    const oldHistory = deferred<AcceptanceReportVersionVO[]>()
    api.getReportVersions.mockReturnValueOnce(oldHistory.promise)
    const oldOpen = history.open(12); await history.open(13)
    oldHistory.resolve([draft()]); await oldOpen
    expect(history.versions).toEqual([])
  })
  it('retains standalone route/filter/editor/save/publish and does not create an activity', async () => {
    route.query = { projectId: '7' }
    const { state } = render()
    await flush(); expect(api.getActivities).toHaveBeenCalledWith('7')
    await state.openDetail(12); await detailState(state).openEditor()
    const editor = editorState(state)
    expect(editor.canUpload).toBe(true); expect(editor.canPublish).toBe(true)
    editor.form.acceptorName = 'updated'; await editor.saveDraft(); await flush()
    await editor.publish()
    expect(api.updateDraft).toHaveBeenCalledOnce(); expect(api.publishVersion).toHaveBeenCalledOnce()
    state.query.projectId = 8; api.getActivities.mockResolvedValue([]); await flush()
    expect(api.getActivities).toHaveBeenLastCalledWith(8)
  })
  it('rechecks revoke after confirmation and retains history in readonly mode', async () => {
    api.getReportVersions.mockResolvedValue([{ ...draft(), reportStatus: 'EFFECTIVE' }])
    const { state, input } = render(Page, { projectId: 7, objectId: 12, allowedActions: ['QUERY', 'REVOKE'] })
    await flush()
    const confirmation = deferred<void>(); message.confirm.mockReturnValueOnce(confirmation.promise)
    const revoke = detailState(state).revoke()
    input.readonly = true; await flush(); confirmation.resolve(); await revoke
    expect(api.revokeCurrentVersion).not.toHaveBeenCalled()
    detailState(state).openHistory(); await flush()
    expect(detailState(state).historyRef.$.setupState.visible).toBe(true)
  })
})
