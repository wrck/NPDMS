import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import CollectionTaskPanel from './CollectionTaskPanel.vue'
import panelSource from './CollectionTaskPanel.vue?raw'
import recordsSource from '@/views/management/RecordsManagementView.vue?raw'
import SemanticResultPanel from './SemanticResultPanel.vue'
import type { CollectionDetails, CollectionSubmission, CollectionTargetDetails } from '@/types/collection'
import type { CollectionSemanticResult } from '@/types/parser'
import { blocksForTarget, renderCommandTranscript } from '@/utils/command-output-blocks'

const api = vi.hoisted(() => ({ getCollection: vi.fn(), getGenericCollection: vi.fn(), getCollectionSemanticResults: vi.fn(), submitCollection: vi.fn(), submitGenericCollection: vi.fn() }))
vi.mock('@/api/device-ops', () => api)
vi.mock('@/api/collection-output-stream', () => ({ streamCollectionOutput: vi.fn(() => new Promise(() => {})) }))
const evidenceApi = vi.hoisted(() => ({ getCollectionEvidence: vi.fn() }))
vi.mock('@/api/collection-evidence', () => evidenceApi)
const evidence = (content = 'show original') => ({ metadata: { collectionId: 'c1', namespace: 'ns', createdAt: '2026-09-08T00:00:00Z' }, input: { contentStatus: 'AVAILABLE', content }, submission: { provenance: 'CAPTURED_SUBMISSION', snapshot: { schemaVersion: 1, body: { namespace: 'ns', activityType: 'ORIGINAL' } }, omittedFields: ['body.connection.password'] }, executionFacts: { targets: [], semanticParsing: null } })
const wrappers: VueWrapper[] = []
const target = (id: number): CollectionTargetDetails => ({ targetId: id, contextSnapshot: { extensions: {} }, endpointSnapshot: { protocol: 'SSH2', host: `host-${id}`, port: 22, username: 'ops' }, status: 'PARTIAL_SUCCESS', stdout: 'output', stderr: '', truncated: false, parsedFacts: { model: `model-${id}` }, commandBlocks: [] })
const details: CollectionDetails = { collectionId: 'c1', namespace: 'ns', status: 'PARTIAL_SUCCESS', script: { source: 'LOCAL_MANAGED', key: 'show', version: '1', sha256: 'a'.repeat(64), parserType: 'NONE' }, targets: [target(1), target(2)] }
const result = (id: number): CollectionSemanticResult => ({ targetId: id, taskId: `task-${id}`, state: 'RUNNING' } as CollectionSemanticResult)
function render(component: typeof CollectionTaskPanel | typeof SemanticResultPanel, props = {}) { const wrapper = mount(component, { props, global: { plugins: [ElementPlus] } }); wrappers.push(wrapper); return wrapper }
beforeEach(() => { vi.clearAllMocks(); evidenceApi.getCollectionEvidence.mockResolvedValue(evidence()); window.history.replaceState({}, '', '/'); api.getGenericCollection.mockResolvedValue(details); api.getCollectionSemanticResults.mockResolvedValue([result(1), result(2)]) })
afterEach(() => { wrappers.splice(0).forEach(w => w.unmount()); vi.restoreAllMocks() })

it('scopes outer tab sizing and makes terminal pre and light evidence colors explicit', () => {
  expect(panelSource).not.toContain('.task-output-tabs :deep(.el-tab-pane)')
  expect(panelSource).toContain('.task-output-tabs > :deep(.el-tabs__content)')
  expect(panelSource).toMatch(/\.terminal-output__target pre\s*\{[^}]*background:\s*#101820;[^}]*color:\s*#f4f8f9;/s)
  expect(panelSource).toMatch(/\.request-evidence\s*\{[^}]*background:\s*#101820;[^}]*color:\s*#f4f8f9;/s)
  expect(panelSource).toMatch(/\.facts\s*\{[^}]*background:\s*var\(--el-bg-color, #fff\);/s)
  expect(recordsSource).not.toContain('SemanticResultPanel')
  expect(recordsSource).not.toContain('ref="panel"')
})
it('keeps all status rail states readable on the task terminal without changing global light rails', () => {
  expect(panelSource).toMatch(/\.task-panel \.status-rail li\s*\{[^}]*color:\s*#aebdc4;/s)
  expect(panelSource).toMatch(/\.task-panel \.status-rail li\.complete\s*\{[^}]*color:\s*#63d5c7;/s)
  expect(panelSource).toMatch(/\.task-panel \.status-rail li\.active\s*\{[^}]*color:\s*#f4f8f9;/s)
})
it('owns semantic results inside the shared four tabs and readonly never executes', async () => {
  window.history.replaceState({}, '', '/?collectionId=c1&namespace=ns&mode=generic')
  const wrapper = render(CollectionTaskPanel, { readonly: true })
  await flushPromises()
  expect(wrapper.findComponent(SemanticResultPanel).exists()).toBe(true)
  expect(wrapper.findComponent(SemanticResultPanel).props('results')).toEqual([result(1), result(2)])
  expect(wrapper.findComponent(SemanticResultPanel).props('collectionDetails')).toEqual(details)
  expect(wrapper.findAll('[role="tab"]').map(t => t.text())).toEqual(['标准输出', '错误输出', '解析事实', '请求快照'])
  expect(wrapper.text()).not.toContain('连接并执行采集')
  expect(api.submitGenericCollection).not.toHaveBeenCalled()
  expect(wrapper.text()).toContain('部分成功')
  expect(wrapper.text()).not.toContain('采集成功 / 解析失败')
})
it('expands the first semantic target once and preserves user collapse across polling', async () => {
  const wrapper = render(SemanticResultPanel, { results: [] })
  await wrapper.setProps({ results: [result(1), result(2)] })
  const collapse = wrapper.findComponent({ name: 'ElCollapse' })
  expect(collapse.props('modelValue')).toBe('task-1')
  collapse.vm.$emit('update:modelValue', '')
  await wrapper.setProps({ results: [result(1), result(2)] })
  expect(collapse.props('modelValue')).toBe('')
})
it('partitions compatibility facts by target without duplicating the first target fallback', () => {
  const wrapper = render(SemanticResultPanel, { collectionDetails: details, legacyFacts: details.targets[0]!.parsedFacts })
  expect(wrapper.text().match(/model-1/g)).toHaveLength(1)
  expect(wrapper.text().match(/model-2/g)).toHaveLength(1)
  expect(wrapper.text()).toContain('host-1')
  expect(wrapper.text()).toContain('host-2')
})
it('restores authoritative input and captured submission independently with retry', async () => {
  evidenceApi.getCollectionEvidence.mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce(evidence(''))
  window.history.replaceState({}, '', '/?collectionId=c1&namespace=ns&mode=generic')
  const wrapper = render(CollectionTaskPanel, { readonly: true })
  await flushPromises()
  expect(wrapper.text()).toContain('output')
  const retry = wrapper.findAll('button').find(b => b.text().includes('重试证据'))
  expect(retry).toBeDefined()
  await retry!.trigger('click'); await flushPromises()
  expect(wrapper.findAll('button').some(b => b.text().includes('下载输入记录'))).toBe(true)
  expect(wrapper.text()).toContain('原提交快照（已脱敏）')
  expect(wrapper.text()).toContain('ORIGINAL')
  expect(wrapper.findAll('.request-snapshot')).toHaveLength(1)
  expect(JSON.parse(wrapper.get('.request-snapshot').text()).body.script.content).toBe('')
  expect(wrapper.text()).not.toContain('restoredCollectionId')
})
it('never substitutes a local retry body when server evidence is reconstructed', async () => {
  evidenceApi.getCollectionEvidence.mockResolvedValue({ ...evidence(), submission: { provenance: 'RECONSTRUCTED_FACTS', snapshot: null, omittedFields: [] } })
  api.submitGenericCollection.mockResolvedValue({ collectionId: 'c1' })
  const wrapper = render(CollectionTaskPanel)
  await (wrapper.vm as unknown as { start: (s: CollectionSubmission) => Promise<boolean> }).start({ mode: 'generic', deviceLabel: 'host', request: { namespace: 'ns', activityType: 'LOCAL-RETRY-BODY', connection: { host: 'host' }, script: { ...details.script, content: 'local' } } } as CollectionSubmission)
  await flushPromises()
  expect(wrapper.text()).not.toContain('LOCAL-RETRY-BODY')
  expect(wrapper.text()).toContain('历史执行事实（非原提交请求）')
  const snapshot = JSON.parse(wrapper.get('.request-snapshot').text())
  expect(snapshot).toMatchObject({ provenance: 'RECONSTRUCTED_FACTS', script: { content: 'show original' }, executionFacts: { targets: [] } })
  expect(snapshot).not.toHaveProperty('body')
})
it('does not offer local attempt input as winner evidence while server evidence is pending', async () => {
  evidenceApi.getCollectionEvidence.mockReturnValue(new Promise(() => {}))
  api.submitGenericCollection.mockResolvedValue({ collectionId: 'c1' })
  const wrapper = render(CollectionTaskPanel)
  await (wrapper.vm as unknown as { start: (s: CollectionSubmission) => Promise<boolean> }).start({ mode: 'generic', deviceLabel: 'host', request: { namespace: 'ns', connection: { host: 'host' }, script: { ...details.script, content: 'retry input' } } } as CollectionSubmission)
  await flushPromises()
  expect(wrapper.findAll('button').some(b => b.text().includes('下载输入记录'))).toBe(false)
  expect(wrapper.findAll('.request-snapshot')).toHaveLength(0)
  expect(wrapper.text()).not.toContain('retry input')
  const contents: string[] = []
  vi.stubGlobal('Blob', class { constructor(parts: string[]) { contents.push(parts.join('')) } })
  URL.createObjectURL = vi.fn(() => 'blob:test'); URL.revokeObjectURL = vi.fn()
  vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
  await wrapper.findAll('button').find(b => b.text().includes('下载完整记录'))!.trigger('click')
  expect(contents[0]).toContain('submittedAt=unknown')
  expect(contents[0]).not.toContain('retry input')
  vi.unstubAllGlobals()
})
it.each(['ERROR', 'RESTRICTED'])('never falls back to local content after accepted evidence %s', async status => {
  evidenceApi.getCollectionEvidence.mockRejectedValue(status === 'RESTRICTED' ? { isAxiosError: true, response: { status: 403 } } : new Error('offline'))
  api.submitGenericCollection.mockResolvedValue({ collectionId: 'c1' })
  const wrapper = render(CollectionTaskPanel)
  await (wrapper.vm as unknown as { start: (s: CollectionSubmission) => Promise<boolean> }).start({ mode: 'generic', deviceLabel: 'host', request: { namespace: 'ns', connection: { host: 'host' }, script: { ...details.script, content: 'LOCAL_SENTINEL' } } } as CollectionSubmission)
  await flushPromises()
  expect(wrapper.findAll('.request-snapshot')).toHaveLength(0)
  expect(wrapper.text()).not.toContain('LOCAL_SENTINEL')
})
it('does not resurrect requests after an aborted POST settles', async () => {
  let accept!: (v: { collectionId: string }) => void
  api.submitGenericCollection.mockReturnValue(new Promise(resolve => { accept = resolve }))
  const wrapper = render(CollectionTaskPanel)
  const state = wrapper.vm as unknown as { start: (s: CollectionSubmission) => Promise<boolean>; stopPolling: () => void }
  const pending = state.start({ mode: 'generic', deviceLabel: 'host', request: { namespace: 'ns', connection: { host: 'host' }, script: { ...details.script, content: 'input' } } } as CollectionSubmission)
  state.stopPolling(); wrapper.unmount()
  accept({ collectionId: 'c1' }); expect(await pending).toBe(false)
  expect(evidenceApi.getCollectionEvidence).not.toHaveBeenCalled()
  expect(api.getGenericCollection).not.toHaveBeenCalled()
})
it('reports an unconfirmed POST outcome without inviting duplicate execution', async () => {
  api.submitGenericCollection.mockRejectedValue(new Error('timeout'))
  const wrapper = render(CollectionTaskPanel)
  await (wrapper.vm as unknown as { start: (s: CollectionSubmission) => Promise<boolean> }).start({ mode: 'generic', deviceLabel: 'host', request: { namespace: 'ns', connection: { host: 'host' }, script: { ...details.script, content: 'input' } } } as CollectionSubmission)
  expect(wrapper.text()).toContain('提交结果未确认')
  expect(wrapper.text()).toContain('避免重复执行')
  expect(api.submitGenericCollection).toHaveBeenCalledOnce()
})
it('blocks readonly imperative execution as well as its button', async () => {
  const wrapper = render(CollectionTaskPanel, { readonly: true })
  expect(await (wrapper.vm as unknown as { start: (s: CollectionSubmission) => Promise<boolean> }).start({} as CollectionSubmission)).toBe(false)
  expect(api.submitGenericCollection).not.toHaveBeenCalled()
})
it('freezes the submitted body before caller edits and replaces display after acceptance', async () => {
  let accept!: (v: { collectionId: string }) => void
  api.submitGenericCollection.mockReturnValue(new Promise(resolve => { accept = resolve }))
  const wrapper = render(CollectionTaskPanel)
  const request = { namespace: 'ns', connection: { protocol: 'SSH2', host: 'host', port: 22, username: 'ops', password: 'secret' }, script: { ...details.script, content: 'original input' } }
  const running = (wrapper.vm as unknown as { start: (s: CollectionSubmission) => Promise<boolean> }).start({ mode: 'generic', deviceLabel: 'host', request } as CollectionSubmission)
  request.script.content = 'edited'; request.connection.host = 'edited'
  expect(api.submitGenericCollection.mock.calls[0]![0].script.content).toBe('original input')
  expect(api.submitGenericCollection.mock.calls[0]![0].connection.host).toBe('host')
  accept({ collectionId: 'c1' }); await running; await flushPromises()
  expect(wrapper.text()).toContain('ORIGINAL')
  expect(wrapper.text()).not.toContain('secret')
})
it('renders both server and client legacy blocks as unknown without rewriting evidence', () => {
  const block = blocksForTarget(target(1))[0]!
  const serverBlock = { ...block, status: 'SUCCEEDED' as const }
  expect(renderCommandTranscript([block, serverBlock])).not.toContain('status=SUCCEEDED')
  expect(renderCommandTranscript([block, serverBlock])).toContain('status=UNKNOWN')
  expect(serverBlock.status).toBe('SUCCEEDED')
})
