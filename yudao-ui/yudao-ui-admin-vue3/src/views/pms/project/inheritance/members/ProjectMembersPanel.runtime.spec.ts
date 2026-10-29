vi.mock('@/config/axios', () => ({ default: { get: vi.fn(), post: vi.fn(), put: vi.fn() } }))
import { defineComponent, h, nextTick, reactive } from 'vue'
import { expect, it, vi } from 'vitest'
import * as Members from '@/api/pms/project/unified-members'
import ProjectMembersPanel from './ProjectMembersPanel.vue'
import { mount, passthrough, tableColumn } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

vi.mock('@/api/pms/project/projects', () => ({ getProject: vi.fn() }))
vi.mock('@/api/pms/project/members', () => ({
  getProjectManagers: vi.fn().mockResolvedValue({ members: [] })
}))
vi.mock('@/api/pms/project/unified-members', () => ({
  MEMBER_ROLE: { PROJECT_MANAGER: 'PROJECT_MANAGER', SERVICE_MANAGER: 'SERVICE_MANAGER' },
  memberRoleOptions: [{ value: 'END_USER', label: '最终用户', permission: 'pms:project-team:create' }],
  logicalMemberRole: (role: string) => role,
  getMemberPage: vi.fn().mockResolvedValue({ list: [], total: 0 })
}))
vi.mock('./OrdinaryMemberForm.vue', () => ({ default: { render: () => null } }))
vi.mock('@/utils/permission', () => ({ checkPermi: () => true }))
vi.mock('@vueuse/core', () => ({ useMediaQuery: () => false }))

it('queries the entry default role and restores all roles when switching to assignment', async () => {
  const props = reactive<{ initialRole?: 'END_USER' }>({ initialRole: 'END_USER' })
  const host = defineComponent({ setup: () => () => h(ProjectMembersPanel, {
    project: { id: 191, lifecycleStatus: 'ACTIVE' }, ...props
  }) })
  const { app } = mount(host, {}, {
    ElTable: passthrough, ElTableColumn: tableColumn, ElDialog: tableColumn,
    ElSelect: passthrough, ElOption: passthrough, ElRadioGroup: passthrough,
    ElRadioButton: passthrough, ElInput: passthrough
  })
  await nextTick()
  expect(Members.getMemberPage).toHaveBeenLastCalledWith(191,
    expect.objectContaining({ role: 'END_USER', pageNo: 1 }))
  props.initialRole = undefined
  await nextTick()
  expect(Members.getMemberPage).toHaveBeenLastCalledWith(191,
    expect.objectContaining({ role: undefined, pageNo: 1 }))
  app.unmount()
})
