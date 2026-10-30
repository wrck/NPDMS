vi.mock('@/config/axios', () => ({ default: { get: vi.fn(), post: vi.fn(), put: vi.fn() } }))
import { computed, defineComponent, h, inject, nextTick, provide, reactive, type ComputedRef } from 'vue'
import { expect, it, vi } from 'vitest'
import * as Members from '@/api/pms/project/unified-members'
import * as Managers from '@/api/pms/project/members'
import * as Projects from '@/api/pms/project/projects'
import ProjectMembersPanel from './ProjectMembersPanel.vue'
import { mount, passthrough, tableColumn, textOf, type TestNode } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

vi.mock('@/api/pms/project/projects', () => ({ getProject: vi.fn() }))
vi.mock('@/api/pms/project/members', () => ({
  getProjectManagers: vi.fn().mockResolvedValue({ members: [] })
}))
vi.mock('@/api/pms/project/unified-members', () => ({
  MEMBER_ROLE: { PROJECT_MANAGER: 'PROJECT_MANAGER', SERVICE_MANAGER: 'SERVICE_MANAGER' },
  memberRoleOptions: [
    { value: 'END_USER', label: '最终用户', permission: 'pms:project-team:create' },
    { value: 'PROJECT_MANAGER', label: '项目经理', permission: 'pms:project:assign' },
    { value: 'SERVICE_MANAGER', label: '服务经理', permission: 'pms:project:assign' }
  ],
  logicalMemberRole: (role: string) => role,
  canAppointPrimaryProjectManager: vi.fn().mockResolvedValue(true),
  canMaintainServiceManagers: vi.fn().mockResolvedValue(true),
  getMemberPage: vi.fn().mockResolvedValue({ list: [], total: 0 })
}))
vi.mock('./OrdinaryMemberForm.vue', () => ({ default: { render: () => null } }))
vi.mock('@/views/system/user/components/UserSelect.vue', () => ({ default: { render: () => null } }))
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

it.each([
  [false, false, true],
  [true, true, true],
  [true, false, false]
])('requires a successor only when a bound project manager remains (%s)', async (anotherBoundManager, expected, appointmentAllowed) => {
  vi.mocked(Members.canAppointPrimaryProjectManager).mockResolvedValue(appointmentAllowed)
  const primaryUserId = '2112914651683598351'
  const row = { id: '2113118246009847875', userId: primaryUserId, memberRole: 'PROJECT_MANAGER', status: 'ACTIVE' }
  vi.mocked(Members.getMemberPage).mockResolvedValue({ list: [row], total: 1 } as never)
  vi.mocked(Managers.getProjectManagers).mockResolvedValue({ primaryUserId, members: [
    { userId: null, name: '未绑定历史经理' }, { userId: primaryUserId, name: '当前主责' },
    ...(anotherBoundManager ? [{ userId: '2112914651683598352', name: '保留经理' }] : [])
  ] } as never)
  vi.mocked(Projects.getProject).mockResolvedValue({ id: 191, version: 0, lifecycleStatus: 'ACTIVE' } as never)
  const table = defineComponent({
    props: { data: Array },
    setup(props, { slots }) {
      provide('member-rows', computed(() => props.data))
      return () => h('section', slots.default?.())
    }
  })
  const column = defineComponent({
    props: { label: String },
    setup(props, { slots }) {
      const data = inject<ComputedRef<unknown[]>>('member-rows')!
      return () => props.label === '操作'
        ? h('section', data.value.map(row => slots.default?.({ row }))) : null
    }
  })
  const dialog = defineComponent({
    props: { modelValue: Boolean },
    setup(props, { slots }) { return () => props.modelValue ? h('section', slots.default?.()) : null }
  })
  const { app, root } = mount(ProjectMembersPanel, { project: { id: 191, lifecycleStatus: 'ACTIVE' } }, {
    ElTable: table, ElTableColumn: column, ElDialog: dialog,
    ElSelect: passthrough, ElOption: passthrough, ElRadioGroup: passthrough,
    ElRadioButton: passthrough, ElInput: passthrough
  })
  for (let i = 0; i < 8; i++) { await Promise.resolve(); await nextTick() }
  const nodes = () => {
    const found: TestNode[] = []
    const visit = (node: TestNode) => { found.push(node); node.children.forEach(visit) }
    visit(root); return found
  }
  const remove = nodes().find(node => node.type === 'button' && textOf(node) === '移出项目')!
  expect(remove.props?.disabled).toBe(!appointmentAllowed)
  await (remove.props!.onClick as () => Promise<void>)()
  await nextTick()
  expect(nodes().some(node => node.props?.label === '接任主责项目经理')).toBe(expected)
  app.unmount()
})

it.each([
  [null, ['否', '否']],
  ['2112914651683598351', ['否', '是']]
])('labels only a bound project manager as primary (%s)', async (primaryUserId, expected) => {
  const rows = [
    { id: '2113118246009847875', userId: null, memberRole: 'PROJECT_MANAGER', status: 'ACTIVE' },
    { id: '2113118246009847876', userId: '2112914651683598351', memberRole: 'PROJECT_MANAGER', status: 'ACTIVE' }
  ]
  vi.mocked(Members.getMemberPage).mockResolvedValue({ list: rows, total: 2 } as never)
  vi.mocked(Managers.getProjectManagers).mockResolvedValue({ primaryUserId, members: [] } as never)
  const table = defineComponent({
    props: { data: Array },
    setup(props, { slots }) {
      provide('member-rows', computed(() => props.data))
      return () => h('section', slots.default?.())
    }
  })
  const column = defineComponent({
    props: { label: String },
    setup(props, { slots }) {
      const data = inject<ComputedRef<unknown[]>>('member-rows')!
      return () => props.label === '主责'
        ? h('section', data.value.map(row => h('span', { 'data-testid': 'primary-label' }, slots.default?.({ row }))))
        : null
    }
  })
  const { app, root } = mount(ProjectMembersPanel, { project: { id: 191, lifecycleStatus: 'ACTIVE' } }, {
    ElTable: table, ElTableColumn: column, ElDialog: tableColumn,
    ElSelect: passthrough, ElOption: passthrough, ElRadioGroup: passthrough,
    ElRadioButton: passthrough, ElInput: passthrough
  })
  for (let i = 0; i < 8; i++) { await Promise.resolve(); await nextTick() }
  const labels: string[] = []
  const visit = (node: TestNode) => {
    if (node.props?.['data-testid'] === 'primary-label') labels.push(textOf(node))
    node.children.forEach(visit)
  }
  visit(root)
  expect(labels).toEqual(expected)
  app.unmount()
})

it('permits service-manager row maintenance independently of primary PM appointment', async () => {
  vi.mocked(Members.canAppointPrimaryProjectManager).mockResolvedValueOnce(false)
  vi.mocked(Members.canMaintainServiceManagers).mockResolvedValueOnce(true)
  vi.mocked(Members.getMemberPage).mockResolvedValueOnce({ list: [], total: 0 } as never)
  vi.mocked(Managers.getProjectManagers).mockResolvedValueOnce({ members: [] } as never)
  const page = mount(ProjectMembersPanel, { project: { id: 191, lifecycleStatus: 'ACTIVE' } }, {
    ElTable: passthrough, ElTableColumn: tableColumn, ElDialog: tableColumn,
    ElSelect: passthrough, ElOption: passthrough, ElRadioGroup: passthrough,
    ElRadioButton: passthrough, ElInput: passthrough
  })
  try {
    for (let i = 0; i < 8; i++) { await Promise.resolve(); await nextTick() }
    const state = (page.vm as any).$.setupState
    expect(state.canAppointPrimary).toBe(false)
    expect(state.canMaintain({ memberRole: 'SERVICE_MANAGER' })).toBe(true)
  } finally { page.app.unmount() }
})
