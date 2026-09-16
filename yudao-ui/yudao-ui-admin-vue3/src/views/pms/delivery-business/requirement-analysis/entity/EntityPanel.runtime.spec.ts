import { defineComponent, h, nextTick } from 'vue'
import { beforeEach, expect, it, vi } from 'vitest'
import Panel from './EntityPanel.vue'
import * as api from '@/api/pms/engineering/requirement-analysis/entity'
import { mount, passthrough, tableColumn, textOf } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
import type { TestNode } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
vi.mock('@/api/pms/engineering/requirement-analysis/entity', () => ({ workspace: vi.fn(), read: vi.fn(), create: vi.fn(), copy: vi.fn(), complete: vi.fn() }))
vi.mock('vue-router', () => ({ onBeforeRouteLeave: vi.fn() }))
vi.mock('@vueuse/core', () => ({ useWindowSize: () => ({ width: { value: 1280 } }) }))
vi.mock('./RevisionDrawer.vue', () => ({ default: { render: () => null } }))
vi.mock('./CompareDrawer.vue', () => ({ default: { render: () => null } }))
vi.mock('./EntityForm.vue', () => ({ default: defineComponent({ props: ['detail'], setup(props, { expose }) { expose({ isSaving: () => false, discardChanges: () => true }); return () => h('div', `revision:${props.detail.revision.ref.revisionId}`) } }) }))
const flush = async () => { for (let i = 0; i < 8; i++) { await Promise.resolve(); await nextTick() } }
const findButton = (root: TestNode, text: string): TestNode | undefined => root.type === 'button' && textOf(root).includes(text) ? root : root.children.map(child => findButton(child,text)).find(Boolean)
const render = (props: any = { project: { id: 7 } }) => mount(defineComponent({ setup: () => () => h(Panel,props) }), {}, { ElSkeleton: passthrough, ElTable: passthrough, ElTableColumn: tableColumn, ElDescriptions: passthrough, ElDescriptionsItem: passthrough, ElInput: passthrough })
const view = () => ({ projectId: '7', revision: { ref: { entity: { entityId: '20' }, revisionId: '31' }, revisionNo: 2, state: 'DRAFT', version: 6, effective: false }, extensionValueVersion: 0, allowedActions: ['PATCH_FORM','COMPLETE'], values: {}, fieldCatalog: [], attachments: [] }) as any
beforeEach(() => {
  vi.clearAllMocks()
  vi.stubGlobal('window', { addEventListener: vi.fn(), removeEventListener: vi.fn() })
  const values = new Map<string,string>()
  vi.stubGlobal('sessionStorage', { getItem: (key: string) => values.get(key) ?? null, setItem: (key: string,value: string) => values.set(key,value), removeItem: (key: string) => values.delete(key) })
})
it('opens current draft from workspace without reading old preparation detail', async () => {
  vi.mocked(api.workspace).mockResolvedValue({ projectId: '7', draft: view(), currentEffective: null, allowedActions: [] })
  const mounted = render(); await flush()
  expect(textOf(mounted.root)).toContain('revision:31'); expect(api.read).not.toHaveBeenCalled()
  expect(findButton(mounted.root,'完成并冻结')).toBeDefined()
  mounted.app.unmount()
})
it('loads explicit historical revision and exposes copy only when owner permits it', async () => {
  const frozen = { ...view(), revision: { ...view().revision, ref: { entity: { entityId: '20' }, revisionId: '30' }, state: 'FROZEN' }, allowedActions: ['CREATE_DRAFT'] }
  vi.mocked(api.workspace).mockResolvedValue({ projectId: '7', draft: null, currentEffective: view(), allowedActions: [] })
  vi.mocked(api.read).mockResolvedValue(frozen)
  const mounted = render({ project: { id: 7 }, revisionId: '30' }); await flush()
  expect(api.read).toHaveBeenCalledWith('30'); expect(textOf(mounted.root)).toContain('revision:30')
  await (findButton(mounted.root,'从查看版本创建草稿')!.props!.onClick as Function)(); await flush()
  expect(api.copy).toHaveBeenCalledWith(frozen.revision,expect.any(String),undefined)
  mounted.app.unmount()
})
it('creates first draft only through the new endpoint', async () => {
  vi.mocked(api.workspace).mockResolvedValue({ projectId: '7', draft: null, currentEffective: null, allowedActions: ['CREATE_INITIAL_DRAFT'] })
  const mounted = render(); await flush()
  await (findButton(mounted.root,'创建需求分析草稿')!.props!.onClick as Function)(); await flush()
  expect(api.create).toHaveBeenCalledWith(7,expect.any(String),undefined)
  mounted.app.unmount()
})
