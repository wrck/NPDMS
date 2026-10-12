import { mount, flushPromises } from '@vue/test-utils'
import { afterEach, expect, it, vi } from 'vitest'
import ElementPlus from 'element-plus'
import Panel from './ProjectBaseInfo.vue'
import request from '@/config/axios'
import { getProjectContractRelations } from '@/api/pms/commerce'

vi.mock('@/config/axios', () => ({ default: { get: vi.fn() } }))
vi.mock('@/api/pms/commerce', () => ({ getProjectContractRelations: vi.fn() }))
vi.mock('@/api/pms/project/unified-members', () => ({ getMemberPage: vi.fn(async () => ({ list: [], total: 0 })) }))
vi.mock('@/utils/permission', () => ({ checkPermi: () => true }))
vi.mock('@/utils/dict', () => ({ DICT_TYPE: {}, getDictLabel: (_: unknown, value: string) => value }))
vi.mock('./ProjectAssociatedDataPanel.vue', () => ({ default: { template: '<div>回款计划</div>' } }))
let wrapper: ReturnType<typeof mount> | undefined
afterEach(() => { wrapper?.unmount(); wrapper = undefined; vi.resetAllMocks() })
const overview = { contracts: [{ id: 11, contractNo: 'C1' }], orders: [], executionOrders: [] }
const history = { id: 20, projectId: 10, contractId: 12, contractNo: 'C2', contractName: '原合同',
  status: 'HISTORICAL', effectiveFrom: '2021-04-14 15:10:56', createTime: '2021-04-14 15:10:56' }
function mountPanel(id = 10) {
  wrapper = mount(Panel, { props: { project: { id, projectCode: 'P1' } as any }, global: { plugins: [ElementPlus] } })
  return wrapper
}
it('shows current and migrated relationships together in the formal contract area', async () => {
  vi.mocked(request.get).mockResolvedValue(overview)
  vi.mocked(getProjectContractRelations).mockResolvedValue({ list: [history] as any, total: 1 })
  mountPanel(); await flushPromises()
  const section = wrapper!.findAll('section').find(item => item.text().includes('合同与执行单'))!
  expect(section.text()).toContain('C1'); expect(section.text()).toContain('C2')
  expect(section.text()).toContain('历史关联'); expect(section.text()).toContain('2021-04-14')
  expect(wrapper!.findAll('h3').map(item => item.text())).not.toContain('历史合同关联')
  expect(getProjectContractRelations).toHaveBeenCalledWith(10, { pageNo: 1, pageSize: 100 })
})
it('does not duplicate a contract already displayed in the formal overview', async () => {
  vi.mocked(request.get).mockResolvedValue(overview)
  vi.mocked(getProjectContractRelations).mockResolvedValue({ list: [{ ...history, contractId: 11, contractNo: 'C1', status: 'ACTIVE' }] as any, total: 1 })
  mountPanel(); await flushPromises()
  expect(wrapper!.findAll('.source-record')).toHaveLength(1)
  expect(wrapper!.text()).toContain('有效关联')
})
it('rejects a late relationship response from a previous project', async () => {
  vi.mocked(request.get).mockResolvedValue(overview)
  let finish: (value: any) => void = () => {}
  vi.mocked(getProjectContractRelations).mockImplementationOnce(() => new Promise(resolve => { finish = resolve }))
    .mockResolvedValueOnce({ list: [], total: 0 })
  mountPanel(); await flushPromises()
  await wrapper!.setProps({ project: { id: 30, projectCode: 'P3' } as any }); await flushPromises()
  finish({ list: [history], total: 1 }); await flushPromises()
  expect(wrapper!.text()).not.toContain('C2')
})
