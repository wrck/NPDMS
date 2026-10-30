import { expect, it, vi } from 'vitest'
import { nextTick, reactive } from 'vue'
import Panel from './InspectionUmcTrackingPanel.vue'
import {
  mount,
  passthrough,
  textOf
} from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

vi.mock('@/utils/permission', () => ({ checkPermi: () => true }))
vi.mock('@/utils/formatTime', () => ({ formatDate: () => '2026-10-29' }))

const render = () => {
  const task = reactive({
    id: '10',
    statusCode: 'TRACKING',
    ownerUserId: '9',
    manager: false,
    issues: [
      {
        id: '20',
        resultId: '2115358100674351105',
        taskId: '10',
        version: '1',
        statusCode: 'PENDING',
        responsibleUserId: '9',
        description: 'CPU异常'
      },
      {
        id: '21',
        resultId: '2115358100674351106',
        taskId: '10',
        version: '1',
        statusCode: 'PROCESSING',
        responsibleUserId: '8',
        description: '端口异常'
      }
    ],
    results: [] as any[],
    devices: [] as any[],
    remediations: []
  })
  const save = vi.fn(),
    dirty = vi.fn()
  const page = mount(
    Panel,
    { task, actorId: '9', disabled: false, onSave: save, onDraftChanged: dirty },
    Object.fromEntries(
      [
        'ElEmpty',
        'ElTag',
        'ElForm',
        'ElFormItem',
        'ElRadioGroup',
        'ElRadio',
        'ElInput',
        'ElButton'
      ].map((name) => [name, passthrough])
    )
  )
  return { ...page, task, save, dirty, state: (page.vm as any).$.setupState }
}

it('shows Demo choices and remark per issue, saving an open issue only appends a process record', () => {
  const p = render()
  try {
    expect(textOf(p.root)).toContain('未闭环')
    p.state.draftFor(p.task.issues[0]).remark = '继续排查'
    p.state.save(p.task.issues[0])
    expect(p.save).toHaveBeenCalledWith({
      issueId: '20',
      version: '1',
      action: 'record-process',
      data: { content: '继续排查' }
    })
    p.state.draftFor(p.task.issues[0]).closed = true
    p.state.save(p.task.issues[0])
    expect(p.save).toHaveBeenLastCalledWith(
      expect.objectContaining({ action: 'request-closure', data: { description: '继续排查' } })
    )
    expect(p.task.issues[0].statusCode).toBe('PENDING')
  } finally {
    p.app.unmount()
  }
})

it('keeps actual pending confirmation separate and uses original confirm or return action', async () => {
  const p = render()
  try {
    p.task.issues[0] = { ...p.task.issues[0], statusCode: 'CLOSURE_PENDING', version: '2' }
    await nextTick()
    const draft = p.state.draftFor(p.task.issues[0])
    expect(draft.closed).toBe(false)
    draft.remark = '证据核对'
    draft.closed = true
    p.state.save(p.task.issues[0])
    expect(p.save).toHaveBeenLastCalledWith(
      expect.objectContaining({ action: 'confirm-closure', version: '2' })
    )
    draft.closed = false
    p.state.save(p.task.issues[0])
    expect(p.save).toHaveBeenLastCalledWith(expect.objectContaining({ action: 'return-closure' }))
  } finally {
    p.app.unmount()
  }
})

it('blocks nonresponsible, archived, disabled and blank submissions', async () => {
  const p = render()
  try {
    p.state.save(p.task.issues[0])
    p.state.draftFor(p.task.issues[1]).remark = '不能代他人办理'
    p.state.save(p.task.issues[1])
    p.task.statusCode = 'ARCHIVED'
    p.state.draftFor(p.task.issues[0]).remark = '历史只读'
    p.state.save(p.task.issues[0])
    expect(p.save).not.toHaveBeenCalled()
    p.task.statusCode = 'CANCELLED'
    p.state.save(p.task.issues[0])
    expect(p.save).toHaveBeenCalledTimes(1)
  } finally {
    p.app.unmount()
  }
})

it('refreshes changed issue revision while retaining other unsaved remarks and clears on task switch', async () => {
  const p = render()
  try {
    p.state.draftFor(p.task.issues[1]).remark = '保留未提交备注'
    p.state.draftFor(p.task.issues[0]).remark = '已保存备注'
    p.state.save(p.task.issues[0])
    p.task.issues[0] = { ...p.task.issues[0], version: '2' }
    p.task.remediations = [{ id: '30', issueId: '20', content: '已保存备注', actionAt: 1 }] as any
    await nextTick()
    expect(p.state.draftFor(p.task.issues[1]).remark).toBe('保留未提交备注')
    expect(p.state.draftFor(p.task.issues[0]).remark).toBe('已保存备注')
    p.task.id = '11'
    await nextTick()
    expect(p.state.draftFor(p.task.issues[1]).remark).toBe('')
  } finally {
    p.app.unmount()
  }
})

it('preserves stale unsubmitted input and prevents using the new issue version', async () => {
  const p = render()
  try {
    p.state.draftFor(p.task.issues[0]).remark = '未提交的现场备注'
    p.task.issues[0] = { ...p.task.issues[0], version: '2' }
    await nextTick()
    expect(p.state.draftFor(p.task.issues[0]).remark).toBe('未提交的现场备注')
    expect(p.state.draftFor(p.task.issues[0]).stale).toBe(true)
    p.state.save(p.task.issues[0])
    expect(p.save).not.toHaveBeenCalled()
    p.state.reset(p.task.issues[0])
    expect(p.state.draftFor(p.task.issues[0]).remark).toBe('')
  } finally {
    p.app.unmount()
  }
})

it('discards only tracking edits using the current server baseline when leaving is confirmed', async () => {
  const p = render()
  try {
    p.state.draftFor(p.task.issues[0]).closed = true
    p.state.draftFor(p.task.issues[0]).remark = '原闭环申请被拒，草稿保留'
    p.state.draftFor(p.task.issues[1]).remark = '另一项未保存'
    await nextTick()
    expect(p.dirty).toHaveBeenLastCalledWith(true)
    p.state.discardDrafts()
    await nextTick()
    expect(p.state.draftFor(p.task.issues[0]).closed).toBe(false)
    expect(p.state.draftFor(p.task.issues[0]).remark).toBe('')
    expect(p.state.draftFor(p.task.issues[1]).remark).toBe('')
    expect(p.dirty).toHaveBeenLastCalledWith(false)
    expect(p.save).not.toHaveBeenCalled()
  } finally {
    p.app.unmount()
  }
})

it('shows the exact confirmed inspection item and verdict while retaining the tracking reason', async () => {
  const p = render()
  try {
    p.task.results = [
      {
        id: '2115358100674351105',
        deviceId: '40',
        ruleName: 'CPU异常繁忙检查',
        detailMessage: 'VCPU有历史异常记录，请及时检查',
        reportId: '11',
        sourceCode: 'UMC'
      },
      {
        id: '2115358100674351106',
        deviceId: '41',
        ruleName: 'CPU异常繁忙检查',
        detailMessage: '另一台设备的判定说明',
        reportId: '12',
        sourceCode: 'UMC'
      }
    ]
    p.task.devices = [
      { deviceId: '40', serialNumber: 'QA-SN-40' },
      { deviceId: '41', serialNumber: 'QA-SN-41' }
    ]
    await nextTick()
    expect(p.state.trackedResult(p.task.issues[0]).detailMessage).toBe(
      'VCPU有历史异常记录，请及时检查'
    )
    expect(p.state.trackedDevice(p.task.issues[0])).toBe('QA-SN-40')
    expect(p.state.trackedResult(p.task.issues[1]).detailMessage).toBe('另一台设备的判定说明')
    expect(textOf(p.root)).toContain('CPU异常繁忙检查')
    expect(textOf(p.root)).toContain('VCPU有历史异常记录，请及时检查')
    expect(textOf(p.root)).toContain('确认跟踪原因：CPU异常')
  } finally {
    p.app.unmount()
  }
})

it('does not guess by matching names or choose a new execution when the exact source is absent or ambiguous', async () => {
  const p = render()
  try {
    p.task.results = [
      { id: 'other', deviceId: '40', ruleName: 'CPU异常', detailMessage: '不得误用的更新结果' }
    ]
    await nextTick()
    expect(p.state.trackedResult(p.task.issues[0])).toBeUndefined()
    expect(textOf(p.root)).toContain('巡检项目来源缺失')
    expect(textOf(p.root)).not.toContain('不得误用的更新结果')
    p.task.results = [
      { id: p.task.issues[0].resultId, ruleName: '原项' },
      { id: p.task.issues[0].resultId, ruleName: '重复项' }
    ]
    await nextTick()
    expect(p.state.trackedResult(p.task.issues[0])).toBeUndefined()
  } finally {
    p.app.unmount()
  }
})
