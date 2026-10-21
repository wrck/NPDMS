vi.mock('@/store/modules/user', () => ({ useUserStore: () => ({ getUser: { id: 1, nickname: '管理员' } }) }))
vi.mock('@/components/BusinessView/operationHost', async () => { const { shallowRef } = await import('vue'); return { useOperationClient: () => shallowRef(), useOperationCheck: () => async () => true } })
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, provide, reactive, ref, type Component } from 'vue'
import { findByTestId, mount, passthrough, tableColumn, textOf, type TestNode } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
import Page from './index.vue'
import Editor from './ReportDraftEditor.vue'
import History from './ReportVersionHistoryDrawer.vue'
import { acceptanceUploadIntentKey, type AcceptanceUploadIntent } from './acceptanceUploadIntent'
import type { AcceptanceActivityVO, AcceptanceReportVersionVO } from '@/api/pms/acceptance/acceptance-report'

const api = vi.hoisted(() => ({ getActivities: vi.fn(), getActivity: vi.fn(), getReportVersions: vi.fn(), getIndependentContext: vi.fn(), createIndependent: vi.fn(), createDraft: vi.fn(), updateDraft: vi.fn(), publishVersion: vi.fn(), revokeCurrentVersion: vi.fn(), downloadAttachment: vi.fn() }))
const message = vi.hoisted(() => ({ confirm: vi.fn(), success: vi.fn(), warning: vi.fn() }))
const permissions = vi.hoisted(() => ({ denied: [] as string[] }))
const route = vi.hoisted(() => ({ query: {} as Record<string, unknown> }))
vi.mock('@/api/pms/acceptance/acceptance-report', () => api)
vi.mock('@/api/pms/project/projects', () => ({ __v_isRef: false, getProjectPage: vi.fn() }))
vi.mock('@/api/pms/platform/file', () => ({ createAccessTicket: vi.fn() }))
vi.mock('@/utils/permission', () => ({ checkPermi: (keys: string[]) => keys.every(key => !permissions.denied.includes(key)) }))
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => message }))
vi.mock('@/components/PmsFileArtifact/PmsFileVersionReferenceList.vue', () => ({ default: { setup: () => () => null } }))
vi.mock('vue-router', () => ({ useRoute: () => route, onBeforeRouteLeave: vi.fn(), onBeforeRouteUpdate: vi.fn() }))
vi.mock('@/components/PmsFileArtifact', async () => {
  const { defineComponent, ref } = await import('vue')
  return { PmsFileUploader: defineComponent({ setup(_, { expose, emit }) {
    const uploading = ref(false), selected = ref(false)
    const submit = vi.fn(async () => { selected.value = false; emit('completed', {}) })
    expose({ submit, isBusy: () => uploading.value, hasPendingFile: () => selected.value, uploading, selected })
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
  it.each(['PRELIMINARY', 'FINAL'] as const)('uses the same fields for %s upload and readonly query without preparing a draft while viewing', async acceptanceType => {
    const target = { ...activity(), acceptanceType, originKind: 'DIRECT' as const, activityStatus: 'COMPLETED', currentReportVersionId: 31 }
    const report = { ...draft(), reportStatus: 'EFFECTIVE', acceptorName: '原验收人', acceptanceTime: '2026-10-21T22:15:42',
      attachments: [{ sequence: 1, artifactId: 99, versionNo: 1, referenceKey: 'frozen-file' }] }
    api.getActivities.mockResolvedValue([target])
    api.getActivity.mockResolvedValue(target)
    api.getReportVersions.mockResolvedValue([report])
    const view = render(Page, { projectId: 7, objectId: 12, acceptanceType, readonly: true, allowedActions: ['QUERY'] })
    await flush(); await view.state.openDetail(12); await flush()
    const queryForm = findByTestId(view.root, 'acceptance-report-form')
    expect(queryForm).toBeDefined()
    expect(textOf(queryForm!)).toContain('原验收人')
    expect(textOf(queryForm!)).toContain('2026-10-21 22:15:42')
    expect(api.createDraft).not.toHaveBeenCalled()
    expect(editorState(view.state).visible).toBe(false)

    const editing = render(Editor, { allowedActions: ['QUERY', 'UPDATE', 'PUBLISH', 'FILE_WRITE'] })
    await editing.state.open({ ...target, activityStatus: 'PENDING', currentReportVersionId: undefined }, draft()); await flush()
    const uploadForm = findByTestId(editing.root, 'acceptance-report-form')
    expect(uploadForm).toBeDefined()
    const fieldLabels = (node: TestNode): string[] => [
      ...(typeof node.props?.label === 'string' ? [node.props.label] : []), ...node.children.flatMap(fieldLabels)
    ]
    expect(fieldLabels(queryForm!)).toEqual(['验收人', '完成时间', '上传交付件'])
    expect(fieldLabels(uploadForm!)).toEqual(fieldLabels(queryForm!))
    expect(textOf(uploadForm!)).toContain('管理员')
    expect(textOf(uploadForm!)).toContain('保存后自动记录')
    expect(api.publishVersion).not.toHaveBeenCalled()
  })
  it('lets an authorized empty final task prepare its own native activity without opening or replacing the initial report', async () => {
    api.getActivities.mockResolvedValue([{ ...activity(), acceptanceType: 'PRELIMINARY' }])
    api.getIndependentContext.mockResolvedValue({ projectId: 7, projectVersion: 5, treeVersion: 2, lifecycleStatus: 'ACTIVE' })
    api.createIndependent.mockResolvedValue({ acceptanceId: 13, projectId: 7, acceptanceType: 'FINAL', created: true })
    const changed = vi.fn(() => { expect(state.creating).toBe(false) })
    const { state, root } = render(Page, { projectId: 7, acceptanceType: 'FINAL', allowedActions: ['QUERY', 'MANAGE'], onChanged: changed })
    await flush()
    expect(textOf(root)).toContain('上传终验交付件')
    await state.createActivity('PRELIMINARY')
    expect(api.createIndependent).not.toHaveBeenCalled()
    api.getActivities.mockResolvedValue([activity(7, 13)])
    await state.createActivity('FINAL')
    expect(api.createIndependent).toHaveBeenCalledWith({ projectId: 7, acceptanceType: 'FINAL', expectedProjectVersion: 5, expectedTreeVersion: 2 }, expect.any(String))
    expect(state.activities.map((row: any) => row.id)).toEqual([13])
    expect(detailState(state).visible).toBe(false)
    expect(api.createDraft).not.toHaveBeenCalled()
    expect(api.publishVersion).not.toHaveBeenCalled()
    expect(changed).toHaveBeenCalledOnce()
  })
  it('continues an explicit upload click after the Host selects the new authorized report, without opening on ordinary entry', async () => {
    const intent = ref()
    const target = reactive<State>({ projectId: 7, acceptanceType: 'FINAL', allowedActions: ['QUERY', 'MANAGE'] })
    const instance = ref<State>()
    api.getActivities.mockResolvedValue([])
    api.getIndependentContext.mockResolvedValue({ projectId: 7, projectVersion: 5, treeVersion: 2, lifecycleStatus: 'ACTIVE' })
    api.createIndependent.mockResolvedValue({ acceptanceId: 13, projectId: 7, acceptanceType: 'FINAL', created: true })
    const mounted = mount(defineComponent({ setup() {
      provide(acceptanceUploadIntentKey, intent)
      return () => h(Page, { ...target, key: target.objectId ?? 'empty', ref: instance })
    } }), {}, { ...components, ElTable: passthrough, ElTableColumn: tableColumn })
    apps.push(mounted.app)
    await flush()
    expect(api.createDraft).not.toHaveBeenCalled()
    api.getActivities.mockResolvedValue([activity(7, 13)])
    await instance.value!.$.setupState.createActivity('FINAL')
    expect(api.createDraft).not.toHaveBeenCalled()
    api.getActivity.mockResolvedValue(activity(7, 13))
    api.getReportVersions.mockResolvedValue([])
    target.objectId = 13
    await flush()
    expect(api.createDraft).not.toHaveBeenCalled()
    target.allowedActions = ['QUERY', 'UPDATE', 'PUBLISH', 'FILE_WRITE']
    await flush()
    const state = instance.value!.$.setupState
    expect(editorState(state).visible).toBe(true)
    expect(editorState(state).activity.id).toBe(13)
    expect(intent.value).toBeUndefined()
    expect(api.createDraft).toHaveBeenCalledOnce()
    await editorState(state).close()
    await flush()
    expect(editorState(state).visible).toBe(false)
    expect(api.publishVersion).not.toHaveBeenCalled()
  })
  it('filters displayed report status without hiding existing reports from creation guards', async () => {
    api.getActivities.mockResolvedValue([activity(), { ...activity(7, 13), activityStatus: 'COMPLETED' }])
    const { state } = render(Page, { projectId: 7, acceptanceType: 'FINAL', allowedActions: ['QUERY', 'MANAGE'] })
    await flush()
    state.query.status = 'COMPLETED'
    await flush()
    expect(state.visibleActivities.map((row: any) => row.id)).toEqual([13])
    state.query.status = 'PENDING'
    await flush()
    expect(state.visibleActivities.map((row: any) => row.id)).toEqual([12])
    expect(state.canCreateActivity('FINAL')).toBe(false)
  })
  it.each<Partial<AcceptanceUploadIntent> & { readonly?: boolean }>([
    { projectId: 8 }, { acceptanceType: 'PRELIMINARY' }, { acceptanceId: 13 }, { readonly: true }
  ])('does not resume a manual upload for a mismatched or readonly context %j', async mismatch => {
    const { readonly, ...differentIntent } = mismatch
    const intent = ref<AcceptanceUploadIntent>({ projectId: 7, acceptanceId: 12, acceptanceType: 'FINAL', ...differentIntent })
    const mounted = mount(defineComponent({ setup() {
      provide(acceptanceUploadIntentKey, intent)
      return () => h(Page, { projectId: 7, objectId: 12, acceptanceType: 'FINAL', readonly,
        allowedActions: ['QUERY', 'UPDATE', 'PUBLISH', 'FILE_WRITE'] })
    } }), {}, { ...components, ElTable: passthrough, ElTableColumn: tableColumn })
    apps.push(mounted.app)
    await flush()
    expect(api.createDraft).not.toHaveBeenCalled()
    expect(api.getActivity).not.toHaveBeenCalled()
  })
  it.each([{ readonly: true, allowedActions: ['QUERY', 'MANAGE'] }, { allowedActions: ['QUERY'] }])('does not expose native activity creation for a blocked task %j', async permission => {
    api.getActivities.mockResolvedValue([])
    const { state } = render(Page, { projectId: 7, acceptanceType: 'FINAL', ...permission })
    await flush()
    await state.createActivity('FINAL')
    expect(api.getIndependentContext).not.toHaveBeenCalled()
    expect(api.createIndependent).not.toHaveBeenCalled()
  })
  it.each([undefined, 12])('keeps final and preliminary task lists separate even when the final task has no own bound report (object=%s)', async objectId => {
    const preliminary = { ...activity(), acceptanceType: 'PRELIMINARY' as const }
    const final = activity(7, 13)
    api.getActivities.mockResolvedValue([preliminary, final, activity(8, 14)])
    const { state, input } = render(Page, { projectId: 7, objectId, acceptanceType: 'FINAL', readonly: true, allowedActions: ['QUERY'] })
    await flush()
    expect(state.activities).toEqual([final])
    input.acceptanceType = 'PRELIMINARY'
    await flush()
    expect(state.activities).toEqual([preliminary])
    input.acceptanceType = 'FINAL'
    api.getActivities.mockResolvedValue([preliminary])
    await flush()
    expect(state.activities).toEqual([])
  })
  it('loads completed reports when host query authority returns and clears them immediately when it is withdrawn', async () => {
    const completed = { ...activity(), activityStatus: 'COMPLETED', currentReportVersionId: 31 }
    api.getActivities.mockResolvedValue([completed])
    api.getActivity.mockResolvedValue(completed)
    api.getReportVersions.mockResolvedValue([{ ...draft(), reportStatus: 'EFFECTIVE' }])
    const { state, input } = render(Page, { projectId: 7, objectId: 12, readonly: true, allowedActions: [] })
    await flush()
    expect(api.getActivities).not.toHaveBeenCalled()
    input.allowedActions = ['QUERY']
    await flush()
    expect(state.activities).toEqual([completed])
    await state.openDetail(12)
    expect(detailState(state).current.reportStatus).toBe('EFFECTIVE')
    expect(detailState(state).canWrite('UPDATE')).toBe(false)
    expect(detailState(state).canWrite('PUBLISH')).toBe(false)
    input.allowedActions = []
    await flush()
    expect(state.activities).toEqual([])
    expect(api.createDraft).not.toHaveBeenCalled()
    expect(api.updateDraft).not.toHaveBeenCalled()
    expect(api.publishVersion).not.toHaveBeenCalled()
  })
  it.each(['PRELIMINARY', 'FINAL'] as const)('shows the scoped %s activity list without opening a drawer or preparing a draft on entry', async acceptanceType => {
    const rows = [activity(7, 12), { ...activity(7, 13), acceptanceType: 'PRELIMINARY' as const },
      activity(7, 14), { ...activity(7, 15), acceptanceType: 'PRELIMINARY' as const }, activity(8, 16)]
    const selected = rows.find(row => row.acceptanceType === acceptanceType)!
    api.getActivities.mockResolvedValue(rows)
    const { state } = render(Page, { projectId: 7, objectId: selected.id, allowedActions: ['QUERY', 'UPDATE'] })
    await flush()
    expect(state.activities).toEqual(rows.filter(row => row.projectId === 7 && row.acceptanceType === acceptanceType))
    expect(detailState(state).visible).toBe(false)
    expect(editorState(state).visible).toBe(false)
    expect(api.getActivity).not.toHaveBeenCalled()
    expect(api.createDraft).not.toHaveBeenCalled()
    api.getActivity.mockResolvedValue(selected)
    api.getReportVersions.mockResolvedValue([{ ...draft(), acceptanceId: selected.id }])
    await state.openDetail(selected.id, true)
    expect(editorState(state).visible).toBe(true)
  })

  it('does not open a different activity while bound to an exact task object', async () => {
    const { state } = render(Page, { projectId: 7, objectId: 12, allowedActions: ['QUERY', 'UPDATE'] })
    await flush()
    await state.openDetail(13, true)
    expect(api.getActivity).not.toHaveBeenCalled()
    expect(api.createDraft).not.toHaveBeenCalled()
  })
  it('shows every report of the bound acceptance type in the current project while keeping other rows query-only', async () => {
    const sameType = activity(7, 13)
    api.getActivities.mockResolvedValue([activity(), sameType, { ...activity(7, 14), acceptanceType: 'PRELIMINARY' }, activity(8, 15)])
    api.getActivity.mockResolvedValue(sameType)
    api.getReportVersions.mockResolvedValue([{ ...draft(), acceptanceId: 13 }])
    const { state } = render(Page, { projectId: 7, objectId: 12, allowedActions: ['QUERY', 'UPDATE', 'PUBLISH', 'FILE_WRITE'] })
    await flush()
    expect(state.activities.map((row: any) => row.id)).toEqual([12, 13])
    await state.openDetail(13)
    expect(detailState(state).activity.id).toBe(13)
    expect(detailState(state).canWrite('UPDATE')).toBe(false)
    expect(detailState(state).canWrite('PUBLISH')).toBe(false)
    api.getActivity.mockClear()
    await state.openDetail(14)
    await state.openDetail(15)
    await state.openDetail(13, true)
    expect(api.getActivity).not.toHaveBeenCalled()
    expect(api.createDraft).not.toHaveBeenCalled()
  })

  it('prepares the upload object automatically and completes a direct acceptance in one save action', async () => {
    const { state, root } = render(Editor)
    await state.open({ ...activity(), originKind: 'DIRECT', projectTaskId: null, executionContractId: null })
    expect(api.createDraft).toHaveBeenCalledOnce()
    expect(textOf(root)).not.toContain('验收结论')
    expect(await state.saveAndComplete()).toBe(false)
    expect(api.publishVersion).not.toHaveBeenCalled()
    state.attachmentCompleted()
    expect(await state.saveAndComplete()).toBe(true)
    expect(api.updateDraft).toHaveBeenCalledOnce()
    expect(api.publishVersion).toHaveBeenCalledOnce()
    expect(api.updateDraft.mock.invocationCallOrder[0]).toBeLessThan(api.publishVersion.mock.invocationCallOrder[0])
    expect(message.success).toHaveBeenLastCalledWith('终验已通过')
  })
  it.each(['PRELIMINARY', 'FINAL'] as const)('uploads the selected %s deliverable before recording a passing report in the task entry', async acceptanceType => {
    const { state, root } = render(Editor, { allowedActions: ['QUERY', 'UPDATE', 'FILE_WRITE', 'PUBLISH'] })
    await state.open({ ...activity(), acceptanceType })
    expect(api.createDraft).toHaveBeenCalledOnce()
    expect(textOf(root)).not.toContain('验收结论')
    state.uploaderRef.selected = true
    expect(await state.saveAndComplete()).toBe(true)
    expect(state.uploaderRef.submit).toHaveBeenCalledOnce()
    expect(api.publishVersion).toHaveBeenCalledOnce()
    expect(state.uploaderRef.submit.mock.invocationCallOrder[0]).toBeLessThan(api.updateDraft.mock.invocationCallOrder[0])
  })
  it('retains selected evidence and never saves or publishes after upload failure', async () => {
    const { state } = render(Editor)
    await state.open(activity(), draft())
    state.uploaderRef.selected = true
    state.uploaderRef.submit.mockRejectedValueOnce(new Error('upload failed'))
    await expect(state.saveAndComplete()).rejects.toThrow('upload failed')
    expect(state.uploaderRef.hasPendingFile()).toBe(true)
    expect(api.updateDraft).not.toHaveBeenCalled()
    expect(api.publishVersion).not.toHaveBeenCalled()
  })
  it('finishes upload, save and publication before notifying the host to refresh permissions', async () => {
    const view = render(Editor, { readonly: false, onChanged: () => { view.input.readonly = true } })
    await view.state.open(activity(), draft())
    view.state.uploaderRef.selected = true
    expect(await view.state.saveAndComplete()).toBe(true)
    expect(api.updateDraft).toHaveBeenCalledOnce()
    expect(api.publishVersion).toHaveBeenCalledOnce()
  })
  it('reopens an uploaded draft without reupload and retains its files if publication fails', async () => {
    const { state } = render(Editor)
    await state.open(activity(), { ...draft(), attachments: [{ sequence: 1, artifactId: 11, versionNo: 1, referenceKey: 'saved-file' } as any] })
    api.publishVersion.mockRejectedValueOnce(new Error('version conflict'))
    await expect(state.saveAndComplete()).rejects.toThrow('version conflict')
    expect(state.visible).toBe(true)
    expect(state.uploadedCount).toBe(1)
    expect(state.draftId).toBe(31)
    expect(state.uploaderRef.submit).not.toHaveBeenCalled()
  })
  it('still stops the save sequence when real write authority is withdrawn during upload', async () => {
    const { state, input } = render(Editor, { readonly: false })
    await state.open(activity(), draft())
    state.uploaderRef.selected = true
    state.uploaderRef.submit.mockImplementationOnce(async () => {
      state.uploaderRef.selected = false
      state.attachmentCompleted()
      input.readonly = true
      await flush()
    })
    expect(await state.saveAndComplete()).toBe(false)
    expect(state.visible).toBe(true)
    expect(state.uploadedCount).toBe(1)
    expect(api.updateDraft).not.toHaveBeenCalled()
    expect(api.publishVersion).not.toHaveBeenCalled()
    expect(message.warning).toHaveBeenCalledWith('交付件已保留为草稿，请刷新后检查操作权限和执行条件再保存')
  })
  it('does not complete uploaded direct reports without publish authority', async () => {
    const { state } = render(Editor, { allowedActions: ['QUERY', 'UPDATE', 'FILE_WRITE'] })
    await state.open({ ...activity(), originKind: 'DIRECT', projectTaskId: null, executionContractId: null }, draft())
    state.attachmentCompleted()
    expect(await state.saveAndComplete()).toBe(false)
    expect(api.updateDraft).not.toHaveBeenCalled()
    expect(api.publishVersion).not.toHaveBeenCalled()
  })
  it('locks the project and opens the exact Long activity ID only on explicit request', async () => {
    const id = '2099999999999999999'
    api.getActivity.mockResolvedValue({ ...activity(), id, projectId: '7' })
    api.getActivities.mockResolvedValue([{ ...activity(), id, projectId: '7' }])
    api.getReportVersions.mockResolvedValue([{ ...draft(), acceptanceId: id }])
    const { state, root } = render(Page, { projectId: '7', objectId: id, allowedActions: ['QUERY', 'UPDATE'] })
    await flush()
    expect(api.getActivity).not.toHaveBeenCalled()
    expect(detailState(state).visible).toBe(false)
    await state.openDetail(id)
    expect(api.getActivity).toHaveBeenCalledWith(id)
    expect(detailState(state).visible).toBe(true)
    state.query.projectId = 99
    await state.load()
    expect(api.getActivities).toHaveBeenLastCalledWith('7')
    expect(textOf(root)).not.toContain('报告发布/附件办理尚未接入当前任务上下文')
  })
  it('rejects foreign object before fetching its versions and invalid canonical IDs before API', async () => {
    api.getActivity.mockResolvedValue(activity(8))
    const { state } = render(Page, { projectId: 7, objectId: 12 })
    await flush()
    await state.openDetail(12)
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
  it('enables embedded upload only when the Owner grants FILE_WRITE and the PLT upload permission holds', async () => {
    permissions.denied = ['pms:file:upload']
    const deniedView = render(Editor, { allowedActions: ['QUERY', 'UPDATE', 'FILE_WRITE'] })
    await deniedView.state.open(activity(), draft())
    expect(deniedView.state.canUpload).toBe(false)
    permissions.denied = []
    const { state } = render(Editor, { allowedActions: ['QUERY', 'UPDATE', 'FILE_WRITE'] })
    await state.open(activity(), draft())
    expect(state.canUpload).toBe(true)
  })
  it('uses real dirty state, forwards events, preserves form on permission refresh and cancelled close/leave', async () => {
    const dirty = vi.fn(), changed = vi.fn()
    const { state, input, exposed } = render(Page, { projectId: 7, objectId: 12, allowedActions: ['QUERY', 'UPDATE'], onDirtyChange: dirty, onChanged: changed })
    await flush(); await state.openDetail(12); await detailState(state).openEditor()
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
    await flush(); await state.openDetail(12); await detailState(state).openEditor()
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
    expect(detailState(state).visible).toBe(false)
    expect(detailState(state).activity).toBeUndefined()
    await state.openDetail(13)
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
    expect(detailState(state).visible).toBe(false)
    expect(editor.visible).toBe(true)
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
    await state.openDetail(12)
    const confirmation = deferred<void>(); message.confirm.mockReturnValueOnce(confirmation.promise)
    const revoke = detailState(state).revoke()
    input.readonly = true; await flush(); confirmation.resolve(); await revoke
    expect(api.revokeCurrentVersion).not.toHaveBeenCalled()
    detailState(state).openHistory(); await flush()
    expect(detailState(state).historyRef.$.setupState.visible).toBe(true)
  })
})
