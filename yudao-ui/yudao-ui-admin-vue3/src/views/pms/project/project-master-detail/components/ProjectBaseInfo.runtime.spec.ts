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

it('does not substitute execution-order parties for the explicit project party facts', async () => {
  api.get.mockResolvedValue({ contracts: [], orders: [], executionOrders: [
    { id: 1, executionNo: 'EX-1', finalCustomerName: '执行单客户', agentName: '执行单代理' }
  ] })
  api.getMemberPage.mockResolvedValue({ list: [], total: 0 })
  const m = mount(Panel, { project: { id: 151, marketName: '市场甲', parties: [] } })
  try {
    for (let i = 0; i < 10; i++) await nextTick()
    const state = (m.vm as any).$.setupState
    expect(state.party('FINAL_CUSTOMER')).toBe('—')
    expect(state.party('AGENT')).toBe('—')
  } finally { m.app.unmount() }
})

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

it('maps legacy signing/category meanings without truncating managers or choosing a single source order', async () => {
  api.get.mockResolvedValue({ contracts: [], executionOrders: [], orders: [
    { id: 1, orderNo: 'SO-1', orderCreateTime: '2026-10-01' },
    { id: 2, orderNo: 'SO-2', orderCreateTime: '2026-10-02' }
  ] })
  api.getMemberPage.mockResolvedValue({ total: 3, list: [
    { userId: 1, memberRole: 'PROJECT_MANAGER', memberName: '主责经理' },
    { userId: 2, memberRole: 'PROJECT_MANAGER', memberName: '经理乙' },
    { userId: 3, memberRole: 'PROJECT_MANAGER', memberName: '经理丙' }
  ] })
  const m = mount(Panel, { project: { id: 151, signingMethod: 'DIRECT', projectCategory: 'ENGINEERING',
    managerId: 1, managerName: '主责经理' } }, { ElDescriptions: passthrough, ElDescriptionsItem: passthrough })
  try {
    for (let i = 0; i < 10; i++) await nextTick()
    const fields = (m.vm as any).$.setupState.baseFields
    const value = (label: string) => fields.find((field: { label: string }) => field.label === label)?.value
    expect(value('项目类别')).toBe('直签')
    expect(value('项目类型')).toBe('工程类')
    expect(value('主责项目经理')).toBe('主责经理')
    expect(value('其他项目经理')).toBe('经理乙、经理丙')
    expect(value('订单创建时间')).toContain('SO-1')
    expect(value('订单创建时间')).toContain('SO-2')
    expect(value('结构深度')).toBeUndefined()
  } finally { m.app.unmount() }
})

it.each([true, false])('shows supplementary sections only for administrators: %s', async (admin) => {
  api.checkRole.mockReturnValue(admin)
  api.get.mockResolvedValue({ contracts: [], orders: [], executionOrders: [] })
  api.getMemberPage.mockResolvedValue({ total: 0, list: [] })
  const m = mount(Panel, { project: { id: 151, customerName: '客户甲' } })
  try {
    for (let i = 0; i < 10; i++) await nextTick()
    const text = textOf(m.root)
    expect(text.includes('合同与执行单')).toBe(admin)
    expect(text.includes('补充信息')).toBe(admin)
    for (const title of ['项目档案', '组织与人员', '客户与参与方']) {
      expect(text).not.toContain(title)
    }
    expect(text).toContain('客户项目名称')
  } finally {
    m.app.unmount()
    api.checkRole.mockReturnValue(true)
  }
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
