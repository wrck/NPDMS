vi.mock('@/config/axios', () => ({ default: { get: vi.fn(), post: vi.fn(), put: vi.fn() } }))
import { defineComponent, h, inject, provide } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as Members from '@/api/pms/project/unified-members'
import * as Managers from '@/api/pms/project/members'
import ProjectMembersPanel from './ProjectMembersPanel.vue'
import { mount, textOf, passthrough } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

vi.mock('@/api/pms/project/unified-members', async original => ({
  ...await original<typeof Members>(), getMemberPage: vi.fn(),
  canAppointPrimaryProjectManager: vi.fn().mockResolvedValue(false),
  canMaintainServiceManagers: vi.fn().mockResolvedValue(false)
}))
vi.mock('@/api/pms/project/members', () => ({ getProjectManagers: vi.fn() }))
vi.mock('@/utils/permission', () => ({ checkPermi: vi.fn(() => true) }))
const table = defineComponent({ props: ['data'], setup(props, { slots }) {
  provide('member-history-test-rows', () => props.data || [])
  return () => h('table', slots.default?.())
} })
const column = defineComponent({ setup(_, { slots }) {
  const rows = inject<() => Members.MemberRecord[]>('member-history-test-rows', () => [])
  return () => h('column', rows().flatMap(row => slots.default?.({ row }) || []))
} })
const dialog = defineComponent({ props: ['modelValue'], setup(props, { slots }) {
  return () => h('dialog', props.modelValue ? slots.default?.() : [])
} })
const record = (confirmed: boolean, userId: number | null = 40): Members.MemberRecord => ({
  id: 12, projectId: 9, userId, memberName: '历史人员', memberCode: 'z01010', memberRole: 'PROJECT_MANAGER',
  mobile: '原联系电话', email: 'interval@example.test', authorizationConfirmed: confirmed,
  effectiveFrom: new Date('2020-01-01'), status: 'ACTIVE'
})
const setup = (row: Members.MemberRecord) => {
  vi.mocked(Members.getMemberPage).mockResolvedValue({ list: [row], total: 1 })
  return mount(ProjectMembersPanel, { project: { id: 9, version: 3, lifecycleStatus: 'ACTIVE' } }, {
    ElTable: table, ElTableColumn: column, ElDialog: dialog, ElRadioGroup: passthrough,
    ElRadioButton: passthrough, ElSelect: passthrough, ElOption: passthrough, ElInput: passthrough, ElText: passthrough
  })
}
describe('project member history authorization presentation', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(Managers.getProjectManagers).mockResolvedValue({ projectId: 9, version: 3, primaryUserId: 1, assignmentStatus: 'ASSIGNED', changed: false, members: [] })
  })
  it('unconfirmed effective history shows its interval facts with no edit, remove or rejoin action', async () => {
    const { root, app } = setup(record(false))
    await vi.waitFor(() => expect(textOf(root)).toContain('历史人员'))
    const text = textOf(root)
    expect(text).toContain('z01010'); expect(text).toContain('原联系电话'); expect(text).toContain('interval@example.test')
    expect(text).toContain('未确认授权'); expect(text).toContain('只读')
    expect(text).not.toContain('编辑'); expect(text).not.toContain('移出项目'); expect(text).not.toContain('重新加入')
    app.unmount()
  })
  it('an unlinked historical person remains readable without selecting a fabricated platform user', async () => {
    const { root, app } = setup(record(false, null))
    await vi.waitFor(() => expect(textOf(root)).toContain('历史人员'))
    expect(textOf(root)).toContain('只读'); expect(textOf(root)).not.toContain('编辑')
    app.unmount()
  })
  it('confirmed normal members retain the existing maintenance actions', async () => {
    const { root, app } = setup(record(true))
    await vi.waitFor(() => expect(textOf(root)).toContain('历史人员'))
    expect(textOf(root)).toContain('编辑'); expect(textOf(root)).toContain('移出项目')
    app.unmount()
  })
  it('keeps accountless END_USER maintenance without granting authorization', async () => {
    const row = { ...record(false, null), memberRole: 'END_USER' }
    const { root, app } = setup(row)
    await vi.waitFor(() => expect(textOf(root)).toContain('历史人员'))
    expect(textOf(root)).toContain('编辑')
    expect(textOf(root)).toContain('移出项目')
    expect(row.authorizationConfirmed).toBe(false)
    app.unmount()
  })
  it('does not treat migrated role 60 as a maintainable END_USER', async () => {
    const { root, app } = setup({ ...record(false, null), memberRole: '60' })
    await vi.waitFor(() => expect(textOf(root)).toContain('历史人员'))
    expect(textOf(root)).not.toContain('编辑')
    expect(textOf(root)).not.toContain('移出项目')
    expect(textOf(root)).not.toContain('重新加入')
    app.unmount()
  })
})
