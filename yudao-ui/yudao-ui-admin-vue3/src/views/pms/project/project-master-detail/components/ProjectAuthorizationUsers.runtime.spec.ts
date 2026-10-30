import { beforeEach, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import request from '@/config/axios'
import Panel from './ProjectAuthorizationPanel.vue'
import { pmsUserSelectionSource } from '@/api/pms/platform/user-selection'
import { mount, passthrough, tableColumn, type TestNode } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

const api = vi.hoisted(() => ({ active: vi.fn(), page: vi.fn(), create: vi.fn(), revoke: vi.fn() }))
vi.mock('@/config/axios', () => ({ default: { get: vi.fn() } }))
vi.mock('@/api/system/user', () => ({ getSimpleUserList: api.active }))
vi.mock('@/api/pms/project/projects', () => ({ getProjectAuthorizationPage: api.page,
  createProjectAuthorization: api.create, revokeProjectAuthorization: api.revoke }))
vi.mock('@/utils/dict', () => ({ DICT_TYPE: {}, getStrDictOptions: () => [] }))
vi.mock('@/utils/formatTime', () => ({ formatDate: () => '' }))
vi.mock('@vueuse/core', async () => { const { ref } = await import('vue'); return { useMediaQuery: () => ref(false) } })
const flush = async () => { for (let i = 0; i < 16; i++) { await Promise.resolve(); await nextTick() } }
const nodes = (root: TestNode): TestNode[] => [root, ...root.children.flatMap(nodes)]
const render = (source?: ReturnType<typeof pmsUserSelectionSource>) => mount(Panel, {
  projectId: 84575, ...(source ? { userSource: source } : {})
}, { ElSelect: passthrough, ElOption: passthrough, ElTable: passthrough, ElTableColumn: tableColumn,
  DictTag: passthrough, ElDatePicker: passthrough, ElInput: passthrough })

beforeEach(() => {
  vi.clearAllMocks()
  api.active.mockResolvedValue([{ id: '8', nickname: '既有人员' }])
  api.page.mockResolvedValue({ list: [], total: 0 })
  vi.mocked(request.get).mockResolvedValue({ list: [], total: 0 })
})

it('connects both current authorization pickers to email search without granting or querying the broad directory', async () => {
  const view = render(pmsUserSelectionSource('project-authorization:84575'))
  try {
    await flush()
    const pickers = nodes(view.root).filter(node => typeof node.props?.['remote-method'] === 'function')
    expect(pickers).toHaveLength(2)
    const user = { id: '2115387679367467008', nickname: '张三' }
    vi.mocked(request.get).mockResolvedValue({ list: [user], total: 1 })
    for (const picker of pickers) {
      vi.mocked(request.get).mockClear()
      ;(picker.props!['remote-method'] as (keyword: string) => void)('zhangsan')
      await vi.waitFor(() => expect(request.get).toHaveBeenCalledWith({
        url: '/api/v1/pms/user-selections', params: { keyword: 'zhangsan', pageNo: 1, pageSize: 20 }
      }))
      await flush()
    }
    ;(pickers[1].props!['onUpdate:modelValue'] as (id: string) => void)(user.id)
    await flush()
    expect((view.vm as any).$.setupState.createForm.subjectUserId).toBe(user.id)
    expect(api.create).not.toHaveBeenCalled()
    expect(api.revoke).not.toHaveBeenCalled()
    expect(api.active).not.toHaveBeenCalled()
  } finally { view.app.unmount() }
})

it('keeps the omitted-source legacy contract on the original enabled directory', async () => {
  const view = render()
  try {
    await flush()
    expect(nodes(view.root).filter(node => typeof node.props?.['remote-method'] === 'function')).toHaveLength(0)
    ;(nodes(view.root).find(node => node.props?.['filter-method'])!.props!.onVisibleChange as (visible: boolean) => void)(true)
    await flush()
    expect(api.active).toHaveBeenCalled()
    expect(request.get).not.toHaveBeenCalled()
    expect(api.create).not.toHaveBeenCalled()
    expect(api.revoke).not.toHaveBeenCalled()
  } finally { view.app.unmount() }
})

it('retains a current selection when its authorized directory fails without broad fallback', async () => {
  const view = render(pmsUserSelectionSource('project-authorization:84575'))
  try {
    await flush()
    const picker = nodes(view.root).find(node => typeof node.props?.['remote-method'] === 'function')
    expect(picker).toBeDefined()
    vi.mocked(request.get).mockResolvedValue({ list: [{ id: '2115387679367467008', nickname: '既有选中' }], total: 1 })
    ;(view.vm as any).$.setupState.query.subjectUserId = '2115387679367467008'
    await flush()
    vi.mocked(request.get).mockRejectedValueOnce(new Error('403'))
    ;(picker!.props!['remote-method'] as (keyword: string) => void)('outside')
    await vi.waitFor(() => expect(request.get).toHaveBeenCalledWith({
      url: '/api/v1/pms/user-selections', params: { keyword: 'outside', pageNo: 1, pageSize: 20 }
    }))
    await flush()
    expect((view.vm as any).$.setupState.query.subjectUserId).toBe('2115387679367467008')
    expect(api.active).not.toHaveBeenCalled()
    expect(api.create).not.toHaveBeenCalled()
    expect(api.revoke).not.toHaveBeenCalled()
  } finally { view.app.unmount() }
})
