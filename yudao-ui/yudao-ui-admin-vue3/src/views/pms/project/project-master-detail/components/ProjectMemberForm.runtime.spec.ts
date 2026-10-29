vi.mock('@/config/axios', () => ({ default: { get: vi.fn(), post: vi.fn(), put: vi.fn() } }))
import { defineComponent, h, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as Members from '@/api/pms/project/members'
import * as Projects from '@/api/pms/project/projects'
import * as Depts from '@/api/system/dept'
import { systemUserSelection } from '@/api/system/user/selection'
import ProjectMemberForm from './ProjectMemberForm.vue'
import {
  mount,
  textOf,
  type TestNode
} from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

vi.mock('@/api/pms/project/members', () => ({
  getManagerCandidates: vi.fn(),
  updateMembers: vi.fn()
}))
vi.mock('@/api/pms/project/projects', () => ({
  getServiceManagerCandidates: vi.fn(),
  getProjectSites: vi.fn()
}))
vi.mock('@/api/system/dept', () => ({ getSimpleDeptList: vi.fn() }))

const control = defineComponent({
  inheritAttrs: false,
  setup(_, { attrs, slots, expose }) {
    expose({ scrollbarRef: { wrapRef: { scrollHeight: 300, clientHeight: 100 } } })
    return () => h('control', attrs, [slots.default?.(), slots.footer?.()])
  }
})
const find = (node: TestNode, predicate: (item: TestNode) => boolean): TestNode | undefined => {
  if (predicate(node)) return node
  for (const child of node.children) {
    const result = find(child, predicate)
    if (result) return result
  }
}
const flush = async () => { for (let i = 0; i < 8; i++) { await nextTick(); await Promise.resolve() } }
const set = async (root: TestNode, label: string, value: unknown) => {
  let node = find(root, (item) => item.props?.['aria-label'] === label)
  expect(node).toBeTruthy()
  if (typeof node!.props!['onUpdate:modelValue'] !== 'function') {
    node = find(node!, item => typeof item.props?.['onUpdate:modelValue'] === 'function')
    ;(node!.props!.onVisibleChange as (open: boolean) => void)(true)
    await flush()
  }
  ;(node!.props!['onUpdate:modelValue'] as (value: unknown) => void)(value)
  await flush()
}
const submit = async (root: TestNode) => {
  const form = find(root, (item) => Boolean(item.props?.onSubmit))
  await (form!.props!.onSubmit as (event: unknown) => Promise<void>)({ preventDefault() {} })
  await nextTick()
}
const setup = () =>
  mount(
    ProjectMemberForm,
    {
      project: { id: 9, lifecycleStatus: 'ACTIVE', departmentId: 5 },
      current: {
        projectId: 9,
        version: 3,
        primaryUserId: 1,
        assignmentStatus: 'ASSIGNED',
        members: [{ assignmentId: 10, userId: 1, name: '原经理' }]
      }
    },
    { ElSelect: control, ElOption: control, ElInput: control, ElCheckbox: control }
  )

describe('PM-01 project member form runtime', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(Members.getManagerCandidates).mockResolvedValue({
      list: [{ userId: 2, username: 'new', nickname: '新经理' }],
      total: 1
    })
    vi.mocked(Projects.getProjectSites).mockResolvedValue([])
    vi.mocked(Projects.getServiceManagerCandidates).mockResolvedValue({ list: [{ userId: 7, username: 'service', nickname: '服务经理', companyId: 1, departmentId: 5, departmentCode: 'OFFICE', departmentName: '办事处' }], total: 1 })
    vi.mocked(Depts.getSimpleDeptList).mockResolvedValue([
      { id: 5, code: 'OFFICE', name: '办事处' }
    ] as Depts.DeptVO[])
    vi.mocked(Members.updateMembers).mockResolvedValue({
      projectManagers: {} as Members.ProjectManagers
    })
  })

  it('requires an explicit successor when removing the primary but adding another manager', async () => {
    const { root, app } = setup()
    await set(root, '增补项目经理', [2])
    await set(root, '移除项目经理', [1])
    await set(root, '调整原因', '工作交接')
    await submit(root)
    expect(textOf(root)).toContain('请从保留或新增经理中选择当前主责')
    expect(Members.updateMembers).not.toHaveBeenCalled()
    await set(root, '当前主责项目经理', 2)
    await submit(root)
    expect(Members.updateMembers).toHaveBeenCalledWith(
      9,
      expect.objectContaining({ addUserIds: [2], removeUserIds: [1], primaryUserId: 2 }),
      3,
      expect.any(String)
    )
    app.unmount()
  })

  it('reuses the same intent key after a failed attempt and changes it for edited input', async () => {
    const { root, app } = setup()
    vi.mocked(Members.updateMembers).mockRejectedValue(new Error('network timeout'))
    await set(root, '调整原因', '首次原因')
    await submit(root)
    await submit(root)
    const calls = vi.mocked(Members.updateMembers).mock.calls
    expect(calls[0][3]).toBe(calls[1][3])
    await set(root, '调整原因', '修订原因')
    await submit(root)
    expect(calls[2][3]).not.toBe(calls[0][3])
    app.unmount()
  })

  it('submits both manager roles in one request and preserves omitted project members', async () => {
    const { root, app } = setup()
    await set(root, '同时指派服务经理', true)
    await nextTick()
    await nextTick()
    await set(root, '服务经理', 7)
    await set(root, '调整原因', '联合指派')
    await submit(root)
    expect(Members.updateMembers).toHaveBeenCalledTimes(1)
    expect(Members.updateMembers).toHaveBeenCalledWith(
      9,
      expect.objectContaining({
        addUserIds: [],
        removeUserIds: [],
        primaryUserId: 1,
        serviceManager: {
          levelCode: 'L1',
          managerId: 7,
          siteId: undefined,
          assignmentType: 'PRIMARY',
          departmentId: 5,
          departmentCode: 'OFFICE'
        }
      }),
      3,
      expect.any(String)
    )
    app.unmount()
  })
  it('uses project-scoped candidates and keeps existing managers disabled while paging', async () => {
    vi.mocked(Members.getManagerCandidates).mockResolvedValueOnce({
      list: [{ userId: 1, username: 'old', nickname: '原经理' }], total: 2
    }).mockResolvedValueOnce({ list: [{ userId: 2, username: 'new', nickname: '新经理' }], total: 2 })
    const active = vi.spyOn(systemUserSelection, 'active')
    const { root, app } = setup()
    const wrapper = find(root, item => item.props?.['aria-label'] === '增补项目经理')!
    const select = find(wrapper, item => typeof item.props?.['remote-method'] === 'function')!
    ;(select.props!['remote-method'] as (keyword: string) => void)('经理')
    await vi.waitFor(() => expect(Members.getManagerCandidates).toHaveBeenCalled())
    await flush()
    expect(Members.getManagerCandidates).toHaveBeenLastCalledWith(9, '经理', 1)
    expect(find(wrapper, item => item.props?.value === 1)?.props?.disabled).toBe(true)
    ;(select.props!.onVisibleChange as (opened: boolean) => void)(true)
    ;(select.props!.onPopupScroll as (value: { scrollTop: number }) => void)({ scrollTop: 200 })
    await flush()
    expect(Members.getManagerCandidates).toHaveBeenLastCalledWith(9, '经理', 2)
    expect(active).not.toHaveBeenCalled()
    active.mockRestore()
    app.unmount()
  })

  it('does not supplement project candidates from the user directory when the owner API fails', async () => {
    vi.mocked(Members.getManagerCandidates).mockReset().mockRejectedValue(new Error('denied'))
    const active = vi.spyOn(systemUserSelection, 'active')
    const { root, app } = setup()
    const wrapper = find(root, item => item.props?.['aria-label'] === '增补项目经理')!
    const select = find(wrapper, item => typeof item.props?.['remote-method'] === 'function')!
    ;(select.props!.onVisibleChange as (opened: boolean) => void)(true)
    await flush()
    expect(textOf(wrapper)).toContain('人员候选加载失败')
    expect(active).not.toHaveBeenCalled()
    expect(find(wrapper, item => item.props?.value === 2)).toBeUndefined()
    active.mockRestore()
    app.unmount()
  })

})
