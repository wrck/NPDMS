import { defineComponent, h, nextTick } from 'vue'
import { beforeEach, expect, it, vi } from 'vitest'
import { LocalDacError } from './localDac'
import Dialog from './BusinessCollectionDialog.vue'
import CollectionDialog from './CollectionDialog.vue'
import CollectionWithDeviceLogsDialog from './CollectionWithDeviceLogsDialog.vue'
import { mount, passthrough, textOf } from '@/views/pms/cutover/cutoverTestHarness'

const api = vi.hoisted(() => ({ context: vi.fn(), templates: vi.fn(), page: vi.fn(), usableConnections: vi.fn(), saveConnection: vi.fn(), connections: vi.fn(), submit: vi.fn(), prepareDesktop: vi.fn(), resumeDesktop: vi.fn(), desktopAccess: vi.fn(), findByRequestKey: vi.fn() }))
const local = vi.hoisted(() => ({ request: vi.fn() }))
const messages = vi.hoisted(() => ({ success: vi.fn(), warning: vi.fn(), error: vi.fn(), confirm: vi.fn(async () => {}) }))
vi.mock('./localDac', async (original) => ({ ...await original<object>(), localDacRequest: local.request }))
vi.mock('./LocalDacStatus.vue', () => ({ default: {} }))
vi.mock('@/api/pms/platform/deviceCollection', () => api)
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => messages }))
vi.mock('@/utils/permission', () => ({ checkPermi: () => true }))
vi.mock('@/utils', () => ({ generateUUID: () => 'connection-save-request-123456' }))
vi.mock('@/utils/formatTime', () => ({ dateFormatter: () => '' }))
vi.mock('@/components/ProjectDeviceSelect/index.vue', () => ({ default: {} }))
vi.mock('./LogParsingDialog.vue', () => ({ default: {} }))

const dialog = defineComponent({ props: { modelValue: Boolean }, setup(props, { slots }) { return () => props.modelValue ? h('section', [slots.default?.(), slots.footer?.()]) : null } })
const form = defineComponent({ setup(_, { slots, expose }) { expose({ validate: async () => true, validateField: async () => true }); return () => h('form', slots.default?.()) } })
const controls = Object.fromEntries(['ElAlert', 'ElFormItem', 'ElRadioGroup', 'ElRadio', 'ElSelect', 'ElOption', 'ElRow', 'ElCol', 'ElInput', 'ElInputNumber', 'ElButton', 'ElTable', 'ElTableColumn', 'ElDatePicker', 'ElTag', 'Pagination'].map(key => [key, passthrough]))
const flush = async () => { for (let i = 0; i < 12; i++) await nextTick() }
const render = (location = 'server') => {
  const mounted = mount(Dialog, { entry: 'cutover-checklist', deviceId: '9007199254740993' }, { ...controls, Dialog: dialog, ElForm: form })
  const state = (mounted.vm as any).$.setupState
  state.executionLocation = location
  return { ...mounted, state }
}
beforeEach(() => {
  vi.clearAllMocks()
  vi.stubGlobal('window', Object.assign(new EventTarget(), { location: { origin: 'https://web.example' } }))
  api.context.mockResolvedValue({ entry: 'cutover-checklist', objectId: '30', projectId: '21', deviceId: '9007199254740993', version: 3, manualAllowed: true, canExecute: true, title: '调研采集' })
  api.templates.mockResolvedValue([])
  api.page.mockResolvedValue({ list: [], total: 0 })
  api.usableConnections.mockResolvedValue([])
  api.connections.mockResolvedValue([])
  api.findByRequestKey.mockResolvedValue(null)
  api.desktopAccess.mockResolvedValue({ value: 'task-ticket', tenantId: '1' })
  local.request.mockResolvedValue({ items: [] })
})

const desktopExecution = { id: '41', actorId: '7', dispatchMode: 'DESKTOP', commandText: 'show version',
  task: { platformTaskId: 'task-1', status: 'CREATED', technicalStage: 'PENDING_DISPATCH', credentialMode: 'LOCAL_TEMPORARY' } }
it('sends only metadata to NPDMS and the temporary password only to the local DAC', async () => {
  const m = render('desktop')
  try {
    await m.state.open('30')
    m.state.localReady = true
    await flush()
    Object.assign(m.state.form, { host: 'test.example', username: 'test-user', password: 'local-secret', commands: 'show version' })
    api.prepareDesktop.mockResolvedValue({ execution: desktopExecution, ticket: { value: 'execute-ticket', tenantId: '1' } })
    local.request.mockImplementation(async input => input.path === '/saved-connections' ? { items: [] } : { collectionId: 'collection-1', existing: false })
    await m.state.submit()
    expect(api.prepareDesktop).toHaveBeenCalledWith('cutover-checklist', '30', expect.objectContaining({ host: 'test.example', username: 'test-user' }))
    expect(JSON.stringify(api.prepareDesktop.mock.calls)).not.toContain('local-secret')
    expect(api.submit).not.toHaveBeenCalled()
    expect(local.request).toHaveBeenCalledWith(expect.objectContaining({ path: '/tasks/task-1/executions', body: { password: 'local-secret' } }))
  } finally { m.app.unmount() }
})

it('does not treat a prepared server record as proof of local acceptance after a lost response', async () => {
  const m = render('desktop')
  try {
    await m.state.open('30')
    m.state.localReady = true
    await flush()
    Object.assign(m.state.form, { host: 'test.example', username: 'test-user', password: 'local-secret', commands: 'show version' })
    api.prepareDesktop.mockResolvedValue({ execution: desktopExecution, ticket: { value: 'execute-ticket', tenantId: '1' } })
    api.findByRequestKey.mockResolvedValue(desktopExecution)
    api.page.mockResolvedValue({ list: [desktopExecution], total: 1 })
    local.request.mockRejectedValue(new LocalDacError(true))
    await m.state.submit()
    await flush()
    expect(m.state.submissionUncertain).toBe(true)
    expect(api.prepareDesktop).toHaveBeenCalledTimes(1)
    await m.state.refresh()
    expect(m.state.submissionUncertain).toBe(true)
    expect(api.prepareDesktop).toHaveBeenCalledTimes(1)
    expect(api.submit).not.toHaveBeenCalled()
  } finally { m.app.unmount() }
})
it('queries templates for the exact project and device and exposes authorized manual commands', async () => {
  const m = render()
  try {
    await m.state.open('30')
    await flush()
    expect(api.templates).toHaveBeenCalledWith({ purpose: 'cutover-checklist', publishedOnly: true, projectId: '21', deviceId: '9007199254740993' })
    expect(textOf(m.root)).toContain('手工命令')
    expect(m.state.commandMode).toBe('manual')
    m.state.form.deviceId = undefined
    await flush()
    expect(m.state.templates).toEqual([])
    expect(m.state.form.templateId).toBeUndefined()
  } finally { m.app.unmount() }
})
it('saves the current target with explicit expiry and selects its authorized connection without dispatching', async () => {
  const m = render()
  try {
    await m.state.open('30')
    Object.assign(m.state.form, { host: 'test.example', port: 5555, username: 'test-user', password: 'test-secret' })
    await m.state.openConnectionSave()
    await flush()
    Object.assign(m.state.connectionSaveForm, { name: 'test-connection', expiresAt: '1792504800000' })
    const saved = { id: '9007199254740994', status: 'ACTIVE' }
    api.saveConnection.mockImplementation(async () => { api.usableConnections.mockResolvedValue([saved]); return saved })
    await m.state.saveCurrentConnection()
    expect(api.saveConnection).toHaveBeenCalledWith(expect.objectContaining({ projectId: '21', deviceId: '9007199254740993', host: 'test.example', port: 5555, username: 'test-user', secret: 'test-secret', expiresAt: '1792504800000', templateId: undefined }))
    expect(m.state.credentialMode).toBe('saved')
    expect(m.state.form.credentialId).toBe(saved.id)
    expect(m.state.form.password).toBe('')
    expect(m.state.connectionSaveForm.secret).toBe('')
    expect(m.state.connectionSaveVisible).toBe(false)
    expect(api.submit).not.toHaveBeenCalled()
  } finally { m.app.unmount() }
})
it('keeps an uncertain saved-connection request frozen and clears secrets before retry or close', async () => {
  const m = render()
  try {
    await m.state.open('30')
    Object.assign(m.state.form, { host: 'test.example', username: 'test-user', password: 'test-secret' })
    await m.state.openConnectionSave()
    await flush()
    Object.assign(m.state.connectionSaveForm, { name: 'test-connection', expiresAt: '1792504800000' })
    const originalKey = m.state.connectionSaveForm.requestKey
    api.saveConnection.mockRejectedValue(new Error('response lost'))
    api.connections.mockResolvedValue([{ registrationKey: originalKey, status: 'PENDING' }])
    await expect(m.state.saveCurrentConnection()).resolves.toBeUndefined()
    expect(m.state.connectionSaveFrozen).toBe(true)
    expect(m.state.connectionSaveForm.secret).toBe('')
    m.state.form.host = 'different.example'
    await m.state.openConnectionSave()
    expect(m.state.connectionSaveForm.requestKey).toBe(originalKey)
    expect(m.state.connectionSaveForm.host).toBe('test.example')
    m.state.connectionSaveForm.secret = 'retry-secret'
    m.state.visible = false
    await flush()
    expect(m.state.connectionSaveForm.secret).toBe('')
    expect(m.state.form.password).toBe('')
  } finally { m.app.unmount() }
})

it.each([CollectionDialog, CollectionWithDeviceLogsDialog])('legacy entry delegates opening and readonly restrictions to the shared dialog', async (component) => {
  const m = mount(component, { entry: 'configuration', readonly: true }, { ...controls, Dialog: dialog, ElForm: form })
  try {
    await (m.vm as any).open('30')
    await flush()
    expect(api.context).toHaveBeenCalledWith('configuration', '30')
    expect(textOf(m.root)).toContain('执行历史')
    expect(textOf(m.root)).not.toContain('确认下发命令')
    expect(textOf(m.root)).not.toContain('保存为连接')
    expect(api.submit).not.toHaveBeenCalled()
  } finally { m.app.unmount() }
})

it('explains authorization failure while preserving the original execution and request', async () => {
  const m=render('desktop')
  try {
    await m.state.open('30');m.state.localReady=true;await flush()
    Object.assign(m.state.form,{host:'test.example',username:'test-user',password:'local-secret',commands:'show version'})
    api.prepareDesktop.mockResolvedValue({execution:desktopExecution,ticket:{value:'execute-ticket',tenantId:'1'}})
    local.request.mockRejectedValue(new LocalDacError(true,503))
    await m.state.submit();await flush()
    expect(messages.warning).toHaveBeenCalledWith(expect.stringContaining('服务端授权校验'))
    expect(m.state.pendingDesktopExecution.id).toBe('41')
    expect(m.state.submissionUncertain).toBe(true)
    api.resumeDesktop.mockResolvedValue({execution:desktopExecution,ticket:{value:'refreshed-ticket',tenantId:'1'}})
    await m.state.continueDesktop()
    expect(api.resumeDesktop).toHaveBeenCalledExactlyOnceWith('cutover-checklist','30','41')
    expect(api.prepareDesktop).toHaveBeenCalledTimes(1)
    expect(messages.warning).toHaveBeenLastCalledWith(expect.stringContaining('服务端授权校验'))
    expect(messages.warning).toHaveBeenLastCalledWith(expect.stringContaining('不会创建新任务'))
  } finally {m.app.unmount()}
})
it('continues the original local task with the same id and accepts the idempotent existing response', async () => {
  const m=render('desktop')
  try {
    await m.state.open('30');m.state.localReady=true;await flush()
    Object.assign(m.state.form,{password:'local-secret'})
    api.resumeDesktop.mockResolvedValue({execution:desktopExecution,ticket:{value:'refreshed-ticket',tenantId:'1'}})
    local.request.mockResolvedValue({collectionId:'original-collection',existing:true})
    await m.state.continueDesktop(desktopExecution)
    expect(api.resumeDesktop).toHaveBeenCalledExactlyOnceWith('cutover-checklist','30','41')
    expect(local.request).toHaveBeenCalledWith(expect.objectContaining({path:'/tasks/task-1/executions',ticket:{value:'refreshed-ticket',tenantId:'1'}}))
    expect(api.prepareDesktop).not.toHaveBeenCalled()
    expect(api.submit).not.toHaveBeenCalled()
    expect(m.state.submissionUncertain).toBe(false)
  } finally {m.app.unmount()}
})
