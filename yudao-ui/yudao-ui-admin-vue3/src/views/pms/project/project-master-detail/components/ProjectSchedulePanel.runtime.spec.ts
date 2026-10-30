vi.mock('@/config/axios', () => ({ default: { get: vi.fn(), post: vi.fn(), put: vi.fn() } }))
import { beforeEach, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import Panel from './ProjectSchedulePanel.vue'
import * as PlanApi from '@/api/pms/engineering/stage-plan'
import * as DurationApi from '@/api/pms/engineering/construction-plan'
import * as MemberApi from '@/api/pms/project/unified-members'
import { stagePlanMenusForRoles } from '@/store/modules/stagePlanMenuVisibility'
import {
  mount,
  findByTestId,
  textOf,
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
const permission = vi.hoisted(() => ({ bpm: true, task: true, schedule: true }))
const businessBar = vi.hoisted(() => ({ embedded: false }))
vi.mock('@/components/BusinessView/businessActionBar', async () => {
  const { ref } = await import('vue')
  return { useBusinessActionBar: () => ({ barTarget: businessBar.embedded ? ref(null) : undefined }) }
})
vi.mock('@/utils/permission', () => ({ checkPermi: (codes: string[]) => codes.includes('bpm:process-instance:query') ? permission.bpm : codes.includes('bpm:task:query') ? permission.task : codes.includes('pms:imp-stage-plan:update') ? permission.schedule : true }))
vi.mock('@/utils/dict', () => ({ DICT_TYPE: {}, getDictLabel: () => '直签' }))
vi.mock('@/api/pms/engineering/stage-plan', () => ({
  getStagePlanBatchPage: vi.fn(),
  getStagePlanBatch: vi.fn(),
  getOverdueStages: vi.fn(),
  createStagePlanBatch: vi.fn(),
  autoEstimateStagePlanBatch: vi.fn(),
  updateStagePlanItems: vi.fn(),
  submitStagePlanBatch: vi.fn()
}))
vi.mock('@/api/pms/engineering/construction-plan', () => ({ getByProjectId: vi.fn(), getEntryReadiness: vi.fn() }))
vi.mock('@/api/pms/project/unified-members', () => ({
  getMemberPage: vi.fn(),
  logicalMemberRole: (role: string) =>
    role.startsWith('SERVICE_MANAGER') ? 'SERVICE_MANAGER' : role
}))
vi.mock('./ProjectDurationPanel.vue', async () => {
  const { defineComponent, h } = await import('vue')
  return { default: defineComponent({
    props: { readonly: Boolean }, methods: { isDirty: () => false },
    render() { return h('section', { 'data-testid': 'duration-panel', readonly: this.readonly }) }
  }) }
})
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
const render = (extraProps: Record<string, unknown> = {}) => {
  const mounted = mount(
    Panel,
    { project: { id: 1 }, ...extraProps },
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
  businessBar.embedded = false
  permission.bpm = true
  permission.task = true
  permission.schedule = true
  vi.clearAllMocks()
  vi.mocked(DurationApi.getEntryReadiness).mockResolvedValue({ durationWritable: true, scheduleWritable: true })
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

it.each([
  { readonly: true, allowedActions: ['QUERY', 'CREATE', 'UPDATE'] },
  { readonly: true, allowedActions: ['QUERY'] }
])('blocks every planning command and the nested duration editor for a read-only task (%j)', async props => {
  const app = render(props)
  try {
    await flush()
    expect(app.state.editable).toBe(false)
    await app.state.createDraft()
    await app.state.estimate()
    await app.state.save()
    await app.state.openSubmit()
    app.state.approverId = 99
    await app.state.submit()
    expect(PlanApi.createStagePlanBatch).not.toHaveBeenCalled()
    expect(PlanApi.autoEstimateStagePlanBatch).not.toHaveBeenCalled()
    expect(PlanApi.updateStagePlanItems).not.toHaveBeenCalled()
    expect(PlanApi.submitStagePlanBatch).not.toHaveBeenCalled()
    expect(MemberApi.getMemberPage).not.toHaveBeenCalled()
    expect(app.state.canWrite).toBe(false)
    expect(findByTestId(app.root, 'duration-panel')?.props?.readonly).toBe(true)
  } finally { app.app.unmount() }
})

it('retains the writable Owner workspace when a current PAGE task exposes its QUERY registration', async () => {
  const app = render({ readonly: false, allowedActions: ['QUERY'] })
  try {
    await flush()
    expect(app.state.canWrite).toBe(true)
    expect(app.state.editable).toBe(true)
    expect(findByTestId(app.root, 'duration-panel')?.props?.readonly).toBe(false)
  } finally { app.app.unmount() }
})

it('keeps independent duration entry available while the construction-plan task is not writable', async () => {
  vi.mocked(DurationApi.getEntryReadiness).mockResolvedValue({ durationWritable: true, scheduleWritable: false })
  const app = render()
  try {
    await flush()
    expect(app.state.canWrite).toBe(false)
    expect(app.state.editable).toBe(false)
    expect(findByTestId(app.root, 'duration-panel')?.props?.readonly).toBe(false)
    await app.state.createDraft()
    expect(PlanApi.createStagePlanBatch).not.toHaveBeenCalled()
  } finally { app.app.unmount() }
})

it('fails closed on an unavailable template qualification while retaining readable planning history', async () => {
  vi.mocked(DurationApi.getEntryReadiness).mockRejectedValue(new Error('unavailable'))
  const app = render()
  try {
    await flush()
    expect(app.state.batch.id).toBe(8)
    expect(app.state.canWrite).toBe(false)
    expect(findByTestId(app.root, 'duration-panel')?.props?.readonly).toBe(true)
    await app.state.createDraft()
    expect(PlanApi.createStagePlanBatch).not.toHaveBeenCalled()
  } finally { app.app.unmount() }
})

it('clears an earlier write grant when refreshing the same project fails', async () => {
  const app = render()
  try {
    await flush()
    expect(app.state.canWrite).toBe(true)
    vi.mocked(PlanApi.getStagePlanBatchPage).mockRejectedValueOnce(new Error('failed refresh'))
    await app.state.refresh()
    await flush()
    expect(app.state.canWrite).toBe(false)
    expect(findByTestId(app.root, 'duration-panel')?.props?.readonly).toBe(true)
  } finally { app.app.unmount() }
})

it.each([false, true])('only exposes its own refresh outside the unified action bar (embedded=%s)', async embedded => {
  businessBar.embedded = embedded
  const app = render()
  try {
    await flush()
    expect(Boolean(findByTestId(app.root, 'refresh-schedule'))).toBe(!embedded)
  } finally { app.app.unmount() }
})

it('reloads the selected plan from the unified refresh and refuses to overwrite unsaved dates', async () => {
  businessBar.embedded = true
  const app = render()
  try {
    await flush()
    vi.mocked(PlanApi.getStagePlanBatch).mockClear()
    vi.mocked(PlanApi.getStagePlanBatch).mockImplementation(async id => id === 7 ? structuredClone(effective) : { ...draft(), remark: '刷新后的数据' })
    await (app.vm as any).refresh()
    expect(PlanApi.getStagePlanBatch).toHaveBeenCalledWith(8)
    expect(app.state.batch.remark).toBe('刷新后的数据')
    app.state.batch.tasks[0].planEnd = '2026-10-19'
    vi.mocked(PlanApi.getStagePlanBatch).mockClear()
    await (app.vm as any).refresh()
    expect(PlanApi.getStagePlanBatch).not.toHaveBeenCalled()
    expect(app.state.batch.tasks[0].planEnd).toBe('2026-10-19')
  } finally { app.app.unmount() }
})

it.each(['bpm', 'task'] as const)('hides the BPM progress entry when the existing account lacks %s query permission', async missing => {
  permission[missing] = false
  vi.mocked(PlanApi.getStagePlanBatch).mockResolvedValue({ ...effective, bpmProcessInstanceId: 'approval-1' } as any)
  const app = render()
  try {
    await flush()
    expect(textOf(app.root)).not.toContain('查看审批进度')
    permission[missing] = true
    app.vm.$forceUpdate()
    await flush()
    expect(textOf(app.root)).toContain('查看审批进度')
  } finally { app.app.unmount() }
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

it('marks completed-date defaults for saving and keeps completed tasks out of stage auto fitting', async () => {
  const value = draft() as any
  value.tasks[0].planStart = undefined
  value.tasks[0].planEnd = undefined
  value.completedDates = { stages: [], tasks: [{ nodeId: 20, actualStart: '2026-09-28', actualEnd: '2026-09-30' }] }
  vi.mocked(PlanApi.getStagePlanBatch).mockImplementation(async id => id === 7 ? structuredClone(effective) : structuredClone(value))
  const app = render()
  try {
    await flush()
    expect(app.state.batch.tasks[0]).toMatchObject({ planStart: '2026-09-28', planEnd: '2026-09-30' })
    expect(app.state.dirty).toBe(true)
    app.state.batch.items[0].planStart = '2026-10-03'
    await flush()
    expect(app.state.batch.tasks[0].planStart).toBe('2026-09-28')
    vi.mocked(PlanApi.updateStagePlanItems).mockImplementation(async request => {
      value.tasks = JSON.parse(JSON.stringify(request.tasks))
      const items = JSON.parse(JSON.stringify(request.items))
      value.items = value.items.map((item: any) => ({ ...item, ...items.find((changed: any) => changed.id === item.id) }))
      return structuredClone(value)
    })
    await app.state.save()
    expect(PlanApi.updateStagePlanItems).toHaveBeenCalledWith(expect.objectContaining({ tasks: [expect.objectContaining({ planStart: '2026-09-28', planEnd: '2026-09-30' })] }))
    expect(JSON.parse(JSON.stringify(app.state.batch))).toEqual(JSON.parse(app.state.saved))
    expect(app.state.dirty).toBe(false)
  } finally { app.app.unmount() }
})

it('does not modify a view-only draft even when completion metadata is present', async () => {
  permission.schedule = false
  const value = draft() as any
  value.tasks[0].planStart = undefined
  value.tasks[0].planEnd = undefined
  value.completedDates = { stages: [], tasks: [{ nodeId: 20, actualStart: '2026-10-10', actualEnd: '2026-10-11' }] }
  vi.mocked(PlanApi.getStagePlanBatch).mockResolvedValue(value)
  const app = render()
  try {
    await flush()
    expect(app.state.batch.tasks[0].planStart).toBeUndefined()
    expect(app.state.batch.tasks[0].planEnd).toBeUndefined()
    expect(app.state.dirty).toBe(false)
    await app.state.save()
    expect(PlanApi.updateStagePlanItems).not.toHaveBeenCalled()
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

it.each(['PROJECT_MANAGER', 'SERVICE_MANAGER'])('独立菜单对%s隐藏后项目详情仍能重新倒排并刷新草稿', async (role) => {
  expect(stagePlanMenusForRoles([
    { component: 'pms/engineering/stage-plan/index', visible: true }
  ], [role])[0].visible).toBe(false)
  feedback.confirm.mockResolvedValue(undefined)
  vi.mocked(PlanApi.autoEstimateStagePlanBatch).mockResolvedValue(draft() as any)
  const app = render()
  try {
    await flush()
    expect(app.state.editable).toBe(true)
    await app.state.estimate()
    expect(PlanApi.autoEstimateStagePlanBatch).toHaveBeenCalledWith(8)
    expect(feedback.success).toHaveBeenCalledWith('倒排已完成，请核对阶段与任务安排')
    expect(app.state.batch.id).toBe(8)
    expect(app.state.dirty).toBe(false)
    expect(effective.status).toBe(2)
  } finally { app.app.unmount() }
})

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

it('excludes external members and service-manager rows without a valid system account from approval candidates', async () => {
  vi.mocked(MemberApi.getMemberPage).mockResolvedValueOnce({ list: [
    { userId: null, memberName: '外部人员', memberRole: 'END_USER' },
    { userId: null, memberName: '无账号', memberRole: 'SERVICE_MANAGER' },
    { userId: 0, memberName: '无效账号', memberRole: 'SERVICE_MANAGER' },
    { userId: 9, memberName: '经理', memberRole: 'SERVICE_MANAGER' }
  ], total: 4 } as any)
  const app = render()
  try {
    await flush()
    await app.state.openSubmit()
    expect(app.state.approvers.map((person: { userId: number }) => person.userId)).toEqual([9])
    expect(app.state.approverId).toBe(9)
  } finally { app.app.unmount() }
})

it('collects all current service-manager pages and deduplicates multiple responsibilities', async () => {
  vi.mocked(MemberApi.getMemberPage)
    .mockResolvedValueOnce({
      list: [
        { userId: 9, memberName: '经理', memberRole: 'SERVICE_MANAGER_L1' },
        { userId: null, memberName: '未关联人员', memberRole: 'SERVICE_MANAGER' }
      ],
      total: 3
    } as any)
    .mockResolvedValueOnce({
      list: [{ userId: 9, memberName: '经理', memberRole: 'SERVICE_MANAGER_L2' }],
      total: 3
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

it('preserves a service manager long account ID when selecting and submitting approval', async () => {
  const userId = '2108539871029019646'
  vi.mocked(MemberApi.getMemberPage).mockResolvedValueOnce({ list: [
    { userId, memberName: '本项目服务经理', memberRole: 'SERVICE_MANAGER' }
  ], total: 1 } as any)
  const app = render()
  try {
    await flush()
    await app.state.openSubmit()
    expect(app.state.approvers.map((person: { userId: string }) => person.userId)).toEqual([userId])
    expect(app.state.approverId).toBe(userId)
    expect(app.state.approverError).toBe('')
    await app.state.submit()
    expect(PlanApi.submitStagePlanBatch).toHaveBeenCalledWith(8, userId, undefined)
  } finally { app.app.unmount() }
})

it('deduplicates equivalent account IDs without rounding distinct long IDs or accepting invalid candidates', async () => {
  vi.mocked(MemberApi.getMemberPage).mockResolvedValueOnce({ list: [
    { userId: 9, memberRole: 'SERVICE_MANAGER' },
    { userId: '9', memberRole: 'SERVICE_MANAGER_L1' },
    { userId: '2108539871029019646', memberRole: 'SERVICE_MANAGER' },
    { userId: '2108539871029019647', memberRole: 'SERVICE_MANAGER_L2' },
    { userId: '10', memberRole: 'PROJECT_MANAGER' },
    ...[null, '', '0', '-1', '1.5', 'abc', 1.5, Number.MAX_SAFE_INTEGER + 1].map(userId => ({ userId, memberRole: 'SERVICE_MANAGER' }))
  ], total: 13 } as any)
  const app = render()
  try {
    await flush()
    await app.state.openSubmit()
    expect(app.state.approvers.map((person: { userId: number | string }) => person.userId))
      .toEqual(['9', '2108539871029019646', '2108539871029019647'])
    expect(app.state.approverId).toBeUndefined()
  } finally { app.app.unmount() }
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

it('allows a plan later than the suggested date to enter approval', async () => {
  const app = render()
  try {
    await flush()
    app.state.batch.items[0].suggestedEnd = '2026-07-16'
    app.state.saved = JSON.stringify(app.state.batch)
    app.state.approverId = 9
    vi.mocked(PlanApi.submitStagePlanBatch).mockResolvedValue(draft() as any)
    await app.state.submit()
    expect(PlanApi.submitStagePlanBatch).toHaveBeenCalledWith(8, 9, undefined)
  } finally { app.app.unmount() }
})

it.each(['baseline', 'acceptance', 'sequence'] as const)('still blocks %s violations when a suggestion is exceeded', async (constraint) => {
  const app = render()
  try {
    await flush()
    app.state.batch.items[0].suggestedEnd = '2026-07-16'
    if (constraint === 'baseline') app.state.batch.baselineEnd = '2026-10-25'
    if (constraint === 'acceptance') app.state.batch.inputSnapshot = JSON.stringify({ stages: [{ stageId: 10, acceptanceTime: '2026-10-25' }] })
    if (constraint === 'sequence') {
      app.state.batch.items.push({ phaseId: 9, phaseName: '准备', planStart: '2026-10-01', planEnd: '2026-10-02' })
      app.state.batch.inputSnapshot = JSON.stringify({ serialEdges: [{ fromStageId: 9, toStageId: 10 }] })
    }
    app.state.saved = JSON.stringify(app.state.batch)
    app.state.approverId = 9
    await app.state.submit()
    expect(PlanApi.submitStagePlanBatch).not.toHaveBeenCalled()
    expect(feedback.warning).toHaveBeenCalledWith(expect.stringContaining({ baseline: '超出工期', acceptance: '不得晚于计划验收', sequence: '不得早于前一阶段' }[constraint]!))
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

it.each([false, true])('has one duration entry when no duration exists (embedded=%s)', async embedded => {
  businessBar.embedded = embedded
  vi.mocked(DurationApi.getByProjectId).mockResolvedValue(undefined as any)
  vi.mocked(PlanApi.getStagePlanBatchPage).mockResolvedValue({ list: [], total: 0 })
  const app = render()
  try {
    await flush()
    const buttons = (node: any): any[] => [
      ...(node.type === 'button' && /录入.*工期/.test(textOf(node)) ? [node] : []),
      ...node.children.flatMap(buttons)
    ]
    const entries = buttons(app.root)
    expect(entries).toHaveLength(1)
    entries[0].props.onClick()
    expect(app.state.durationVisible).toBe(true)
  } finally { app.app.unmount() }
})
