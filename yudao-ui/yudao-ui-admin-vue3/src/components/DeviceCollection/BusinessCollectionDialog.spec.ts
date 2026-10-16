import { defineComponent, h, nextTick } from 'vue'
import { beforeEach, expect, it, vi } from 'vitest'
import Dialog from './BusinessCollectionDialog.vue'
import CollectionDialog from './CollectionDialog.vue'
import CollectionWithDeviceLogsDialog from './CollectionWithDeviceLogsDialog.vue'
import { mount, passthrough, textOf } from '@/views/pms/cutover/cutoverTestHarness'

const api = vi.hoisted(() => ({ context: vi.fn(), templates: vi.fn(), page: vi.fn(), usableConnections: vi.fn(), saveConnection: vi.fn(), connections: vi.fn(), submit: vi.fn() }))
const messages = vi.hoisted(() => ({ success: vi.fn(), warning: vi.fn(), confirm: vi.fn(async () => {}) }))
vi.mock('@/api/pms/platform/deviceCollection', () => api)
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => messages }))
vi.mock('@/utils/permission', () => ({ checkPermi: () => true }))
vi.mock('@/utils', () => ({ generateUUID: () => 'connection-save-request-123456' }))
vi.mock('@/utils/formatTime', () => ({ dateFormatter: () => '' }))
vi.mock('@/components/ProjectDeviceSelect/index.vue', () => ({ default: {} }))
vi.mock('./LogParsingDialog.vue', () => ({ default: {} }))

const dialog = defineComponent({ props: ['modelValue'], setup(props, { slots }) { return () => props.modelValue ? h('section', [slots.default?.(), slots.footer?.()]) : null } })
const form = defineComponent({ setup(_, { slots, expose }) { expose({ validate: async () => true, validateField: async () => true }); return () => h('form', slots.default?.()) } })
const controls = Object.fromEntries(['ElAlert', 'ElFormItem', 'ElRadioGroup', 'ElRadio', 'ElSelect', 'ElOption', 'ElRow', 'ElCol', 'ElInput', 'ElInputNumber', 'ElButton', 'ElTable', 'ElTableColumn', 'ElDatePicker', 'ElTag', 'Pagination'].map(key => [key, passthrough]))
const flush = async () => { for (let i = 0; i < 12; i++) await nextTick() }
const render = () => {
  const mounted = mount(Dialog, { entry: 'cutover-checklist', deviceId: '9007199254740993' }, { ...controls, Dialog: dialog, ElForm: form })
  return { ...mounted, state: (mounted.vm as any).$.setupState }
}
beforeEach(() => {
  vi.clearAllMocks()
  api.context.mockResolvedValue({ entry: 'cutover-checklist', objectId: '30', projectId: '21', deviceId: '9007199254740993', version: 3, manualAllowed: true, canExecute: true, title: '调研采集' })
  api.templates.mockResolvedValue([])
  api.page.mockResolvedValue({ list: [], total: 0 })
  api.usableConnections.mockResolvedValue([])
  api.connections.mockResolvedValue([])
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
