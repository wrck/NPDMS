import { defineComponent, h, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as Members from '@/api/pms/project/unified-members'
import OrdinaryMemberForm from './OrdinaryMemberForm.vue'
import { mount, textOf, type TestNode } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

vi.mock('@/config/axios', () => ({ default: { get: vi.fn(), post: vi.fn(), put: vi.fn() } }))
vi.mock('@/api/pms/project/unified-members', async importOriginal => ({
  ...await importOriginal<typeof Members>(), getMemberCandidates: vi.fn(), saveMember: vi.fn(), canAppointPrimaryProjectManager: vi.fn(async () => true), canMaintainServiceManagers: vi.fn(async () => true)
}))
vi.mock('@/utils/permission', () => ({ checkPermi: vi.fn(() => true) }))
vi.mock('@/api/pms/project/projects', () => ({ getProjectSites: vi.fn(async () => []) }))
vi.mock('@/api/system/dept', () => ({ getSimpleDeptList: vi.fn(async () => [{ id: 25, code: 'OFFICE', name: '办事处' }]) }))
const control = defineComponent({ inheritAttrs: false, setup(_, { attrs, slots, expose }) {
  expose({ scrollbarRef: { wrapRef: { scrollHeight: 300, clientHeight: 100 } } })
  return () => h('control', attrs, [...(slots.default?.() || []), ...(slots.footer?.() || [])])
} })
const find = (node: TestNode, predicate: (item: TestNode) => boolean): TestNode | undefined => {
  if (predicate(node)) return node
  for (const child of node.children) { const found = find(child, predicate); if (found) return found }
}
const flush = async () => { for (let i = 0; i < 8; i++) { await nextTick(); await Promise.resolve() } }
const userControl = (root: TestNode) => {
  const wrapper = find(root, item => item.props?.['aria-label'] === '人员')!
  return find(wrapper, item => typeof item.props?.['remote-method'] === 'function')!
}
const set = async (root: TestNode, label: string, value: unknown) => {
  let node = find(root, item => item.props?.['aria-label'] === label)
  expect(node).toBeTruthy()
  if (label === '人员') {
    node = userControl(root)
    ;(node.props!.onVisibleChange as (open: boolean) => void)(true)
    await flush()
  }
  ;(node!.props!['onUpdate:modelValue'] as (value: unknown) => void)(value)
  if (label !== '人员' && node!.props!.onChange) (node!.props!.onChange as (value: unknown) => void)(value)
  await flush()
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
    vi.mocked(Members.canAppointPrimaryProjectManager).mockResolvedValue(true)
    vi.mocked(Members.canMaintainServiceManagers).mockResolvedValue(true)
    vi.mocked(Members.getMemberCandidates).mockReset().mockResolvedValue({ list: [{ id: 40, username: 'engineer', nickname: '普通人员' }], total: 1 })
    vi.mocked(Members.saveMember).mockResolvedValue({ projectId: 9, version: 4, assignmentId: 13, userId: 40, memberRole: 'TEAM_MEMBER', changed: true })
  })
  it('maintains external end users without querying system candidates', async () => {
    const { root, app } = setup({ defaultRole: 'END_USER', member: { ...currentMember,
      userId: null, memberRole: 'END_USER', memberName: '外部姓名', companyName: '客户单位',
      departmentName: '客户建设部', title: '原职务', mobile: '13800138000', email: 'a@example.test' } })
    await nextTick()
    expect(Members.getMemberCandidates).not.toHaveBeenCalled()
    expect(find(root, item => item.props?.['aria-label'] === '人员')).toBeUndefined()
    expect(find(root, item => item.props?.['aria-label'] === '部门')?.props?.modelValue).toBe('客户建设部')
    expect(find(root, item => item.props?.['aria-label'] === '职务')?.props?.modelValue).toBe('原职务')
    await set(root, '姓名', '修改姓名')
    await set(root, '职务', ' 副主任 ')
    await submit(root)
    expect(Members.saveMember).toHaveBeenCalledWith(9, 12, expect.objectContaining({
      userId: null, memberRole: 'END_USER', externalPerson: { name: '修改姓名', companyName: '客户单位', department: '客户建设部', title: '副主任', mobile: '13800138000', email: 'a@example.test' }
    }), 3, expect.any(String))
    app.unmount()
  })
  it('requires the external name and rejoins from historical external details without selecting an account', async () => {
    const empty = setup({ defaultRole: 'END_USER' })
    await submit(empty.root)
    expect(Members.saveMember).not.toHaveBeenCalled()
    expect(Members.getMemberCandidates).not.toHaveBeenCalled()
    empty.app.unmount()
    const { root, app } = setup({ rejoin: true, member: { ...currentMember, userId: null,
      memberRole: 'END_USER', memberName: '历史姓名', departmentName: '历史部门', title: '历史职务', mobile: '13900139000', effectiveTo: new Date() } })
    await nextTick()
    await submit(root)
    expect(Members.getMemberCandidates).not.toHaveBeenCalled()
    expect(Members.saveMember).toHaveBeenCalledWith(9, undefined, expect.objectContaining({ userId: null,
      externalPerson: { name: '历史姓名', companyName: '', department: '历史部门', title: '历史职务', mobile: '13900139000', email: '' }
    }), 3, expect.any(String))
    app.unmount()
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
  it('resolves an existing member with equivalent string and numeric IDs without clearing the person', async () => {
    const { root, app } = setup({ member: { ...currentMember, userId: '40' } })
    await flush()
    await submit(root)
    expect(Members.saveMember).toHaveBeenCalledWith(9, 12,
      expect.objectContaining({ userId: 40, memberRole: 'TEAM_MEMBER' }), 3, expect.any(String))
    app.unmount()
  })
  it('cannot request primary appointment or maintain service managers when project role permission is denied', async () => {
    vi.mocked(Members.canAppointPrimaryProjectManager).mockResolvedValue(false)
    vi.mocked(Members.canMaintainServiceManagers).mockResolvedValue(false)
    const { root, app } = setup({ defaultRole: 'PROJECT_MANAGER' })
    await flush()
    const checkbox = find(root, item => item.props?.['aria-label'] === '设为当前角色主责')!
    expect(checkbox.props?.disabled).toBe(true)
    await set(root, '人员', 40)
    // Even a synthetic change to the disabled checkbox must not become a primary mutation.
    await set(root, '设为当前角色主责', true)
    await submit(root)
    expect(Members.saveMember).toHaveBeenCalledWith(9, undefined, expect.objectContaining({ primary: false }), 3, expect.any(String))
    vi.mocked(Members.saveMember).mockClear()
    await set(root, '项目角色', 'SERVICE_MANAGER')
    await submit(root)
    expect(Members.saveMember).not.toHaveBeenCalled()
    app.unmount()
  })
  it('allows service manager maintenance without granting primary project-manager appointment', async () => {
    vi.mocked(Members.canAppointPrimaryProjectManager).mockResolvedValue(false)
    vi.mocked(Members.canMaintainServiceManagers).mockResolvedValue(true)
    const { root, app } = setup({ defaultRole: 'SERVICE_MANAGER' })
    await flush()
    await set(root, '人员', 40)
    await set(root, '调整原因', '管理员维护')
    await submit(root)
    expect(Members.saveMember).toHaveBeenCalledWith(9, undefined,
      expect.objectContaining({ userId: 40, memberRole: 'SERVICE_MANAGER' }), 3, expect.any(String))
    app.unmount()
  })
  it.each([
    ['联系电话', '随便填写', '联系电话格式无效'],
    ['联系电话', '+()-', '联系电话格式无效'],
    ['联系电话', '0'.repeat(64), '联系电话格式无效'],
    ['联系电话', '121231', '联系电话格式无效'],
    ['联系电话', '12800138000', '联系电话格式无效'],
    ['邮箱', '000@1.1', '邮箱格式无效'],
    ['邮箱', 'a..b@example.com', '邮箱格式无效'],
    ['邮箱', 'invalid-email', '邮箱格式无效']
  ])('rejects malformed external %s without saving', async (label, value, hint) => {
    const { root, app } = setup({ defaultRole: 'END_USER' })
    await set(root, '姓名', '最终用户')
    await set(root, label, value)
    await submit(root)
    expect(Members.saveMember).not.toHaveBeenCalled()
    expect(find(root, item => item.props?.role === 'alert')?.props?.title).toBe(hint)
    app.unmount()
  })
  it.each(['', '+86 (571) 8888-1234', '0571-88881234', '13800138000', '+86 13900139000'])('accepts optional or international external phone %s', async mobile => {
    const { root, app } = setup({ defaultRole: 'END_USER' })
    await set(root, '姓名', '最终用户')
    await set(root, '联系电话', mobile)
    await submit(root)
    expect(Members.saveMember).toHaveBeenCalledWith(9, undefined,
      expect.objectContaining({ externalPerson: expect.objectContaining({ mobile, email: '' }) }), 3, expect.any(String))
    app.unmount()
  })
  it('service manager needs only role, person, primary and reason, and shows directory contacts', async () => {
    vi.mocked(Members.getMemberCandidates).mockResolvedValue({ list: [{ id: 40, username: 'service',
      nickname: '服务人员', mobile: '13800000000', email: 'service@example.test' }], total: 1 })
    const { root, app } = setup()
    await set(root, '项目角色', 'SERVICE_MANAGER')
    const select = userControl(root)
    await (select.props!['remote-method'] as (value: string) => Promise<void>)(''); await new Promise(resolve => setTimeout(resolve, 320)); await flush()
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
    const select = userControl(root)
    await (select.props!['remote-method'] as (value: string) => Promise<void>)(''); await new Promise(resolve => setTimeout(resolve, 320)); await flush()
    await nextTick()
    ;(select.props!.onVisibleChange as (opened: boolean) => void)(true)
    ;(select.props!.onPopupScroll as (value: { scrollTop: number }) => void)({ scrollTop: 200 })
    await flush()
    const retry = find(root, item => Boolean(item.props?.onClick) && textOf(item).includes('重新加载用户'))!
    await (retry.props!.onClick as () => Promise<void>)(); await flush()
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
    const select = userControl(root)
    await (select.props!['remote-method'] as (value: string) => Promise<void>)(''); await flush()
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
    const select = () => userControl(root)
    const pending = (select().props!['remote-method'] as (value: string) => Promise<void>)('')
    await vi.waitFor(() => expect(finish).toBeTypeOf('function'))
    await set(root, '项目角色', 'SALES_REPRESENTATIVE')
    await (select().props!['remote-method'] as (value: string) => Promise<void>)(''); await new Promise(resolve => setTimeout(resolve, 320)); await flush()
    finish({ list: [{ id: 40, username: 'old', nickname: '旧角色候选' }], total: 1 })
    await pending; await flush()
    expect(find(root, item => item.props?.label === 'sales-销售人员')).toBeTruthy()
    expect(find(root, item => item.props?.label === 'old-旧角色候选')).toBeUndefined()
    app.unmount()
  })
})
