import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { defineComponent } from 'vue'
import ElementPlus from 'element-plus'
import CollectionTaskPanel from './CollectionTaskPanel.vue'
import TargetSelector from './TargetSelector.vue'
import ParserSidebar from './ParserSidebar.vue'
import ProjectCollectionView from '@/views/ProjectCollectionView.vue'
import { downloadSessionRecord } from '@/utils/session-export'
import { offerScript, consumeScript } from '@/management/script-handoff'
import ScriptArtifactEditor from './ScriptArtifactEditor.vue'
import type { CollectionDetails, ConnectionRequest, ScriptDraft, CollectionSubmission } from '@/types/collection'

const api = vi.hoisted(() => ({
  findProjects: vi.fn(), findDevices: vi.fn(), getParserSelectionOptions: vi.fn(),
  getCollection: vi.fn(), getGenericCollection: vi.fn(), getCollectionSemanticResults: vi.fn(),
  submitCollection: vi.fn(), submitGenericCollection: vi.fn(), upsertSchedule: vi.fn()
}))
vi.mock('@/api/device-ops', () => api)
vi.mock('@/api/collection-output-stream', () => ({ streamCollectionOutput: vi.fn(() => new Promise(() => {})) }))
vi.mock('vue-router', () => ({ onBeforeRouteLeave: vi.fn() }))

const script: ScriptDraft = { source: 'LOCAL_MANAGED', key: 'show', version: '1', content: 'show version', sha256: 'a'.repeat(64), policy: 'EXECUTION_ONLY', parserType: 'NONE', parserConfig: '' }
const project = (key: string) => ({ namespace: 'ns', projectKey: key, projectName: key, projectCode: key })
const device = (key: string) => ({ deviceKey: key, deviceName: key, vendor: 'vendor', model: 'model' })
const connection: ConnectionRequest = { protocol: 'SSH2', executionMode: 'SHELL', authenticationType: 'PASSWORD', host: 'host', port: 22, username: 'ops', password: 'secret', connectTimeoutSeconds: 10 }
const submission = (): CollectionSubmission => ({ mode: 'generic', deviceLabel: 'host', request: { namespace: 'ns', connection: structuredClone(connection), script: { ...script }, activityType: 'IMPLEMENTATION', commandTimeoutSeconds: 120, parseTimeoutSeconds: 15, leaseGraceSeconds: 10 } })
const details: CollectionDetails = { collectionId: 'c1', namespace: 'ns', status: 'SUCCEEDED', script, targets: [] }
const wrappers: VueWrapper[] = []
function render(component: Parameters<typeof mount>[0], options = {}): VueWrapper {
  const wrapper = mount(component, { global: { plugins: [ElementPlus] }, ...options })
  wrappers.push(wrapper)
  return wrapper
}
function button(wrapper: VueWrapper, text: string) { return wrapper.findAll('button').find(item => item.text().includes(text)) }
function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
beforeEach(() => {
  vi.clearAllMocks()
  window.history.replaceState({}, '', '/')
  vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn() })))
  api.submitGenericCollection.mockResolvedValue({ collectionId: 'c1' })
  api.getGenericCollection.mockResolvedValue(details)
  api.getCollectionSemanticResults.mockResolvedValue([{ state: 'RUNNING' }])
  api.getParserSelectionOptions.mockResolvedValue({ automaticEnabled: true, defaultAvailable: true, options: [] })
  api.findProjects.mockResolvedValue([])
  api.findDevices.mockResolvedValue([])
})
afterEach(() => { wrappers.splice(0).forEach(wrapper => wrapper.unmount()); vi.unstubAllGlobals(); vi.restoreAllMocks() })

describe('phase one workbench regressions', () => {
  it('consumes safe script handoff once into the real editor without executing', async () => {
    offerScript({ content: 'show safe', scriptKey: 'safe-library', scriptVersion: 'v2' })
    const options = { props: { projectKey: 'direct' }, global: { plugins: [ElementPlus], stubs: {
      AppShell: { template: '<div><slot /></div>' }, TargetSelector: true, ParserSidebar: true
    } } }
    const wrapper = render(ProjectCollectionView, options)
    await flushPromises()
    const model = wrapper.findComponent(ScriptArtifactEditor).props('modelValue')
    expect(model).toMatchObject({ content: 'show safe', key: 'safe-library', version: 'v2', policy: 'EXECUTION_ONLY' })
    await vi.waitFor(() => expect(model.sha256).toMatch(/^[a-f0-9]{64}$/))
    expect(consumeScript()).toBeUndefined()
    expect(api.submitCollection).not.toHaveBeenCalled()
    expect(api.submitGenericCollection).not.toHaveBeenCalled()
    expect(window.location.search).toBe('')
    const second = render(ProjectCollectionView, options)
    await flushPromises()
    expect(second.findComponent(ScriptArtifactEditor).props('modelValue').content).toBe('')
  })

  it('invalidates hidden-context project requests before reopening', async () => {
    const old = deferred<ReturnType<typeof project>[]>()
    api.findProjects.mockReturnValueOnce(old.promise).mockResolvedValueOnce([project('B')])
    const wrapper = render(TargetSelector, { props: { projectKey: 'A' }, global: { plugins: [ElementPlus], stubs: { ConnectionSourceTabs: true, ProtocolConnectionForm: true } } })
    const toggle = wrapper.findComponent({ name: 'ElSwitch' })
    toggle.vm.$emit('update:modelValue', true)
    await flushPromises()
    toggle.vm.$emit('update:modelValue', false)
    await flushPromises()
    await wrapper.setProps({ projectKey: 'B' })
    old.resolve([project('A')])
    await flushPromises()
    toggle.vm.$emit('update:modelValue', true)
    await flushPromises()
    expect(api.findProjects).toHaveBeenCalledWith('B')
    expect(wrapper.findAllComponents({ name: 'ElSelect' })[0]!.props('modelValue')).toBe('B')
  })

  it('invalidates pending device results when context is cleared', async () => {
    const old = deferred<ReturnType<typeof device>[]>()
    api.findProjects.mockResolvedValue([project('A')])
    api.findDevices.mockReturnValue(old.promise)
    const wrapper = render(TargetSelector, { props: { projectKey: 'A' }, global: { plugins: [ElementPlus], stubs: { ConnectionSourceTabs: true, ProtocolConnectionForm: true } } })
    const toggle = wrapper.findComponent({ name: 'ElSwitch' })
    toggle.vm.$emit('update:modelValue', true)
    await flushPromises()
    toggle.vm.$emit('update:modelValue', false)
    await flushPromises()
    old.resolve([device('stale')])
    await flushPromises()
    toggle.vm.$emit('update:modelValue', true)
    await flushPromises()
    expect(wrapper.findAllComponents({ name: 'ElOption' }).map(item => item.props('value'))).not.toContain('stale')
  })
  it.each(['SHELL', 'EXEC', 'SAVED'] as const)('preserves project connection %s mode', async executionMode => {
    const start = vi.fn().mockResolvedValue(true)
    const wrapper = render(ProjectCollectionView, {
      props: { projectKey: 'A' },
      global: { plugins: [ElementPlus], stubs: {
        AppShell: { template: '<div><slot /></div>' },
        TargetSelector: defineComponent({ setup(_, { expose }) { expose({ buildSelection: () => ({ namespace: 'ns', project: project('A'), device: device('d'), connection: executionMode === 'SAVED' ? { savedConnectionId: 'saved', credentialNamespace: 'ns' } : { ...connection, executionMode }, deviceLabel: 'd' }), rememberCurrent: vi.fn() }); return () => null } }),
        ScriptArtifactEditor: defineComponent({ emits: ['update:modelValue'], mounted() { this.$emit('update:modelValue', { ...script }) }, template: '<div />' }),
        ParserSidebar: true,
        CollectionTaskPanel: defineComponent({ emits: ['submit'], setup(_, { expose }) { expose({ start }); }, template: '<button @click="$emit(\'submit\')">submit</button>' })
      } }
    })
    await flushPromises()
    await button(wrapper, 'submit')!.trigger('click')
    await flushPromises()
    expect(start).toHaveBeenCalledOnce()
    const target = start.mock.calls[0]![0].request.targets[0]
    if (executionMode === 'SAVED') {
      expect(target).toMatchObject({ savedConnectionId: 'saved', credentialNamespace: 'ns' })
      expect(target).not.toHaveProperty('executionMode')
      expect(target).not.toHaveProperty('password')
    } else expect(target.executionMode).toBe(executionMode)
  })

  it('resumes parser polling after collection completion without resubmission', async () => {
    const wrapper = render(CollectionTaskPanel)
    await (wrapper.vm as unknown as { start: (value: CollectionSubmission) => Promise<boolean> }).start(submission())
    await flushPromises()
    await button(wrapper, '停止查看')!.trigger('click')
    expect(button(wrapper, '继续查看')).toBeDefined()
    await button(wrapper, '继续查看')!.trigger('click')
    await flushPromises()
    expect(api.getCollectionSemanticResults).toHaveBeenCalledTimes(2)
    expect(api.submitGenericCollection).toHaveBeenCalledOnce()
  })

  it('can resume after parser query failure and hides resume only after parsing completes', async () => {
    api.getCollectionSemanticResults.mockRejectedValueOnce(new Error('temporary')).mockResolvedValueOnce([{ state: 'SUCCEEDED' }])
    const wrapper = render(CollectionTaskPanel)
    await (wrapper.vm as unknown as { start: (value: CollectionSubmission) => Promise<boolean> }).start(submission())
    await flushPromises()
    await button(wrapper, '停止查看')!.trigger('click')
    await button(wrapper, '继续查看')!.trigger('click')
    await flushPromises()
    expect(api.submitGenericCollection).toHaveBeenCalledOnce()
    expect(button(wrapper, '继续查看')).toBeUndefined()
    expect(button(wrapper, '停止查看')).toBeUndefined()
  })

  it('rejects overlapping submissions', async () => {
    const pending = deferred<{ collectionId: string }>()
    api.submitGenericCollection.mockReturnValue(pending.promise)
    const wrapper = render(CollectionTaskPanel)
    const start = (wrapper.vm as unknown as { start: (value: CollectionSubmission) => Promise<boolean> }).start
    const first = start(submission())
    const second = start(submission())
    expect(api.submitGenericCollection).toHaveBeenCalledOnce()
    pending.resolve({ collectionId: 'c1' })
    await Promise.all([first, second])
  })

  it('marks restored input unavailable and exports unknown submission time', async () => {
    window.history.replaceState({}, '', '/?collectionId=c1&namespace=ns&mode=generic')
    const blobs: Blob[] = []
    vi.stubGlobal('Blob', class { constructor(parts: string[]) { blobs.push(parts.join('') as unknown as Blob) } })
    vi.stubGlobal('URL', Object.assign(URL, { createObjectURL: vi.fn(() => 'blob:test'), revokeObjectURL: vi.fn() }))
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    const wrapper = render(CollectionTaskPanel)
    await flushPromises()
    expect(wrapper.text()).toContain('原始脚本不可用')
    expect(button(wrapper, '下载输入记录')).toBeUndefined()
    await button(wrapper, '下载完整记录')!.trigger('click')
    expect(String(blobs[0])).toContain('submittedAt=unknown')
    expect(String(blobs[0])).toContain('[INPUT]\n原始脚本不可用')
  })

  it('exports explicit submission evidence unchanged and missing evidence explicitly', () => {
    const contents: string[] = []
    vi.stubGlobal('Blob', class { constructor(parts: string[]) { contents.push(parts.join('')) } })
    URL.createObjectURL = vi.fn(() => 'blob:test')
    URL.revokeObjectURL = vi.fn()
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    downloadSessionRecord('host', '', details, '')
    expect(contents[0]).toContain('submittedAt=unknown')
    expect(contents[0]).toContain('原始脚本不可用')
    downloadSessionRecord('host', 'show version', details, '2026-01-01T00:00:00Z')
    expect(contents[1]).toContain('submittedAt=2026-01-01T00:00:00Z')
    expect(contents[1]).toContain('[INPUT]\nshow version')
  })

  it('retries parser options errors and distinguishes successful empty releases', async () => {
    api.getParserSelectionOptions.mockRejectedValueOnce(new Error('选项请求失败'))
    const wrapper = render(ParserSidebar, { props: { modelValue: { ...script }, selection: { mode: 'AUTO' } } })
    await flushPromises()
    expect(wrapper.text()).toContain('选项请求失败')
    expect(wrapper.text()).not.toContain('暂无已发布解析版本')
    expect(button(wrapper, '重试')).toBeDefined()
    await button(wrapper, '重试')!.trigger('click')
    await flushPromises()
    expect(api.getParserSelectionOptions).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).not.toContain('选项请求失败')
    expect(wrapper.text()).toContain('暂无已发布解析版本')
  })

  it('deduplicates parser refresh while options are loading', async () => {
    const pending = deferred<{ automaticEnabled: boolean; defaultAvailable: boolean; options: [] }>()
    api.getParserSelectionOptions.mockResolvedValueOnce({ automaticEnabled: true, defaultAvailable: false, options: [] }).mockReturnValueOnce(pending.promise)
    const wrapper = render(ParserSidebar, { props: { modelValue: { ...script }, selection: { mode: 'AUTO' } } })
    await flushPromises()
    const refresh = button(wrapper, '刷新解析版本')!
    await refresh.trigger('click')
    await refresh.trigger('click')
    expect(api.getParserSelectionOptions).toHaveBeenCalledTimes(2)
    pending.resolve({ automaticEnabled: true, defaultAvailable: false, options: [] })
    await flushPromises()
  })

  it.each(['project', 'device'] as const)('ignores pending %s success after unmount', async kind => {
    const pending = deferred<ReturnType<typeof project>[] | ReturnType<typeof device>[]>()
    api.findProjects.mockResolvedValue([project('A')])
    if (kind === 'project') api.findProjects.mockReturnValue(pending.promise)
    else api.findDevices.mockReturnValue(pending.promise)
    const wrapper = render(TargetSelector, { props: { projectKey: 'A' }, global: { plugins: [ElementPlus], stubs: { ConnectionSourceTabs: true, ProtocolConnectionForm: true } } })
    wrapper.findComponent({ name: 'ElSwitch' }).vm.$emit('update:modelValue', true)
    await flushPromises()
    // Retain the disposed component state to detect late writes after its DOM is gone.
    const state = (wrapper.vm.$ as unknown as { setupState: Record<string, unknown> }).setupState
    wrapper.unmount()
    wrappers.splice(wrappers.indexOf(wrapper), 1)
    const before = { projects: state.projects, devices: state.devices, error: state.masterDataError, loading: state.masterDataLoading }
    pending.resolve(kind === 'project' ? [project('A')] : [device('late')])
    await flushPromises()
    expect({ projects: state.projects, devices: state.devices, error: state.masterDataError, loading: state.masterDataLoading }).toEqual(before)
  })

  it('keeps newer project loading when an older request finishes first', async () => {
    const old = deferred<ReturnType<typeof project>[]>()
    const current = deferred<ReturnType<typeof project>[]>()
    api.findProjects.mockReturnValueOnce(old.promise).mockReturnValueOnce(current.promise)
    const wrapper = render(TargetSelector, { props: { projectKey: 'A' }, global: { plugins: [ElementPlus], stubs: { ConnectionSourceTabs: true, ProtocolConnectionForm: true } } })
    wrapper.findComponent({ name: 'ElSwitch' }).vm.$emit('update:modelValue', true)
    await flushPromises()
    await wrapper.setProps({ projectKey: 'B' })
    old.reject(new Error('old failure'))
    await flushPromises()
    expect(wrapper.findAllComponents({ name: 'ElSelect' })[0]!.props('loading')).toBe(true)
    expect(wrapper.text()).not.toContain('只读主档暂不可用')
    current.resolve([project('B')])
    await flushPromises()
    expect(wrapper.findAllComponents({ name: 'ElSelect' })[0]!.props('loading')).toBe(false)
  })

  it.each(['resolve', 'reject'] as const)('ignores stale project %s after route changes', async outcome => {
    const old = deferred<ReturnType<typeof project>[]>()
    api.findProjects.mockReturnValueOnce(old.promise).mockResolvedValueOnce([project('B')])
    const wrapper = render(TargetSelector, { props: { projectKey: 'A' }, global: { plugins: [ElementPlus], stubs: { ConnectionSourceTabs: true, ProtocolConnectionForm: true } } })
    wrapper.findComponent({ name: 'ElSwitch' }).vm.$emit('update:modelValue', true)
    await flushPromises()
    await wrapper.setProps({ projectKey: 'B' })
    await flushPromises()
    expect(api.findProjects).toHaveBeenCalledWith('B')
    if (outcome === 'resolve') old.resolve([project('A')]); else old.reject(new Error('old'))
    await flushPromises()
    const select = wrapper.findAllComponents({ name: 'ElSelect' })[0]!
    expect(select.props('modelValue')).toBe('B')
    expect(select.props('loading')).toBe(false)
    expect(wrapper.text()).not.toContain('只读主档暂不可用')
  })

  it.each(['resolve', 'reject'] as const)('ignores stale device %s after selection changes', async outcome => {
    const old = deferred<ReturnType<typeof device>[]>()
    api.findProjects.mockResolvedValue([project('A'), project('B')])
    api.findDevices.mockReturnValueOnce(old.promise).mockResolvedValueOnce([device('B-device')])
    const wrapper = render(TargetSelector, { props: { projectKey: 'direct' }, global: { plugins: [ElementPlus], stubs: { ConnectionSourceTabs: true, ProtocolConnectionForm: true } } })
    wrapper.findComponent({ name: 'ElSwitch' }).vm.$emit('update:modelValue', true)
    await flushPromises()
    const select = wrapper.findAllComponents({ name: 'ElSelect' })[0]!
    select.vm.$emit('update:modelValue', 'A')
    await flushPromises()
    select.vm.$emit('update:modelValue', 'B')
    await flushPromises()
    if (outcome === 'resolve') old.resolve([device('A-device')]); else old.reject(new Error('old'))
    await flushPromises()
    const values = wrapper.findAllComponents({ name: 'ElOption' }).map(item => item.props('value'))
    expect(values).toContain('B-device')
    expect(values).not.toContain('A-device')
    expect(wrapper.text()).not.toContain('设备选项加载失败')
  })
})
