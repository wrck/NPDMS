import { nextTick } from 'vue'
import { beforeEach, expect, it, vi } from 'vitest'
import Host from './BusinessEntityHost.vue'
import request from '@/config/axios'
import { mount, passthrough, textOf } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
import type { TestNode } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
vi.mock('@/utils/auth', () => ({ getCurrentUserId: () => 0, getTenantId: () => 7, getVisitTenantId: () => undefined }))
vi.mock('@/config/axios', () => ({ default: { get: vi.fn(), post: vi.fn() } }))
vi.mock('@/components/BusinessView/registry', () => ({ resolveStandaloneBusinessEntityView: () => undefined }))
vi.mock('@/api/pms/project/projects', () => ({ getProjectPage: vi.fn() }))
vi.mock('@/components/PmsEntitySelect/index.vue', () => ({ default: { render: () => null } }))
vi.mock('./BusinessEntityList.vue', async () => {
 const { defineComponent, h } = await import('vue')
 return { default: defineComponent({ props: ['rows'], emits: ['open'], setup: (props, { emit }) => () => h('button', { onClick: () => emit('open', props.rows[0]) }, 'open-native-root') }) }
})
vi.mock('./BusinessEntityForm.vue', async () => { const { defineComponent, h } = await import('vue'); return { default: defineComponent({ setup: (_, { slots }) => () => h('div', slots.extra?.()) }) } })
vi.mock('./DeliveryPanel.vue', async () => { const { defineComponent, h } = await import('vue'); return { default: defineComponent({ props: ['ownerModule', 'entityType', 'entityId'], setup: props => () => h('div', `actual-material-panel:${props.ownerModule}:${props.entityType}:${props.entityId}`) }) } })
vi.mock('./ApprovalPanel.vue', () => ({ default: { render: () => null } }))
vi.mock('./ContentHistoryPanel.vue', () => ({ default: { render: () => null } }))
const flush = async () => { for (let i = 0; i < 14; i++) { await Promise.resolve(); await nextTick() } }
const openButton = (root: TestNode): TestNode | undefined => root.type === 'button' && textOf(root).includes('open-native-root') ? root : root.children.map(openButton).find(Boolean)
beforeEach(() => vi.clearAllMocks())
for (const entityType of ['archiveDocument', 'completionCertificate', 'deliverableChecklist', 'acceptanceActivity', 'satisfactionCollectionTask']) {
 it(`mounts declared ${entityType} material panel on the actual root after controlled read`, async () => {
  const row = { ref: { tenantId: 7, ownerModule: 'ACC', entityType, entityId: 91 }, available: true, fieldValues: { status: 2 }, concurrencyBasis: 4 }
  vi.mocked(request.get).mockImplementation(async (query: any) => query.url.includes('/data') ? row : ({ ownerModule: 'ACC', entityType, title: 'Native', viewCode: entityType, fields: [], operations: [], capabilities: [{ type: 'DELIVERY', enabled: true }] }))
  vi.mocked(request.post).mockResolvedValue({ members: [row], completeness: 'COMPLETE' })
  const mounted = mount(Host, { ownerModule: 'ACC', entityType }, { ContentWrap: passthrough, ElAlert: passthrough, ElDescriptions: passthrough, ElDescriptionsItem: passthrough, ElPageHeader: passthrough, ElFormItem: passthrough })
  await flush(); expect(textOf(mounted.root)).not.toContain('actual-material-panel')
  await (openButton(mounted.root)!.props!.onClick as Function)(); await flush()
  expect(textOf(mounted.root)).toContain(`actual-material-panel:ACC:${entityType}:91`)
  mounted.app.unmount()
 })
}
