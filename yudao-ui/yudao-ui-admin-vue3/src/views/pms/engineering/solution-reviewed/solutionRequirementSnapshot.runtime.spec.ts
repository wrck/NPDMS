import { afterEach, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, ref } from 'vue'
import { mount, passthrough, textOf, testEventHandler, type TestNode } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
import Chapters from './SolutionReviewChapterForm.vue'

const api = vi.hoisted(() => ({ workspace: vi.fn(), emptyPage: vi.fn(async () => ({ list: [], total: 0 })) }))
vi.mock('@/api/pms/engineering/requirement-analysis/entity', () => ({ workspace: api.workspace }))
vi.mock('@/api/pms/project/unified-members', () => ({ getMemberPage: api.emptyPage, logicalMemberRole: () => '' }))
vi.mock('@/api/pms/customer/contacts', () => ({ getProjectPage: api.emptyPage }))
vi.mock('@/api/pms/engineering/stage-plan', () => ({ getStagePlanBatchPage: api.emptyPage }))
vi.mock('@/api/pms/asset/device/archive', () => ({ getDeviceArchivePage: api.emptyPage }))
vi.mock('@/api/pms/commerce', () => ({ getDeliveryScopePage: api.emptyPage }))
vi.mock('@/components/BusinessEntity/DeliveryPanel.vue', () => ({ default: passthrough }))
vi.mock('@/utils/dict', () => ({ DICT_TYPE: {}, getDictLabel: () => '' }))
vi.mock('./saveCustomerSolutionDocuments', () => ({ resolveCustomerSolutionDocument: () => undefined, customerSolutionDocumentUrls: () => [], customerSolutionDocumentName: () => '', downloadCustomerSolutionDocument: vi.fn() }))
vi.mock('@/components/DeliveryArtifact/uploadDeliveryFile', () => ({ uploadDeliveryFile: vi.fn() }))
vi.mock('@/views/pms/engineering/solution/saveGeneratedSolutionHtml', () => ({ prepareGeneratedSolutionHtml: vi.fn() }))

const apps: { unmount: () => void }[] = []
afterEach(() => { apps.splice(0).forEach(app => app.unmount()); vi.clearAllMocks() })
const effective = {
  projectId: 202,
  revision: { ref: { entity: { tenantId: 1, ownerModule: 'SOL', entityType: 'requirementAnalysis', entityId: '300' }, revisionId: '400' }, revisionNo: 2, state: 'FROZEN', effective: true, version: 3 },
  values: { networkTopology: '<p>需求V2拓扑</p>', transmissionCurrentOptions: ['IPv6'], trafficNewConnections: '1000', trafficConcurrency: '10000', trafficThroughput: '1Gbps', projectBackground: 'V2背景' }
}
const snapshot = { sourceRevisionRef: effective.revision.ref, sourceRevisionNo: 2, sourceRevisionVersion: 3, values: effective.values }
const settle = async () => { for (let index = 0; index < 8; index++) { await Promise.resolve(); await nextTick() } }
const render = (readOnly: boolean, remark?: string, sectionData?: string) => {
  const value = ref<any>({ id: 41, projectId: 202, name: '方案', topology: JSON.stringify({ toBeChanged: 'no' }), remark, sectionData })
  const saved = vi.fn()
  const view = mount(defineComponent({ setup: () => () => h(Chapters, { modelValue: value.value, 'onUpdate:modelValue': (next: any) => { value.value = next }, readOnly, onSave: saved }) }), {}, { Editor: passthrough, ElRadioGroup: passthrough, ElRadio: passthrough, ElCheckboxGroup: passthrough, ElCheckbox: passthrough, ElInput: passthrough, ElUpload: passthrough, ElTable: passthrough, ElTableColumn: defineComponent({ setup: () => () => h('span') }) })
  apps.push(view.app)
  return { ...view, value, saved }
}
const find = (node: TestNode, label: string): TestNode | undefined => node.type === 'button' && textOf(node) === label ? node : node.children.map(child => find(child, label)).find(Boolean)

it('renders frozen requirement references in approval without fetching current workspace', async () => {
  api.workspace.mockResolvedValue({ currentEffective: { ...effective, values: { networkTopology: '后来的V3' } } })
  const view = render(true, JSON.stringify({ hasCustomerPlan: false }), JSON.stringify({ requirementReference: snapshot }))
  await settle()
  expect(textOf(view.root)).toContain('传输现状：IPv6')
  expect(textOf(view.root)).toContain('1000 / 并发 10000 / 吞吐 1Gbps')
  expect(textOf(view.root)).toContain('需求V2拓扑')
  expect(textOf(view.root)).not.toContain('后来的V3')
  expect(api.workspace).not.toHaveBeenCalled()
  expect(JSON.parse(view.value.value.sectionData).requirementReference).toEqual(snapshot)
})

it('identifies legacy readonly versions without a reference instead of claiming missing topology', async () => {
  const view = render(true, JSON.stringify({ archive: '已批准历史内容' }))
  await settle()
  expect(textOf(view.root)).toContain('本方案未保存需求分析引用快照')
  expect(textOf(view.root)).not.toContain('需求分析暂无网络拓扑')
  expect(api.workspace).not.toHaveBeenCalled()
})

it('saves the effective completed revision in chapter data and retains other chapter and remark fields', async () => {
  api.workspace.mockResolvedValue({ currentEffective: effective, draft: { ...effective, values: { networkTopology: '未完成草稿' } } })
  const view = render(false, JSON.stringify({ hasCustomerPlan: false, customRetained: '保留', archive: '原内容' }), JSON.stringify({ retainedChapter: { text: '已填章节' } }))
  await settle()
  const remarkBeforeSave = view.value.value.remark
  testEventHandler(find(view.root, '保存草稿')!, 'onClick')()
  await settle()
  const reference = JSON.parse(view.value.value.sectionData).requirementReference
  expect(reference).toMatchObject({ sourceRevisionRef: effective.revision.ref, sourceRevisionNo: 2, sourceRevisionVersion: 3, values: { networkTopology: '<p>需求V2拓扑</p>' } })
  expect(JSON.parse(view.value.value.sectionData).retainedChapter).toEqual({ text: '已填章节' })
  expect(view.value.value.remark).toBe(remarkBeforeSave)
  expect(JSON.parse(view.value.value.remark).customRetained).toBe('保留')
  expect(view.saved).toHaveBeenCalledTimes(1)
})

it('preserves draft preview and records its actual state without calling it effective', async () => {
  api.workspace.mockResolvedValue({ currentEffective: null, draft: { ...effective, revision: { ...effective.revision, state: 'DRAFT', effective: false } } })
  const view = render(false)
  await settle()
  testEventHandler(find(view.root, '生成方案（引用前序数据）')!, 'onClick')()
  await settle()
  expect(JSON.parse(view.value.value.sectionData || '{}').requirementReference).toMatchObject({ sourceRevisionState: 'DRAFT', sourceEffective: false, sourceRevisionRef: effective.revision.ref })
  expect(textOf(view.root)).toContain('需求V2拓扑')
})

it('retains a long topology snapshot without filling the bounded remark column', async () => {
  const networkTopology = `<p>${'完整网络拓扑内容'.repeat(500)}</p>`
  api.workspace.mockResolvedValue({ currentEffective: { ...effective, values: { ...effective.values, networkTopology } } })
  const view = render(false)
  await settle()
  const remarkBeforeSave = view.value.value.remark
  testEventHandler(find(view.root, '保存草稿')!, 'onClick')()
  await settle()
  expect(JSON.parse(view.value.value.sectionData).requirementReference.values.networkTopology).toBe(networkTopology)
  expect(view.value.value.remark).toBe(remarkBeforeSave)
  expect(view.saved).toHaveBeenCalledTimes(1)
})
