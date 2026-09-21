import { beforeEach, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import Panel from './ProjectSchedulePanel.vue'
import * as PlanApi from '@/api/pms/engineering/stage-plan'
import * as DurationApi from '@/api/pms/engineering/construction-plan'
import * as MemberApi from '@/api/pms/project/unified-members'
import {
  mount,
  passthrough,
  tableColumn
} from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

const feedback = vi.hoisted(() => ({
  success: vi.fn(),
  warning: vi.fn(),
  error: vi.fn(),
  confirm: vi.fn()
}))
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => feedback }))
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
  onBeforeRouteLeave: vi.fn(),
  onBeforeRouteUpdate: vi.fn()
}))
vi.mock('@/utils/permission', () => ({ checkPermi: () => true }))
vi.mock('@/utils/dict', () => ({ DICT_TYPE: {}, getDictLabel: () => '直签' }))
vi.mock('@/api/pms/engineering/stage-plan', () => ({
  getStagePlanBatchPage: vi.fn(),
  getStagePlanBatch: vi.fn(),
  getOverdueStages: vi.fn(),
  updateStagePlanItems: vi.fn(),
  submitStagePlanBatch: vi.fn()
}))
vi.mock('@/api/pms/engineering/construction-plan', () => ({ getByProjectId: vi.fn() }))
vi.mock('@/api/pms/project/unified-members', () => ({
  getMemberPage: vi.fn(),
  logicalMemberRole: (role: string) =>
    role.startsWith('SERVICE_MANAGER') ? 'SERVICE_MANAGER' : role
}))
vi.mock('./ProjectDurationPanel.vue', () => ({
  default: { methods: { isDirty: () => false }, render: () => null }
}))
vi.mock('./SchedulePlanningTable.vue', () => ({ default: { render: () => null } }))

const effective = {
  id: 7,
  projectId: 1,
  status: 2,
  version: 1,
  durationRevisionId: 2,
  baselineStart: '2026-10-01',
  baselineEnd: '2026-10-31',
  items: [
    {
      id: 1,
      phaseId: 10,
      phaseCode: 'S',
      phaseName: '实施',
      planStart: '2026-10-01',
      planEnd: '2026-10-31'
    }
  ],
  tasks: []
}
const draft = () => ({
  ...effective,
  id: 8,
  status: 0,
  items: effective.items.map((item) => ({ ...item })),
  tasks: [
    {
      taskId: 20,
      stageCode: 'S',
      name: '安装',
      version: 3,
      planStart: '2026-10-01',
      planEnd: '2026-10-20'
    }
  ]
})
const flush = async () => {
  for (let i = 0; i < 14; i++) await nextTick()
}
const render = () => {
  const mounted = mount(
    Panel,
    { project: { id: 1 } },
    Object.fromEntries(
      [
        'ElSelect',
        'ElOption',
        'ElCollapse',
        'ElCollapseItem',
        'ElDialog',
        'ElProgress',
        'ElInput',
        'ElTable'
      ]
        .map((name) => [name, passthrough])
        .concat([['ElTableColumn', tableColumn]])
    )
  )
  return { ...mounted, state: (mounted.vm as any).$.setupState }
}
beforeEach(() => {
  vi.clearAllMocks()
  vi.stubGlobal('window', { addEventListener: vi.fn(), removeEventListener: vi.fn() })
  vi.mocked(DurationApi.getByProjectId).mockResolvedValue({
    currentRevision: { revisionId: 2, durationDays: 31 }
  } as any)
  vi.mocked(PlanApi.getStagePlanBatchPage).mockResolvedValue({
    list: [
      { id: 8, status: 0 },
      { id: 7, status: 2 }
    ],
    total: 2
  })
  vi.mocked(PlanApi.getStagePlanBatch).mockImplementation(async (id) =>
    id === 7 ? structuredClone(effective) : draft()
  )
  vi.mocked(PlanApi.getOverdueStages).mockResolvedValue([])
})

it('loads effective plan dates for warnings even when the page response has no date fields', async () => {
  const app = render()
  try {
    await flush()
    expect(app.state.tightSchedule).toBe(true)
    expect(app.state.batch.id).toBe(8)
  } finally {
    app.app.unmount()
  }
})

it.each([1, 2])(
  'renders status %s with numeric server timestamps and disables editing',
  async (status) => {
    vi.mocked(PlanApi.getStagePlanBatch).mockResolvedValue({
      ...draft(),
      status,
      submittedAt: 1789921357532,
      effectiveAt: status === 2 ? 1789921357532 : undefined
    })
    const app = render()
    try {
      await flush()
      expect(app.state.batch.status).toBe(status)
      expect(app.state.editable).toBe(false)
    } finally {
      app.app.unmount()
    }
  }
)

it('saves nested task dates with the batch version and retains unsaved input on API failure', async () => {
  const app = render()
  try {
    await flush()
    app.state.batch.tasks[0].planEnd = '2026-10-18'
    app.state.batch.remark = '协调现场资源'
    vi.mocked(PlanApi.updateStagePlanItems).mockRejectedValue(new Error('版本冲突'))
    await app.state.save()
    expect(PlanApi.updateStagePlanItems).toHaveBeenCalledWith(
      expect.objectContaining({
        id: 8,
        version: 1,
        tasks: [expect.objectContaining({ taskId: 20, planEnd: '2026-10-18', version: 3 })]
      })
    )
    expect(app.state.dirty).toBe(true)
    expect(app.state.batch.tasks[0].planEnd).toBe('2026-10-18')
  } finally {
    app.app.unmount()
  }
})

it('guards navigation without discarding changes when the user cancels', async () => {
  const app = render()
  try {
    await flush()
    app.state.batch.remark = '尚未保存'
    feedback.confirm.mockRejectedValue(new Error('cancel'))
    await app.state.selectBatch(7)
    expect(app.state.selectedId).toBe(8)
    expect(app.state.batch.remark).toBe('尚未保存')
  } finally {
    app.app.unmount()
  }
})

it('collects all current service-manager pages and deduplicates multiple responsibilities', async () => {
  vi.mocked(MemberApi.getMemberPage)
    .mockResolvedValueOnce({
      list: [{ userId: 9, memberName: '经理', memberRole: 'SERVICE_MANAGER_L1' }],
      total: 2
    } as any)
    .mockResolvedValueOnce({
      list: [{ userId: 9, memberName: '经理', memberRole: 'SERVICE_MANAGER_L2' }],
      total: 2
    } as any)
  const app = render()
  try {
    await flush()
    await app.state.openSubmit()
    expect(MemberApi.getMemberPage).toHaveBeenLastCalledWith(
      1,
      expect.objectContaining({ role: 'SERVICE_MANAGER', state: 'CURRENT', pageNo: 2 })
    )
    expect(app.state.approvers).toHaveLength(1)
    expect(app.state.approverId).toBe(9)
  } finally {
    app.app.unmount()
  }
})

it('prevents approval when a task remains outside its stage and keeps approved versions read-only', async () => {
  const app = render()
  try {
    await flush()
    app.state.batch.tasks[0].planEnd = '2026-11-01'
    app.state.saved = JSON.stringify(app.state.batch)
    app.state.approverId = 9
    await app.state.submit()
    expect(PlanApi.submitStagePlanBatch).not.toHaveBeenCalled()
    expect(feedback.warning).toHaveBeenCalledWith(expect.stringContaining('超出所属阶段'))
    await app.state.selectBatch(7)
    expect(app.state.editable).toBe(false)
  } finally {
    app.app.unmount()
  }
})
