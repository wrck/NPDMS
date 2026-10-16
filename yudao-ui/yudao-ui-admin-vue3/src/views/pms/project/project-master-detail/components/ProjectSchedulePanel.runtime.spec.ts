import { beforeEach, expect, it, vi } from 'vitest'
import { defineComponent, nextTick } from 'vue'
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
const nativeContext = vi.hoisted(() => ({ current: undefined as any }))
vi.mock('@/components/ProjectBusiness/native/nativeBusinessContext', () => ({
  useNativeBusiness: () => nativeContext.current,
  useNativeBusinessApi: (_kind: string, original: unknown) => original
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
  nativeContext.current = undefined
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

it('reads the associated effective plan only once when task and list IDs use different scalar types', async () => {
  nativeContext.current = {
    initialEntityId: () => '7',
    can: () => false,
    Form: defineComponent({ setup: () => () => null })
  }
  vi.mocked(PlanApi.getStagePlanBatch).mockImplementation(async id =>
    String(id) === '7' ? structuredClone(effective) : draft())
  const app = render()
  try {
    await flush()
    expect(app.state.batch.id).toBe(7)
    expect(app.state.editable).toBe(false)
    expect(PlanApi.getStagePlanBatch).toHaveBeenCalledTimes(1)
    expect(String(vi.mocked(PlanApi.getStagePlanBatch).mock.calls[0][0])).toBe('7')
  } finally { app.app.unmount() }
})

it('reads selected and effective plan details while independent duration and overdue requests are pending', async () => {
  let resolveDuration!: (value: any) => void, resolveOverdue!: (value: any) => void
  vi.mocked(DurationApi.getByProjectId).mockReturnValue(new Promise(resolve => { resolveDuration = resolve }))
  vi.mocked(PlanApi.getOverdueStages).mockReturnValue(new Promise(resolve => { resolveOverdue = resolve }))
  const app = render()
  try {
    await flush()
    expect(PlanApi.getOverdueStages).toHaveBeenCalledWith(1)
    expect(PlanApi.getStagePlanBatch).toHaveBeenCalledWith(8)
    expect(PlanApi.getStagePlanBatch).toHaveBeenCalledWith(7)
    expect(app.state.batch).toBeUndefined()
    expect(app.state.loading).toBe(true)
    resolveDuration({ currentRevision: { revisionId: 2, durationDays: 31 } })
    resolveOverdue([])
    await flush()
    expect(app.state.batch.id).toBe(8)
    expect(app.state.tightSchedule).toBe(true)
    expect(app.state.loading).toBe(false)
  } finally { app.app.unmount() }
})

it('loads distinct selected and effective versions concurrently', async () => {
  let resolveSelected!: (value: any) => void
  vi.mocked(PlanApi.getStagePlanBatch).mockImplementation(id => id === 8
    ? new Promise(resolve => { resolveSelected = resolve })
    : Promise.resolve(structuredClone(effective)))
  const app = render()
  try {
    await flush()
    expect(PlanApi.getStagePlanBatch).toHaveBeenCalledWith(7)
    expect(app.state.batch).toBeUndefined()
    resolveSelected(draft()); await flush()
    expect(app.state.batch.id).toBe(8)
    expect(app.state.currentEffective.id).toBe(7)
    expect(app.state.tightSchedule).toBe(true)
  } finally { app.app.unmount() }
})

it('keeps overdue lookup failure visible while retaining the readable plan', async () => {
  vi.mocked(PlanApi.getOverdueStages).mockRejectedValue(new Error('超期查询失败'))
  const app = render()
  try {
    await flush()
    expect(app.state.batch.id).toBe(8)
    expect(app.state.overdueError).toBe(true)
    expect(app.state.errorText).toBe('')
    expect(app.state.loading).toBe(false)
  } finally { app.app.unmount() }
})

it('does not publish plan data when a required duration read is forbidden', async () => {
  vi.mocked(DurationApi.getByProjectId).mockRejectedValue(new Error('403'))
  const app = render()
  try {
    await flush()
    expect(app.state.batch).toBeUndefined()
    expect(app.state.batches).toEqual([])
    expect(app.state.errorText).toBe('施工计划加载失败，请重试。')
    expect(app.state.loading).toBe(false)
  } finally { app.app.unmount() }
})

it('ignores a superseded read after a newer refresh has completed', async () => {
  let resolveOld!: (value: any) => void
  vi.mocked(DurationApi.getByProjectId).mockReturnValueOnce(new Promise(resolve => { resolveOld = resolve }))
  const app = render()
  try {
    await flush()
    await app.state.load(7)
    expect(app.state.batch.id).toBe(7)
    resolveOld({ currentRevision: { revisionId: 99, durationDays: 999 } })
    await flush()
    expect(app.state.batch.id).toBe(7)
    expect(app.state.durationPlan.currentRevision.revisionId).toBe(2)
    expect(app.state.loading).toBe(false)
    expect(app.state.errorText).toBe('')
  } finally { app.app.unmount() }
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
    // 首次提交也必须校验阶段区间与任务安排
    app.state.batch.remark = '现场协调调整'
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

it('submits the first plan without a reason and keeps reason entry out of the date editor', async () => {
  const app = render()
  try {
    await flush()
    app.state.batch.adjustmentReasonRequired = false
    app.state.batch.remark = ''
    app.state.saved = JSON.stringify(app.state.batch)
    app.state.approverId = 9
    vi.mocked(PlanApi.submitStagePlanBatch).mockResolvedValue(draft() as any)
    await app.state.submit()
    expect(PlanApi.submitStagePlanBatch).toHaveBeenCalledWith(8, 9, undefined)
  } finally { app.app.unmount() }
})

it('allows submission within the baseline when an advisory suggested date has passed', async () => {
  vi.mocked(PlanApi.getStagePlanBatch).mockImplementation(async (id) => {
    const value = id === 7 ? structuredClone(effective) : draft()
    return { ...value, items: value.items.map((item) => ({ ...item, suggestedEnd: '2026-06-08' })) }
  })
  const app = render()
  try {
    await flush()
    app.state.approverId = 9
    vi.mocked(PlanApi.submitStagePlanBatch).mockResolvedValue(draft() as any)
    await app.state.submit()
    expect(PlanApi.submitStagePlanBatch).toHaveBeenCalledWith(8, 9, undefined)
  } finally { app.app.unmount() }
})

it('collects a fresh adjustment reason in the submission dialog without making dates dirty', async () => {
  const app = render()
  try {
    await flush()
    app.state.batch.adjustmentReasonRequired = true
    app.state.saved = JSON.stringify(app.state.batch)
    app.state.approverId = 9
    await app.state.submit()
    expect(PlanApi.submitStagePlanBatch).not.toHaveBeenCalled()
    expect(feedback.warning).toHaveBeenCalledWith('请填写调整理由')
    app.state.adjustmentReason = '  现场实施窗口调整  '
    expect(app.state.dirty).toBe(false)
    vi.mocked(PlanApi.submitStagePlanBatch).mockResolvedValue(draft() as any)
    await app.state.submit()
    expect(PlanApi.submitStagePlanBatch).toHaveBeenCalledWith(8, 9, '现场实施窗口调整')
  } finally { app.app.unmount() }
})

it('auto-fits violating task dates into the stage window when stage dates change', async () => {
  const app = render()
  try {
    await flush()
    // 任务日期落在阶段区间之外（整体早于区间），阶段计划时间更新后应立即自动适配进区间并保持天数
    app.state.batch.tasks[0].planStart = '2026-09-01'
    app.state.batch.tasks[0].planEnd = '2026-09-08'
    app.state.batch.items[0].planStart = '2026-10-05'
    await flush()
    expect(app.state.batch.tasks[0].planStart).toBe('2026-10-05')
    expect(app.state.batch.tasks[0].planEnd).toBe('2026-10-12')
  } finally {
    app.app.unmount()
  }
})
