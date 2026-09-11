import { defineComponent, h, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as Members from '@/api/pms/project/unified-members'
import OrdinaryMemberForm from './OrdinaryMemberForm.vue'
import { mount, textOf, type TestNode } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

vi.mock('@/config/axios', () => ({ default: { get: vi.fn(), post: vi.fn(), put: vi.fn() } }))
vi.mock('@/api/pms/project/unified-members', async importOriginal => ({
  ...await importOriginal<typeof Members>(), getMemberCandidates: vi.fn(), saveMember: vi.fn()
}))
vi.mock('@/utils/permission', () => ({ checkPermi: vi.fn(() => true) }))
vi.mock('@/api/pms/project/projects', () => ({ getProjectSites: vi.fn(async () => []) }))
vi.mock('@/api/system/dept', () => ({ getSimpleDeptList: vi.fn(async () => [{ id: 25, code: 'OFFICE', name: '办事处' }]) }))
const control = defineComponent({ inheritAttrs: false, setup(_, { attrs, slots }) {
  return () => h('control', attrs, [...(slots.default?.() || []), ...(slots.footer?.() || [])])
} })
const find = (node: TestNode, predicate: (item: TestNode) => boolean): TestNode | undefined => {
  if (predicate(node)) return node
  for (const child of node.children) { const found = find(child, predicate); if (found) return found }
}
const set = async (root: TestNode, label: string, value: unknown) => {
  const node = find(root, item => item.props?.['aria-label'] === label)
  expect(node).toBeTruthy()
  ;(node!.props!['onUpdate:modelValue'] as (value: unknown) => void)(value)
  if (node!.props!.onChange) (node!.props!.onChange as (value: unknown) => void)(value)
  await nextTick()
}
const submit = async (root: TestNode) => {
  const node = find(root, item => Boolean(item.props?.onSubmit))
  await (node!.props!.onSubmit as (event: unknown) => Promise<void>)({ preventDefault() {} })
  await nextTick()
}
const setup = (extra: Record<string, unknown> = {}) => mount(OrdinaryMemberForm,
  { project: { id: 9, version: 3, departmentId: 25 }, primaryUserId: 55, ...extra }, {
    ElSelect: control, ElOption: control, ElInput: control, ElCheckbox: control,
    ElDescriptions: control, ElDescriptionsItem: control
  })
const currentMember = { id: 12, projectId: 9, userId: 40, memberRole: 'TEAM_MEMBER', memberName: '原人员',
  responsibility: '原职责', remark: '原备注', status: 'ACTIVE' }

describe('unified ordinary member form', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(Members.getMemberCandidates).mockResolvedValue({ list: [{ id: 40, username: 'engineer', nickname: '普通人员' }], total: 1 })
    vi.mocked(Members.saveMember).mockResolvedValue({ projectId: 9, version: 4, assignmentId: 13, userId: 40, memberRole: 'TEAM_MEMBER', changed: true })
  })
  it('requires a chosen person and reason then posts ordinary member data only', async () => {
    const { root, app } = setup()
    await submit(root)
    expect(Members.saveMember).not.toHaveBeenCalled()
    await set(root, '人员', 40)
    await set(root, '调整原因', '现场支持')
    await set(root, '备注', '保留普通备注')
    await submit(root)
    expect(Members.saveMember).toHaveBeenCalledWith(9, undefined,
      { userId: 40, memberRole: 'TEAM_MEMBER', responsibility: '', remark: '保留普通备注', reason: '现场支持', primary: undefined, scope: undefined }, 3, expect.any(String))
    app.unmount()
  })
  it('service manager needs only role, person, primary and reason, and shows directory contacts', async () => {
    vi.mocked(Members.getMemberCandidates).mockResolvedValue({ list: [{ id: 40, username: 'service',
      nickname: '服务人员', mobile: '13800000000', email: 'service@example.test' }], total: 1 })
    const { root, app } = setup()
    await set(root, '项目角色', 'SERVICE_MANAGER')
    const select = find(root, item => item.props?.['aria-label'] === '人员')!
    await (select.props!['remote-method'] as (value: string) => Promise<void>)('')
    await set(root, '人员', 40)
    await set(root, '设为当前角色主责', true)
    await set(root, '调整原因', '统一角色加入')
    expect(find(root, item => item.props?.['aria-label'] === '邮箱')?.props?.['model-value']).toBe('service@example.test')
    expect(find(root, item => item.props?.['aria-label'] === '联系电话')?.props?.['model-value']).toBe('13800000000')
    expect(find(root, item => item.props?.['aria-label'] === '服务经理层级')).toBeUndefined()
    expect(find(root, item => item.props?.['aria-label'] === '实施站点')).toBeUndefined()
    await submit(root)
    expect(Members.saveMember).toHaveBeenCalledWith(9, undefined,
      expect.objectContaining({ userId: 40, memberRole: 'SERVICE_MANAGER', primary: true }), 3, expect.any(String))
    expect(vi.mocked(Members.saveMember).mock.calls[0][2].scope).toBeUndefined()
    app.unmount()
  })
  it('allows an empty reason without inventing one', async () => {
    const { root, app } = setup()
    await set(root, '人员', 40)
    await submit(root)
    expect(Members.saveMember).toHaveBeenCalledWith(9, undefined,
      expect.objectContaining({ reason: '' }), 3, expect.any(String))
    app.unmount()
  })
  it('preserves intent key on network retries and changes it when input changes', async () => {
    vi.mocked(Members.saveMember).mockRejectedValue(new Error('timeout'))
    const { root, app } = setup()
    await set(root, '人员', 40)
    await set(root, '调整原因', '加入')
    await submit(root)
    await submit(root)
    const calls = vi.mocked(Members.saveMember).mock.calls
    expect(calls[0][4]).toBe(calls[1][4])
    await set(root, '备注', '新备注')
    await submit(root)
    expect(calls[2][4]).not.toBe(calls[0][4])
    expect(textOf(root)).toContain('保存未成功')
    app.unmount()
  })
  it('validates the existing person before editing and keeps the interval ID in the update', async () => {
    const { root, app } = setup({ member: currentMember })
    await nextTick(); await nextTick()
    expect(Members.getMemberCandidates).toHaveBeenCalledWith(9, { pageNo: 1, pageSize: 1, userId: 40, projectRole: 'TEAM_MEMBER' })
    await set(root, '调整原因', '备注调整')
    await set(root, '备注', '新备注')
    await submit(root)
    expect(Members.saveMember).toHaveBeenCalledWith(9, 12,
      expect.objectContaining({ userId: 40, responsibility: '原职责', remark: '新备注' }), 3, expect.any(String))
    app.unmount()
  })
  it('rejoin creates a new interval and an unavailable former user cannot be submitted', async () => {
    const joined = setup({ member: currentMember, rejoin: true })
    await nextTick(); await nextTick()
    await set(joined.root, '调整原因', '重新加入')
    await submit(joined.root)
    expect(vi.mocked(Members.saveMember).mock.calls[0][1]).toBeUndefined()
    joined.app.unmount()
    vi.mocked(Members.saveMember).mockClear()
    vi.mocked(Members.getMemberCandidates).mockResolvedValue({ list: [], total: 0 })
    const unavailable = setup({ member: currentMember, rejoin: true })
    await nextTick(); await nextTick()
    expect(textOf(unavailable.root)).toContain('原人员当前不可选')
    await set(unavailable.root, '调整原因', '重新加入')
    await submit(unavailable.root)
    expect(Members.saveMember).not.toHaveBeenCalled()
    unavailable.app.unmount()
  })
  it('retries the failed candidate page instead of skipping it', async () => {
    vi.mocked(Members.getMemberCandidates).mockResolvedValueOnce({ list: [{ id: 40, username: 'one', nickname: '一' }], total: 2 })
      .mockRejectedValueOnce(new Error('network'))
      .mockResolvedValueOnce({ list: [{ id: 41, username: 'two', nickname: '二' }], total: 2 })
    const { root, app } = setup()
    const select = find(root, item => item.props?.['aria-label'] === '人员')!
    await (select.props!['remote-method'] as (value: string) => Promise<void>)('')
    await nextTick()
    const more = () => find(root, item => Boolean(item.props?.onClick) && textOf(item).includes('加载更多人员'))!
    await (more().props!.onClick as () => Promise<void>)(); await nextTick()
    await (more().props!.onClick as () => Promise<void>)(); await nextTick()
    expect(vi.mocked(Members.getMemberCandidates).mock.calls.map(call => call[1].pageNo)).toEqual([1, 2, 2])
    app.unmount()
  })
  it('switches candidate role in one form and clears the former selection', async () => {
    const { root, app } = setup()
    await set(root, '人员', 40)
    await set(root, '项目角色', 'SALES_REPRESENTATIVE')
    await set(root, '调整原因', '销售协同')
    await submit(root)
    expect(Members.saveMember).not.toHaveBeenCalled()
    const select = find(root, item => item.props?.['aria-label'] === '人员')!
    await (select.props!['remote-method'] as (value: string) => Promise<void>)('')
    expect(Members.getMemberCandidates).toHaveBeenLastCalledWith(9,
      expect.objectContaining({ projectRole: 'SALES_REPRESENTATIVE' }))
    await set(root, '人员', 40)
    await submit(root)
    expect(Members.saveMember).toHaveBeenCalledWith(9, undefined,
      expect.objectContaining({ memberRole: 'SALES_REPRESENTATIVE', userId: 40 }), 3, expect.any(String))
    app.unmount()
  })
  it('ignores a late candidate response from the former role', async () => {
    let finish!: (value: { list: Members.MemberCandidate[]; total: number }) => void
    vi.mocked(Members.getMemberCandidates).mockImplementationOnce(() => new Promise(resolve => { finish = resolve }))
      .mockResolvedValueOnce({ list: [{ id: 41, username: 'sales', nickname: '销售人员' }], total: 1 })
    const { root, app } = setup()
    const select = () => find(root, item => item.props?.['aria-label'] === '人员')!
    const pending = (select().props!['remote-method'] as (value: string) => Promise<void>)('')
    await set(root, '项目角色', 'SALES_REPRESENTATIVE')
    await (select().props!['remote-method'] as (value: string) => Promise<void>)('')
    finish({ list: [{ id: 40, username: 'old', nickname: '旧角色候选' }], total: 1 })
    await pending; await nextTick()
    expect(find(root, item => item.props?.label === '销售人员（sales）')).toBeTruthy()
    expect(find(root, item => item.props?.label === '旧角色候选（old）')).toBeUndefined()
    app.unmount()
  })
})
