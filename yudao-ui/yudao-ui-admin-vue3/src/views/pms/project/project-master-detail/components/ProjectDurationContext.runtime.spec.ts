import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, reactive, ref, type Component } from 'vue'
import { mount, passthrough } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
import Panel from './ProjectDurationPanel.vue'
import Drawer from './ProjectDurationFormDrawer.vue'

const api = vi.hoisted(() => ({
  getByProjectId: vi.fn(),
  getChanges: vi.fn(),
  getChange: vi.fn(),
  createInitial: vi.fn(),
  createChange: vi.fn(),
  patchChange: vi.fn(),
  submitChange: vi.fn()
}))
const message = vi.hoisted(() => ({
  confirm: vi.fn(),
  prompt: vi.fn(),
  success: vi.fn(),
  warning: vi.fn()
}))
const cancel = vi.hoisted(() => vi.fn())
const push = vi.hoisted(() => vi.fn())
vi.mock('@/api/pms/engineering/construction-plan', () => api)
vi.mock('@/api/bpm/processInstance', () => ({ cancelProcessInstanceByStartUser: cancel }))
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => message }))
vi.mock('@/store/modules/user', () => ({ useUserStore: () => ({ getUser: { id: 17 } }) }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push }) }))
vi.mock('@vueuse/core', () => ({ useMediaQuery: () => ({ value: false }) }))
vi.mock('@/utils/dict', () => ({
  getStrDictOptions: () => [{ value: 'CUSTOMER_DELAY', label: '客户延期' }]
}))
vi.mock('@/components/PmsFileArtifact', () => ({
  PmsFileReferenceList: { render: () => null },
  PmsFileUploader: { render: () => null }
}))
vi.mock('./ProjectDurationHistoryDrawer.vue', () => ({
  default: {
    setup: (_: unknown, { expose }: any) => {
      expose({ open: vi.fn() })
      return () => null
    }
  }
}))

const revision = {
  revisionId: 101,
  revisionNo: 1,
  calculationBasis: 'DATE_RANGE',
  startDate: '2026-09-01',
  endDate: '2026-09-10',
  durationDays: 10
}
const plan = (projectId: number) => ({
  projectId,
  planId: projectId * 100,
  planVersion: 1,
  currentRevision: { ...revision },
  allowedActions: ['CREATE_CHANGE'],
  planRecalculationStatus: 'PENDING_RECALCULATION'
})
const change = () => ({
  changeId: 33,
  status: 'DRAFT',
  version: 2,
  reasonType: 'CUSTOMER_DELAY',
  reasonDetail: '原说明',
  customerEvidenceRequired: false,
  candidateRevision: { ...revision }
})
const deferred = <T>() => {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((done) => {
    resolve = done
  })
  return { promise, resolve }
}
const flush = async () => {
  for (let i = 0; i < 4; i++) {
    await nextTick()
    await Promise.resolve()
  }
}
const apps: { unmount: () => void }[] = []
const render = (component: Component, readonly = false, id: number | undefined = 1) => {
  const props = reactive({ project: { id, version: 7, projectEndDate: undefined as string | undefined }, readonly })
  const child = ref<any>()
  const wrapper = defineComponent({ setup: () => () => h(component, { ...props, ref: child }) })
  const stubs = Object.fromEntries(
    [
      'ElSkeleton',
      'ElRadioGroup',
      'ElRadioButton',
      'ElDatePicker',
      'ElInputNumber',
      'ElSelect',
      'ElOption',
      'ElInput'
    ].map((name) => [name, passthrough])
  )
  const { app } = mount(wrapper, {}, stubs)
  apps.push(app)
  return { props, child, state: () => child.value.$.setupState }
}

beforeEach(() => {
  vi.clearAllMocks()
  api.getByProjectId.mockImplementation(async (id: number) => plan(id))
  api.getChanges.mockResolvedValue({ items: [], hasMore: false })
  api.getChange.mockResolvedValue(change())
  message.confirm.mockResolvedValue(undefined)
  message.prompt.mockResolvedValue({ value: '测试撤回原因' })
})

it('uses the survey project deadline to derive the submitted initial date range', async () => {
  const view = render(Drawer)
  view.props.project.projectEndDate = '2026-12-31'
  await flush()
  view.child.value.openInitial()
  await flush()
  view.state().form.durationDays = 31
  await flush()
  expect(view.state().form.startDate).toBe('2026-12-01')
  expect(view.state().durationPayload()).toEqual({ calculationBasis: 'DATE_RANGE', startDate: '2026-12-01', endDate: '2026-12-31' })
  expect(view.props.project.projectEndDate).toBe('2026-12-31')
})
afterEach(() => apps.splice(0).forEach((app) => app.unmount()))

describe('PRE-01 existing duration components in project views', () => {
  it('keeps the existing project prop and default writable behavior', async () => {
    const view = render(Panel)
    await flush()
    expect(api.getByProjectId).toHaveBeenCalledWith(1)
    expect(view.state().plan.planId).toBe(100)
    expect(view.state().canWrite).toBe(true)
  })

  it.each([0, -1, NaN, Number.MAX_SAFE_INTEGER + 1])(
    'rejects invalid project %s without offering initial creation',
    async (id) => {
      const view = render(Panel, false, id)
      await flush()
      expect(api.getByProjectId).not.toHaveBeenCalled()
      expect(view.state().canWrite).toBe(false)
      expect(view.state().plan).toBeNull()
    }
  )

  it('ignores a late previous project and does not fetch its changes', async () => {
    const old = deferred<any>()
    api.getByProjectId.mockImplementation((id: number) =>
      id === 1 ? old.promise : Promise.resolve(plan(id))
    )
    const view = render(Panel)
    view.props.project.id = 2
    await flush()
    old.resolve(plan(1))
    await flush()
    expect(view.state().plan.projectId).toBe(2)
    expect(api.getChanges).not.toHaveBeenCalledWith(100, expect.anything())
  })

  it('ignores a late previous draft after switching projects', async () => {
    const old = deferred<any>()
    api.getChanges.mockImplementation(async (id: number) => ({
      items: id === 100 ? [change()] : []
    }))
    api.getChange.mockReturnValue(old.promise)
    const view = render(Panel)
    await flush()
    view.props.project.id = 2
    await flush()
    old.resolve(change())
    await flush()
    expect(view.state().plan.projectId).toBe(2)
    expect(view.state().draft).toBeUndefined()
  })

  it('shows a load error instead of a false first-duration creation state', async () => {
    api.getByProjectId.mockRejectedValueOnce(new Error('unavailable'))
    const view = render(Panel)
    await flush()
    expect(view.state().errorText).toContain('加载失败')
    expect(view.state().plan).toBeNull()
  })

  it('blocks submit, withdrawal and approval navigation in readonly mode', async () => {
    const view = render(Panel, true)
    await flush()
    view.state().draft = change()
    view.state().plan.pendingChangeSummary = {
      ...change(),
      processInstanceId: 'bpm1',
      applicantUserId: 17,
      status: 'PENDING_APPROVAL'
    }
    await view.state().submitDraft()
    await view.state().withdraw()
    view.state().openBpm('bpm1')
    expect(api.submitChange).not.toHaveBeenCalled()
    expect(cancel).not.toHaveBeenCalled()
    expect(push).not.toHaveBeenCalled()
    view.state().formRef.openInitial()
    expect(view.state().formRef.isDirty()).toBe(false)
  })

  it('rechecks readonly after the submit confirmation', async () => {
    const confirmation = deferred<void>()
    message.confirm.mockReturnValue(confirmation.promise)
    const view = render(Panel)
    await flush()
    view.state().draft = change()
    const pending = view.state().submitDraft()
    view.props.readonly = true
    await flush()
    confirmation.resolve()
    await pending
    expect(api.submitChange).not.toHaveBeenCalled()
  })

  it('does not withdraw an old project after its prompt resolves', async () => {
    const prompt = deferred<any>()
    message.prompt.mockReturnValue(prompt.promise)
    const view = render(Panel)
    await flush()
    view.state().plan.pendingChangeSummary = { ...change(), processInstanceId: 'old-bpm' }
    const pending = view.state().withdraw()
    view.props.project.id = 2
    await flush()
    prompt.resolve({ value: '测试原因' })
    await pending
    expect(cancel).not.toHaveBeenCalled()
  })

  it('reuses the original initial-duration API and date payload', async () => {
    const view = render(Drawer)
    view.child.value.openInitial()
    view.state().formRef = { validate: async () => true }
    Object.assign(view.state().form, { startDate: '2026-09-01', endDate: '2026-09-10' })
    await view.state().save()
    expect(api.createInitial).toHaveBeenCalledWith(
      {
        projectId: 1,
        expectedProjectVersion: 7,
        calculationBasis: 'DATE_RANGE',
        startDate: '2026-09-01',
        endDate: '2026-09-10'
      },
      expect.any(String)
    )
  })

  it('does not write after the project changes during asynchronous form validation', async () => {
    const validation = deferred<boolean>()
    const view = render(Drawer)
    view.child.value.openInitial()
    view.state().formRef = { validate: () => validation.promise }
    const pending = view.state().save()
    view.props.project.id = 2
    await flush()
    validation.resolve(true)
    await pending
    expect(api.createInitial).not.toHaveBeenCalled()
  })

  it('rejects readonly form/file handlers and mismatched project plans', async () => {
    const view = render(Drawer, true)
    view.child.value.openInitial()
    view.child.value.openCreate(plan(1))
    expect(view.state().visible).toBe(false)
    await view.state().save()
    await view.state().saveEvidence({ artifactId: 1 })
    await view.state().clearEvidence({ artifactId: 1 })
    expect(api.createInitial).not.toHaveBeenCalled()
    expect(api.patchChange).not.toHaveBeenCalled()
    view.props.readonly = false
    await flush()
    view.child.value.openEdit(plan(2), change())
    expect(view.state().visible).toBe(false)
  })

  it('preserves draft input when readonly changes and supports explicit discard', async () => {
    const view = render(Drawer)
    view.child.value.openEdit(plan(1), change())
    view.state().form.reasonDetail = '未保存输入'
    view.props.readonly = true
    await flush()
    expect(view.state().form.reasonDetail).toBe('未保存输入')
    expect(view.child.value.isDirty()).toBe(true)
    expect(view.child.value.discardChanges()).toBe(true)
    expect(view.child.value.isDirty()).toBe(false)
  })

  it('does not apply a late evidence PATCH to a new project', async () => {
    const patched = deferred<any>()
    api.patchChange.mockReturnValue(patched.promise)
    const view = render(Drawer)
    view.child.value.openEdit(plan(1), change())
    const pending = view
      .state()
      .saveEvidence({ artifactId: 1, versionNo: 1, referenceKey: 'customer-delay' })
    view.props.project.id = 2
    await flush()
    patched.resolve(change())
    await pending
    expect(view.state().draft).toBeUndefined()
    expect(view.state().visible).toBe(false)
  })
})
