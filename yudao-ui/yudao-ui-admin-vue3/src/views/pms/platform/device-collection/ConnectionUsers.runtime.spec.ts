import { beforeEach, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import request from '@/config/axios'
import Panel from './ConnectionPanel.vue'
import { mount, passthrough, tableColumn, type TestNode } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

const api = vi.hoisted(() => ({ active: vi.fn(), connections: vi.fn(), grants: vi.fn(), grant: vi.fn(), save: vi.fn() }))
vi.mock('@/config/axios', () => ({ default: { get: vi.fn() } }))
vi.mock('@/api/system/user', () => ({ getSimpleUserList: api.active }))
vi.mock('@/api/pms/platform/deviceCollection', () => ({ connections: api.connections, grants: api.grants,
  grantConnection: api.grant, saveConnection: api.save }))
vi.mock('@/components/ProjectDeviceSelect/index.vue', () => ({ default: { render: () => null } }))
vi.mock('@/utils/formatTime', () => ({ dateFormatter: () => '' }))
const flush = async () => { for (let i = 0; i < 16; i++) { await Promise.resolve(); await nextTick() } }
const nodes = (root: TestNode): TestNode[] => [root, ...root.children.flatMap(nodes)]
beforeEach(() => {
  vi.clearAllMocks()
  api.active.mockResolvedValue([{ id: '8', nickname: '既有授权人' }])
  api.connections.mockResolvedValue([])
  api.grants.mockResolvedValue([])
  vi.mocked(request.get).mockResolvedValue({ list: [], total: 0 })
})

it('searches email from the real connection grantee picker and keeps authorization a separate explicit action', async () => {
  const view = mount(Panel, { projectId: '84575' }, {
    ElSelect: passthrough, ElOption: passthrough, ElTable: passthrough, ElTableColumn: tableColumn,
    ElDatePicker: passthrough, ElInput: passthrough, ElInputNumber: passthrough
  })
  try {
    await flush()
    const state = (view.vm as any).$.setupState
    await state.openGrants({ id: '100', name: '测试连接' })
    await flush()
    const picker = nodes(view.root).find(node => typeof node.props?.['remote-method'] === 'function')
    expect(picker).toBeDefined()
    const user = { id: '2115387679367467008', nickname: '张三' }
    vi.mocked(request.get).mockResolvedValue({ list: [user], total: 1 })
    ;(picker!.props!['remote-method'] as (keyword: string) => void)('zhangsan')
    await vi.waitFor(() => expect(request.get).toHaveBeenCalledWith({
      url: '/api/v1/pms/user-selections', params: { keyword: 'zhangsan', pageNo: 1, pageSize: 20 }
    }))
    await flush()
    ;(picker!.props!['onUpdate:modelValue'] as (id: string) => void)(user.id)
    await flush()
    expect(state.grantForm.userId).toBe(user.id)
    expect(state.users).toEqual([{ id: '8', nickname: '既有授权人' }])
    expect(api.active).toHaveBeenCalledTimes(1)
    expect(api.grant).not.toHaveBeenCalled()
    expect(api.save).not.toHaveBeenCalled()
  } finally { view.app.unmount() }
})
