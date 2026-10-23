import { defineComponent, h, nextTick } from 'vue'
import { beforeEach, expect, it, vi } from 'vitest'
import { LocalDacError } from './localDac'
import Dialog from './BusinessCollectionDialog.vue'
import CollectionDialog from './CollectionDialog.vue'
import CollectionWithDeviceLogsDialog from './CollectionWithDeviceLogsDialog.vue'
import { mount, passthrough, textOf } from '@/views/pms/cutover/cutoverTestHarness'

const api = vi.hoisted(() => ({ context: vi.fn(), templates: vi.fn(), page: vi.fn(), usableConnections: vi.fn(), saveConnection: vi.fn(), connections: vi.fn(), submit: vi.fn(), prepareDesktop: vi.fn(), resumeDesktop: vi.fn(), desktopAccess: vi.fn(), findByRequestKey: vi.fn() }))
const local = vi.hoisted(() => ({ request: vi.fn() }))
const messages = vi.hoisted(() => ({ success: vi.fn(), warning: vi.fn(), error: vi.fn(), info: vi.fn(), confirm: vi.fn(async () => {}) }))
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
const render = (location = 'server', options: Record<string, unknown> = {}) => {
  const mounted = mount(Dialog, { entry: 'cutover-checklist', deviceId: '9007199254740993', ...options }, { ...controls, Dialog: dialog, ElForm: form })
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
  local.request.mockResolvedValue({ items: [], scope: { projectId: '21', deviceId: '9007199254740993', protocol: 'SSH2' } })
})

const desktopExecution = { id: '41', actorId: '7', dispatchMode: 'DESKTOP', commandText: 'show version',
  task: { platformTaskId: 'task-1', status: 'CREATED', technicalStage: 'PENDING_DISPATCH', credentialMode: 'LOCAL_TEMPORARY' } }

const localConnection = (id = 'local-connection-1') => ({ id, displayName: '设备连接', version: 0,
  connection: { protocol: 'SSH2', authenticationType: 'PASSWORD', executionMode: 'SHELL', host: '192.0.2.10', port: 2222, username: 'operator' } })

it.each(['cutover-checklist','cutover-pre-check','cutover-execution','cutover-test','cutover-rollback','cutover-post-collection'])(
  '%s keeps the original execution device when retrying a multi-device source', async entry => {
    const m=render('desktop',{entry})
    api.context.mockResolvedValue({entry,projectId:'21',deviceId:undefined,version:0,manualAllowed:true,canExecute:true})
    try {
      await m.state.open('30')
      m.state.form.password='previous-target-secret'
      await m.state.retry({id:'41',commandText:'show version',task:{deviceId:'9',host:'192.0.2.10',port:22,protocol:'SSH'}})
      expect(m.state.form.deviceId).toBe('9')
      expect(m.state.form.retryOfId).toBe('41')
      expect(m.state.form.password).toBe('')
      expect(api.prepareDesktop).not.toHaveBeenCalled()
    } finally { m.app.unmount() }
  })

it.each(['center','configuration','joint-test','cutover-checklist','cutover-pre-check','cutover-execution','cutover-test','cutover-rollback','cutover-post-collection'])(
  '%s supplies a complete scope from the selected or source-bound device', async entry => {
    const fixed=['configuration','joint-test'].includes(entry)
    const m=render('desktop',{entry,deviceId:fixed?undefined:'9007199254740993'})
    api.context.mockResolvedValue({entry,objectId:'30',projectId:'21',deviceId:fixed?'9007199254740993':undefined,version:0,canExecute:true,manualAllowed:true})
    try {
      await m.state.open('30');m.state.localReady=true;await flush()
      expect(api.desktopAccess).toHaveBeenCalledWith(entry,'30',{operation:'CONNECTIONS_READ',deviceId:'9007199254740993',protocol:'SSH'})
      expect(m.state.source.version).toBe(0)
      expect(m.state.form.deviceId).toBe('9007199254740993')
    } finally { m.app.unmount() }
  })

it('normalizes an empty caller device and uses a fixed source device instead of sending an empty id', async () => {
  const m=render('desktop',{entry:'configuration',deviceId:''})
  try {
    await m.state.open('30');m.state.localReady=true;await flush()
    expect(m.state.form.deviceId).toBe('9007199254740993')
    expect(api.desktopAccess).toHaveBeenCalledWith('configuration','30',expect.objectContaining({deviceId:'9007199254740993'}))
  } finally { m.app.unmount() }
})

it('allows project-only connection lookup but blocks command dispatch until a device is selected', async () => {
  const m=render('desktop',{entry:'center',deviceId:undefined})
  api.context.mockResolvedValue({entry:'center',projectId:'21',version:0,manualAllowed:true,canExecute:true})
  local.request.mockResolvedValue({items:[],scope:{projectId:'21',deviceId:null,protocol:'SSH2'}})
  try {
    await m.state.open('21');m.state.localReady=true;await flush()
    expect(api.desktopAccess).toHaveBeenCalledWith('center','21',{operation:'CONNECTIONS_READ',deviceId:undefined,protocol:'SSH'})
    await m.state.submit()
    expect(api.prepareDesktop).not.toHaveBeenCalled()
    expect(messages.warning).toHaveBeenCalledWith(expect.stringContaining('请先选择设备'))
  } finally { m.app.unmount() }
})

it('rejects missing source projects and invalid object ids before sending dependent requests', async () => {
  const m=render('desktop')
  try {
    await m.state.open(undefined)
    expect(api.context).not.toHaveBeenCalled()
    api.context.mockResolvedValue({projectId:null,version:0})
    await m.state.open('30')
    expect(api.desktopAccess).not.toHaveBeenCalled()
    expect(api.page).not.toHaveBeenCalled()
    expect(m.state.visible).toBe(false)
    expect(messages.warning).toHaveBeenCalledWith(expect.stringContaining('缺少项目'))
  } finally { m.app.unmount() }
})

it('handles source and choice loading failures locally instead of rejecting the entry click', async () => {
  const m=render('desktop')
  try {
    api.context.mockRejectedValueOnce(new Error('offline'))
    await expect(m.state.open('30')).resolves.toBeUndefined()
    api.page.mockRejectedValueOnce(new Error('offline'))
    api.templates.mockRejectedValueOnce(new Error('offline'))
    await expect(m.state.open('30')).resolves.toBeUndefined()
    expect(messages.warning).toHaveBeenCalledWith(expect.stringContaining('加载失败'))
  } finally { m.app.unmount() }
})

it('saves against the frozen device and protocol and does not use a later target selection', async () => {
  const m=render('desktop')
  try {
    await m.state.open('30');m.state.localReady=true;await flush()
    Object.assign(m.state.form,{host:'192.0.2.10',port:22,username:'fixture',password:'fixture-only-secret'})
    await m.state.openConnectionSave()
    m.state.connectionSaveForm.name='scope fixture'
    m.state.form.deviceId='10'
    local.request.mockResolvedValue({saved:true,connection:{id:'scope-fixture'}})
    await m.state.saveCurrentConnection()
    expect(api.desktopAccess).toHaveBeenCalledWith('cutover-checklist','30',{
      operation:'CONNECTION_SAVE',deviceId:'9007199254740993',protocol:'SSH'
    })
    expect(api.saveConnection).not.toHaveBeenCalled()
    expect(api.prepareDesktop).not.toHaveBeenCalled()
  } finally { m.app.unmount() }
})

it('does not call prepare or resume when there is no original desktop task', async () => {
  const m=render('desktop')
  try {
    await m.state.open('30');m.state.localReady=true;await flush()
    await m.state.continueDesktop()
    expect(api.resumeDesktop).not.toHaveBeenCalled()
    expect(api.prepareDesktop).not.toHaveBeenCalled()
    expect(messages.warning).toHaveBeenCalledWith(expect.stringContaining('没有可继续'))
  } finally { m.app.unmount() }
})

it('automatically selects a single scoped local connection on reopen without returning a password', async () => {
  const m = render('desktop')
  local.request.mockResolvedValue({ items: [localConnection()], scope: { projectId: '21', deviceId: '9007199254740993', protocol: 'SSH2' } })
  try {
    await m.state.open('30'); m.state.localReady = true; await flush()
    expect(api.desktopAccess).toHaveBeenCalledWith('cutover-checklist', '30', {
      operation: 'CONNECTIONS_READ', deviceId: '9007199254740993', protocol: 'SSH'
    })
    expect(m.state.credentialMode).toBe('saved')
    expect(m.state.form.credentialId).toBe('local-connection-1')
    expect(m.state.selectedConnection.host).toBe('192.0.2.10')
    expect(m.state.selectedConnection.port).toBe(2222)
    expect(m.state.form.password).toBe('')
    m.state.credentialMode = 'temporary'; await flush()
    await m.state.loadConnections(); await flush()
    expect(m.state.credentialMode).toBe('temporary')
    await m.state.open('30'); m.state.localReady = true; await flush()
    expect(m.state.form.credentialId).toBe('local-connection-1')
  } finally { m.app.unmount() }
})

it('does not guess among multiple matches and supports project/protocol lookup without a device', async () => {
  const m = render('desktop')
  try {
    local.request.mockResolvedValue({ items: [localConnection('first'),localConnection('second')], scope: { projectId: '21', deviceId: '9007199254740993', protocol: 'SSH2' } })
    await m.state.open('30'); m.state.localReady = true; await flush()
    expect(m.state.credentialMode).toBe('saved')
    expect(m.state.form.credentialId).toBeUndefined()
    local.request.mockResolvedValue({ items: [localConnection('first'),localConnection('second')], scope: { projectId: '21', deviceId: null, protocol: 'SSH2' } })
    m.state.form.deviceId = undefined; await flush()
    expect(api.desktopAccess).toHaveBeenLastCalledWith('cutover-checklist','30', {
      operation: 'CONNECTIONS_READ', deviceId: undefined, protocol: 'SSH'
    })
    local.request.mockResolvedValue({ items: [], scope: { projectId: '21', deviceId: null, protocol: 'TELNET' } })
    m.state.form.protocol = 'TELNET'; await flush()
    expect(m.state.credentialMode).toBe('temporary')
    expect(m.state.form.credentialId).toBeUndefined()
    expect(m.state.form.password).toBe('')
  } finally { m.app.unmount() }
})

it('rejects unscoped old-client connection lists instead of matching all saved connections', async () => {
  const m=render('desktop')
  local.request.mockResolvedValue({items:[localConnection()]})
  try {
    await m.state.open('30'); m.state.localReady=true; await flush()
    expect(m.state.connections).toEqual([])
    expect(m.state.form.credentialId).toBeUndefined()
    expect(m.state.localConnectionScopeReady).toBe(false)
  } finally { m.app.unmount() }
})
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
