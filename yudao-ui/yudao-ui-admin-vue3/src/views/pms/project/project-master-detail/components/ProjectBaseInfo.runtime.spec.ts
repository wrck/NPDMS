import { expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import Panel from './ProjectBaseInfo.vue'
import { mount, passthrough } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

const api = vi.hoisted(() => ({ get: vi.fn(), getMemberPage: vi.fn() }))
vi.mock('@/config/axios', () => ({ default: { get: api.get } }))
vi.mock('@/api/pms/project/unified-members', () => ({ getMemberPage: api.getMemberPage }))
vi.mock('@/utils/permission', () => ({ checkPermi: () => true }))
vi.mock('@vueuse/core', () => ({ useMediaQuery: () => ({ value: false }) }))
vi.mock('@/utils/dict', () => ({ DICT_TYPE: { PMS_SIGNING_METHOD: 'signing', PMS_PROJECT_CATEGORY: 'category' },
  getDictLabel: (dict: string, value: string) => dict === 'signing' ? '直签' : dict === 'category' ? '工程类' : value }))

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
