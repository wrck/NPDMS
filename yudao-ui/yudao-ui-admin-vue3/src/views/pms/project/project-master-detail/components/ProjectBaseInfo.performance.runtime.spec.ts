import { beforeEach, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import Panel from './ProjectBaseInfo.vue'
import { mount, passthrough, textOf } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

const api = vi.hoisted(() => ({ get: vi.fn(), getMemberPage: vi.fn(), relations: vi.fn(), checkRole: vi.fn(() => true) }))
vi.mock('@/api/pms/commerce', () => ({ getProjectContractRelations: api.relations }))
vi.mock('./ProjectAssociatedDataPanel.vue', () => ({ default: { render: () => null } }))
beforeEach(() => { api.relations.mockReset().mockResolvedValue({ list: [], total: 0 }) })
vi.mock('@/config/axios', () => ({ default: { get: api.get } }))
vi.mock('@/api/pms/project/unified-members', () => ({ getMemberPage: api.getMemberPage }))
vi.mock('@/utils/permission', () => ({ checkPermi: () => true, checkRole: api.checkRole }))
vi.mock('@vueuse/core', () => ({ useMediaQuery: () => ({ value: false }) }))
vi.mock('@/utils/dict', () => ({ DICT_TYPE: { PMS_SIGNING_METHOD: 'signing', PMS_PROJECT_CATEGORY: 'category' },
  getDictLabel: (dict: string, value: string) => dict === 'signing' ? '直签' : dict === 'category' ? '工程类' : value }))

it('shows the overview even when relation lookup fails and keeps deduplicated contract numbers', async () => {
  api.get.mockResolvedValue({ contracts: [{ id: 1, contractNo: 'CT-1' }, { id: 2, contractNo: 'CT-2' }], orders: [], executionOrders: [] })
  api.getMemberPage.mockResolvedValue({ list: [], total: 0 })
  api.relations.mockRejectedValue(new Error('relation unavailable'))
  const m = mount(Panel, { project: { id: 151, contractNo: 'CT-1', marketName: '市场甲' } })
  try {
    await vi.waitFor(() => expect((m.vm as any).$.setupState.relationError).toContain('合同关联信息加载失败'))
    expect(textOf(m.root)).toContain('CT-1、CT-2')
    expect((m.vm as any).$.setupState.commerce.contracts).toHaveLength(2)
    expect((m.vm as any).$.setupState.error).toBe('')
  } finally { m.app.unmount() }
})

it('publishes overview facts before a pending relation lookup completes', async () => {
  let finish!: (value: any) => void
  api.relations.mockImplementation(() => new Promise(resolve => { finish = resolve }))
  api.get.mockResolvedValue({ contracts: [{ id: 1, contractNo: 'CT-READY' }], orders: [], executionOrders: [] })
  api.getMemberPage.mockResolvedValue({ list: [], total: 0 })
  const m = mount(Panel, { project: { id: 151, marketName: '市场甲' } })
  try {
    await vi.waitFor(() => expect(textOf(m.root)).toContain('CT-READY'))
    expect((m.vm as any).$.setupState.loading).toBe(false)
    finish({ list: [], total: 0 })
    await nextTick()
  } finally { m.app.unmount() }
})

it('renders base facts while the contract overview is pending and does not await unrelated requests', async () => {
  let finish!: (value: any) => void
  api.get.mockImplementation(() => new Promise(resolve => { finish = resolve }))
  api.relations.mockImplementation(() => new Promise(() => {}))
  api.getMemberPage.mockImplementation(() => new Promise(() => {}))
  const m = mount(Panel, { project: { id: 151, contractNo: 'REGISTERED', customerProjectName: '项目先显示', marketName: '市场甲' } })
  try {
    await nextTick()
    expect(textOf(m.root)).toContain('项目先显示')
    expect(textOf(m.root)).toContain('REGISTERED')
    expect(textOf(m.root)).toContain('合同信息加载中')
    finish({ contracts: [{ id: 1, contractNo: 'ASYNC-CONTRACT' }], orders: [], executionOrders: [] })
    await vi.waitFor(() => expect(textOf(m.root)).toContain('REGISTERED、ASYNC-CONTRACT'))
    expect(textOf(m.root)).not.toContain('合同信息加载中')
  } finally { m.app.unmount() }
})
