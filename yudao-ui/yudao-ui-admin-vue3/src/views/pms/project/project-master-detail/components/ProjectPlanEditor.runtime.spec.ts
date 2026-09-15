import { defineComponent, h, nextTick, reactive } from 'vue'
import { beforeEach, expect, it, vi } from 'vitest'
import { ElMessageBox } from 'element-plus'
import ProjectPlanEditor from './ProjectPlanEditor.vue'
import * as api from '@/api/pms/project/projects/projectPlan'
import {
  mount,
  passthrough,
  textOf,
  type TestNode
} from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

// https://element-plus.org/en-US/component/message-box.html#confirm
const confirmMessage = vi.hoisted(() => vi.fn<() => Promise<'confirm'>>())
vi.mock('element-plus', () => ({ ElMessageBox: { confirm: confirmMessage } }))
vi.mock('../../project-templates/TemplateContentEditor.vue', async () => {
  const { defineComponent, h, nextTick } = await import('vue')
  return {
    default: defineComponent({
      props: { content: Object, readonly: Boolean, busy: Boolean },
      emits: ['dirty-change'],
      setup: (props, { emit, expose }) => {
        expose({ prepareSave: async () => {
          await nextTick()
          if (props.readonly) throw new Error('编辑上下文已变化，请重新保存。')
          expect(props.busy).toBe(true)
          return props.content
        } })
        return () => h('button', { onClick: () => emit('dirty-change', true) }, '修改节点绑定')
      }
    })
  }
})
vi.mock('../../project-templates/editorModel', () => ({
  errorText: (error: Error) => error.message
}))
vi.mock('@/api/pms/project/projects/projectPlan', () => ({
  getProjectPlan: vi.fn(),
  createProjectPlanDraft: vi.fn(),
  saveProjectPlanDraft: vi.fn(),
  previewProjectPlanDraft: vi.fn(),
  applyProjectPlanDraft: vi.fn()
}))
beforeEach(() => vi.resetAllMocks())
const tick = async () => {
  for (let i = 0; i < 12; i++) {
    await Promise.resolve()
    await nextTick()
  }
}
const visit = (node: TestNode, predicate: (node: TestNode) => boolean): TestNode | undefined =>
  predicate(node) ? node : node.children.map((child) => visit(child, predicate)).find(Boolean)

it('shows actual execution-round handling in saved-draft preview without saving or advancing the plan', async () => {
  const actions = ['CREATE', 'REBASE_CURRENT', 'PRESERVE_HISTORY', 'RETIRE_UNSTARTED'] as const
  const executionChanges: api.PlanImpact['executionChanges'] = actions.map((action, index) => ({
    nodeKey: `task:${index}`,
    nodeKind: 'TASK',
    name: `任务${index}`,
    action,
    nodeInstanceId: index ? String(10 + index) : null,
    executionId: index ? `900719925474099${index}` : null,
    executionVersion: index ? 2 : null,
    nodeVersion: index ? 6 : null,
    roundNo: index === 0 ? 1 : 3,
    sourcePlanVersionId: index ? '9007199254740999' : null,
    fromCode: index ? `T${index}` : null,
    toCode: `T${index}`
  }))
  const changes: api.PlanImpact['changes'] = executionChanges.map((change) => ({
    nodeKey: change.nodeKey,
    nodeKind: change.nodeKind,
    name: change.name,
    action: 'UPDATE',
    started: false,
    completed: change.action === 'PRESERVE_HISTORY',
    effects: []
  }))
  vi.mocked(api.getProjectPlan).mockResolvedValue({
    editable: true,
    projectVersion: 6,
    effective: {
      id: '9007199254740999',
      revisionNo: 1,
      version: 0,
      designer: { schemaVersion: 2 } as any
    },
    draft: {
      id: '9007199254740998',
      revisionNo: 2,
      version: 4,
      designer: { schemaVersion: 2 } as any
    }
  })
  vi.mocked(api.previewProjectPlanDraft).mockResolvedValue({
    basePlanVersionId: '9007199254740999',
    draftId: '9007199254740998',
    draftVersion: 4,
    projectVersion: 6,
    changedRuleKeys: [],
    changes,
    executionChanges,
    deliverableChanges: [],
    milestoneChanges: [],
    gateChanges: [],
    issues: []
  })
  const state = reactive({ visible: false })
  const column = defineComponent({
    setup:
      (_, { slots }) =>
      () =>
        h(
          'div',
          changes.map((row) => slots.default?.({ row }))
        )
  })
  const page = mount(
    defineComponent({
      setup: () => () => h(ProjectPlanEditor, { projectId: 9, modelValue: state.visible })
    }),
    {},
    {
      ElTable: passthrough,
      ElTableColumn: column
    }
  )
  try {
    state.visible = true
    await tick()
    const preview = visit(
      page.root,
      (node) => node.type === 'button' && textOf(node) === '影响预览'
    )!
    expect(preview.props?.disabled).toBe(false)
    await (preview.props!.onClick as Function)()
    await tick()
    expect(textOf(page.root)).toContain('新增独立执行')
    expect(textOf(page.root)).toContain('延续第3轮，不自动返工')
    expect(textOf(page.root)).toContain('保留第3轮及原冻结版本')
    expect(textOf(page.root)).toContain('移除未开始节点，保留原计划记录')
    expect(api.previewProjectPlanDraft).toHaveBeenCalledWith(
      9,
      expect.objectContaining({ id: '9007199254740998', version: 4 })
    )
    expect(api.saveProjectPlanDraft).not.toHaveBeenCalled()
    expect(api.createProjectPlanDraft).not.toHaveBeenCalled()
    expect(api.applyProjectPlanDraft).not.toHaveBeenCalled()
  } finally {
    page.app.unmount()
  }
})

const planState = (): api.ProjectPlanState => ({
  editable: true,
  projectVersion: 6,
  effective: {
    id: '9007199254740999',
    revisionNo: 1,
    version: 0,
    designer: { schemaVersion: 2 } as any
  },
  draft: {
    id: '9007199254740998',
    revisionNo: 2,
    version: 4,
    designer: { schemaVersion: 2 } as any
  }
})
const planImpact = (): api.PlanImpact => ({
  basePlanVersionId: '9007199254740999',
  draftId: '9007199254740998',
  draftVersion: 4,
  projectVersion: 6,
  changes: [],
  changedRuleKeys: [],
  executionChanges: [],
  deliverableChanges: [],
  milestoneChanges: [],
  gateChanges: [],
  issues: []
})
const openEditor = async () => {
  const state = reactive({ visible: false, projectId: 9 })
  const changed = vi.fn()
  const page = mount(
    defineComponent({
      setup: () => () =>
        h(ProjectPlanEditor, {
          projectId: state.projectId,
          modelValue: state.visible,
          onChanged: changed
        })
    }),
    {},
    { ElTable: passthrough, ElTableColumn: { render: () => null } }
  )
  state.visible = true
  await tick()
  const button = (label: string) =>
    visit(page.root, (node) => node.type === 'button' && textOf(node) === label)!
  const click = async (label: string) => {
    await (button(label).props!.onClick as () => Promise<void>)()
    await tick()
  }
  return { ...page, state, changed, button, click }
}

it('saves a project draft while the editor is busy but not read-only', async () => {
  const original = planState()
  const expectedDraft = structuredClone(original.draft!)
  vi.mocked(api.getProjectPlan).mockResolvedValue(original)
  vi.mocked(api.saveProjectPlanDraft).mockResolvedValue({ ...original.draft!, version: 5 })
  const page = await openEditor()
  try {
    await page.click('修改节点绑定')
    await tick()
    await page.click('保存计划草稿')
    expect(api.saveProjectPlanDraft).toHaveBeenCalledWith(9, expectedDraft, expectedDraft.designer, expect.any(String))
    expect(textOf(page.root)).not.toContain('编辑上下文已变化')
    expect(api.applyProjectPlanDraft).not.toHaveBeenCalled()
  } finally { page.app.unmount() }
})

it('requires a clean successful preview and explicit confirmation before applying', async () => {
  vi.mocked(api.getProjectPlan).mockResolvedValue(planState())
  vi.mocked(api.previewProjectPlanDraft).mockResolvedValue(planImpact())
  const page = await openEditor()
  try {
    expect(page.button('生效计划').props?.disabled).toBe(true)
    await page.click('生效计划')
    expect(ElMessageBox.confirm).not.toHaveBeenCalled()
    await page.click('影响预览')
    expect(page.button('生效计划').props?.disabled).toBe(false)
    vi.mocked(ElMessageBox.confirm).mockRejectedValueOnce('cancel')
    await page.click('生效计划')
    expect(api.applyProjectPlanDraft).not.toHaveBeenCalled()
    expect(page.button('生效计划').props?.disabled).toBe(false)
    await page.click('修改节点绑定')
    expect(page.button('生效计划').props?.disabled).toBe(true)
    await page.click('生效计划')
    expect(api.applyProjectPlanDraft).not.toHaveBeenCalled()
    expect(api.saveProjectPlanDraft).not.toHaveBeenCalled()
  } finally {
    page.app.unmount()
  }
})

it('keeps a preview with validation issues unapplied', async () => {
  vi.mocked(api.getProjectPlan).mockResolvedValue(planState())
  vi.mocked(api.previewProjectPlanDraft).mockResolvedValue({
    ...planImpact(),
    issues: [
      {
        field: 'task:survey.binding',
        code: 'STARTED_BINDING_IMMUTABLE',
        message: '已开始任务不能改变业务绑定'
      }
    ]
  })
  const page = await openEditor()
  try {
    await page.click('影响预览')
    expect(page.button('生效计划').props?.disabled).toBe(true)
    await page.click('生效计划')
    expect(ElMessageBox.confirm).not.toHaveBeenCalled()
    expect(api.applyProjectPlanDraft).not.toHaveBeenCalled()
  } finally {
    page.app.unmount()
  }
})

it('reuses the apply intent key after an unknown network result and refreshes only after success', async () => {
  const oldState = planState()
  const impact = planImpact()
  vi.mocked(api.getProjectPlan)
    .mockResolvedValueOnce(oldState)
    .mockResolvedValue({
      editable: true,
      projectVersion: 7,
      effective: { ...oldState.draft!, version: 5 },
      draft: null
    })
  vi.mocked(api.previewProjectPlanDraft).mockResolvedValue(impact)
  confirmMessage.mockResolvedValue('confirm')
  vi.mocked(api.applyProjectPlanDraft)
    .mockRejectedValueOnce(new Error('网络结果未知'))
    .mockResolvedValue({
      projectId: 9,
      planVersionId: impact.draftId,
      projectVersion: 7,
      revisionNo: 2
    })
  const page = await openEditor()
  try {
    await page.click('影响预览')
    await page.click('生效计划')
    expect(textOf(page.root)).toContain('网络结果未知')
    expect(page.changed).not.toHaveBeenCalled()
    expect(api.getProjectPlan).toHaveBeenCalledTimes(1)
    await page.click('生效计划')
    const calls = vi.mocked(api.applyProjectPlanDraft).mock.calls
    expect(calls).toHaveLength(2)
    expect(calls[0]).toEqual([9, impact, expect.any(String)])
    expect(calls[0]![2]).not.toBe('')
    expect(calls[1]).toEqual(calls[0])
    expect(page.changed).toHaveBeenCalledTimes(1)
    expect(api.getProjectPlan).toHaveBeenCalledTimes(2)
    expect(textOf(page.root)).toContain('当前项目计划 v2')
    expect(textOf(page.root)).not.toContain('预览通过')
    expect(api.saveProjectPlanDraft).not.toHaveBeenCalled()
  } finally {
    page.app.unmount()
  }
})

it('does not apply a prior project after navigation while confirmation is open', async () => {
  vi.mocked(api.getProjectPlan).mockResolvedValue(planState())
  vi.mocked(api.previewProjectPlanDraft).mockResolvedValue(planImpact())
  let confirm!: (value: 'confirm') => void
  confirmMessage.mockReturnValue(
    new Promise((resolve) => {
      confirm = resolve
    })
  )
  const page = await openEditor()
  try {
    await page.click('影响预览')
    const applying = page.click('生效计划')
    await tick()
    page.state.projectId = 10
    await tick()
    confirm('confirm')
    await applying
    expect(api.applyProjectPlanDraft).not.toHaveBeenCalled()
    expect(page.changed).not.toHaveBeenCalled()
    expect(api.getProjectPlan).toHaveBeenLastCalledWith(10)
  } finally {
    page.app.unmount()
  }
})

it('discards a late preview response from the previous project', async () => {
  vi.mocked(api.getProjectPlan).mockResolvedValue(planState())
  let resolvePreview!: (value: api.PlanImpact) => void
  vi.mocked(api.previewProjectPlanDraft).mockReturnValue(
    new Promise((resolve) => {
      resolvePreview = resolve
    })
  )
  const page = await openEditor()
  try {
    const previewing = page.click('影响预览')
    await tick()
    page.state.projectId = 10
    await tick()
    resolvePreview(planImpact())
    await previewing
    expect(textOf(page.root)).not.toContain('预览通过')
    expect(page.button('生效计划').props?.disabled).toBe(true)
    expect(api.applyProjectPlanDraft).not.toHaveBeenCalled()
  } finally {
    page.app.unmount()
  }
})

it('does not send a plan activation after the editor is unmounted during confirmation', async () => {
  vi.mocked(api.getProjectPlan).mockResolvedValue(planState())
  vi.mocked(api.previewProjectPlanDraft).mockResolvedValue(planImpact())
  let confirm!: (value: 'confirm') => void
  confirmMessage.mockReturnValue(new Promise(resolve => { confirm = resolve }))
  const page = await openEditor()
  await page.click('影响预览')
  const applying = page.click('生效计划')
  await tick()
  page.app.unmount()
  confirm('confirm')
  await applying
  expect(api.applyProjectPlanDraft).not.toHaveBeenCalled()
  expect(page.changed).not.toHaveBeenCalled()
  expect(api.getProjectPlan).toHaveBeenCalledTimes(1)
})

it('does not reload or notify a departed page when an already-sent activation returns', async () => {
  vi.mocked(api.getProjectPlan).mockResolvedValue(planState())
  vi.mocked(api.previewProjectPlanDraft).mockResolvedValue(planImpact())
  confirmMessage.mockResolvedValue('confirm')
  let complete!: (value: Awaited<ReturnType<typeof api.applyProjectPlanDraft>>) => void
  vi.mocked(api.applyProjectPlanDraft).mockReturnValue(new Promise(resolve => { complete = resolve }))
  const page = await openEditor()
  await page.click('影响预览')
  const applying = page.click('生效计划')
  await tick()
  expect(api.applyProjectPlanDraft).toHaveBeenCalledTimes(1)
  page.app.unmount()
  complete({ projectId: 9, planVersionId: planImpact().draftId, projectVersion: 7, revisionNo: 2 })
  await applying
  expect(page.changed).not.toHaveBeenCalled()
  expect(api.getProjectPlan).toHaveBeenCalledTimes(1)
  // A request already submitted is not revoked; its server-side command/idempotency remains authoritative.
  expect(api.applyProjectPlanDraft).toHaveBeenCalledTimes(1)
})
