import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, reactive, ref, type Component } from 'vue'
import {
  mount,
  passthrough,
  tableColumn
} from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
import TaskPanel from './TaskPanel.vue'
import ResultPanel from './ResultPanel.vue'
import Workbench from './index.vue'
import { satisfactionProjectContext, type SatisfactionViewProps } from './projectContext'

const api = vi.hoisted(() => ({
  listTasks: vi.fn(),
  listResults: vi.fn(),
  assignTask: vi.fn(),
  createGrant: vi.fn(),
  reserveAssistedResponse: vi.fn(),
  initializeAssistedFile: vi.fn(),
  completeAssistedFile: vi.fn(),
  submitAssisted: vi.fn(),
  recollect: vi.fn(),
  invalidateResult: vi.fn(),
  requestResultExport: vi.fn(),
  getExportTask: vi.fn(),
  getResultDownload: vi.fn()
}))
const message = vi.hoisted(() => ({ confirm: vi.fn(), success: vi.fn(), warning: vi.fn() }))
const fileApi = vi.hoisted(() => ({ createAccessTicket: vi.fn() }))
vi.mock('@/api/pms/project/satisfaction', () => api)
vi.mock('@/api/pms/platform/file', () => fileApi)
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => message }))
vi.mock('@/utils/auth', () => ({ getTenantId: () => 1 }))
vi.mock('@/components/Qrcode', () => ({ Qrcode: { render: () => null } }))
vi.mock('./TemplatePanel.vue', () => ({ default: { render: () => '模板管理测试占位' } }))

const mounted: { unmount: () => void }[] = []
const flush = async () => {
  await nextTick()
  await Promise.resolve()
  await nextTick()
}
const task = (projectId: number) => ({
  id: projectId * 10,
  projectId,
  version: 3,
  resultId: projectId * 100
})
const result = (projectId: number) => ({
  resultId: projectId * 100,
  projectId,
  passed: true,
  resultStatus: 'EFFECTIVE'
})
const deferred = <T>() => {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((done) => {
    resolve = done
  })
  return { promise, resolve }
}

const renderPanel = (component: Component, initial: SatisfactionViewProps = {}) => {
  const props = reactive(initial)
  // setupState is used only to drive the real SFC handlers; the public integration API remains narrow.
  const child = ref<any>()
  const wrapper = defineComponent({ setup: () => () => h(component, { ...props, ref: child }) })
  const stubs = Object.fromEntries(
    [
      'ElInputNumber',
      'ElSkeleton',
      'ElTable',
      'ElDialog',
      'ElInput',
      'ElDatePicker',
      'ElUpload',
      'ElCheckboxGroup',
      'ElCheckbox',
      'ElTabs',
      'ElTabPane'
    ].map((name) => [name, passthrough])
  )
  const { app, root } = mount(wrapper, {}, { ...stubs, ElTableColumn: tableColumn })
  mounted.push(app)
  return { props, root, child, state: () => child.value.$.setupState }
}

beforeEach(() => {
  vi.clearAllMocks()
  api.listTasks.mockImplementation(async (id?: number) => (id ? [task(id)] : []))
  api.listResults.mockImplementation(async (id?: number) => (id ? [result(id)] : []))
  message.confirm.mockResolvedValue(undefined)
  vi.stubGlobal('window', { clearTimeout, setTimeout, location: { origin: 'http://localhost' } })
})
afterEach(() => {
  mounted.splice(0).forEach((app) => app.unmount())
  vi.unstubAllGlobals()
})

describe('ACC-02 existing panels in a project context', () => {
  it('keeps standalone queries unchanged and locks contextual queries to the supplied project', async () => {
    renderPanel(TaskPanel)
    renderPanel(ResultPanel)
    const scoped = renderPanel(TaskPanel, { projectId: 41 })
    await flush()
    expect(api.listTasks).toHaveBeenCalledWith(undefined)
    expect(api.listResults).toHaveBeenCalledWith(undefined)
    expect(api.listTasks).toHaveBeenCalledWith(41)
    scoped.state().projectId = 99
    await scoped.state().load()
    expect(api.listTasks).toHaveBeenLastCalledWith(41)
  })

  it.each([0, -1, NaN, null, Number.MAX_SAFE_INTEGER + 1])(
    'rejects supplied invalid context %s without broadening the query',
    async (id) => {
      renderPanel(TaskPanel, { projectId: id as number })
      renderPanel(ResultPanel, { projectId: id as number })
      await flush()
      expect(api.listTasks).not.toHaveBeenCalled()
      expect(api.listResults).not.toHaveBeenCalled()
      expect(satisfactionProjectContext(id as number, 12).valid).toBe(false)
    }
  )

  it.each([
    [TaskPanel, 'listTasks', 'tasks'],
    [ResultPanel, 'listResults', 'results']
  ] as const)(
    'ignores old %s responses after a supported project switch',
    async (component, method, rows) => {
      const first = deferred<any[]>()
      api[method].mockImplementation((id: number) =>
        id === 1 ? first.promise : Promise.resolve([{ projectId: id }])
      )
      const view = renderPanel(component, { projectId: 1 })
      view.props.projectId = 2
      await flush()
      first.resolve([{ projectId: 1 }])
      await flush()
      expect(view.state()[rows]).toEqual([{ projectId: 2 }])
    }
  )

  it('clears old data and shows an error when the current project query fails', async () => {
    const view = renderPanel(TaskPanel, { projectId: 1 })
    await flush()
    api.listTasks.mockRejectedValueOnce(new Error('unavailable'))
    view.props.projectId = 2
    await flush()
    expect(view.state().tasks).toEqual([])
    expect(view.state().errorText).toContain('加载失败')
  })

  it('prevents every task write handler in readonly mode, even when called directly', async () => {
    const view = renderPanel(TaskPanel, { projectId: 1, readonly: true })
    const state = view.state()
    state.selected = task(1)
    state.assignedUserId = 7
    state.grantExpiresAt = '2026-10-01T12:00:00'
    await state.assign()
    await state.createGrant()
    await state.submitAssisted()
    await state.submitRecollect()
    for (const name of [
      'assignTask',
      'createGrant',
      'reserveAssistedResponse',
      'submitAssisted',
      'recollect'
    ] as const) {
      expect(api[name]).not.toHaveBeenCalled()
    }
  })

  it('reuses the original assign API and task version for a matching writable project', async () => {
    const view = renderPanel(TaskPanel, { projectId: 1 })
    view.state().openAssign(task(1))
    view.state().assignedUserId = 7
    await view.state().assign()
    expect(api.assignTask).toHaveBeenCalledWith(task(1), 7)
    view.state().openAssign(task(2))
    expect(view.state().assignVisible).toBe(false)
  })

  it('does not expose a late grant URL in a different project', async () => {
    const grant = deferred<any>()
    api.createGrant.mockReturnValueOnce(grant.promise)
    const view = renderPanel(TaskPanel, { projectId: 1 })
    view.state().openGrant(task(1))
    const pending = view.state().createGrant()
    view.props.projectId = 2
    await flush()
    grant.resolve({ token: 'fixture-only' })
    await pending
    expect(view.state().grantUrl).toBe('')
    expect(view.state().selected).toBeUndefined()
  })

  it('stops follow-on uploads after readonly changes while response reservation is in flight', async () => {
    const reservation = deferred<any>()
    api.reserveAssistedResponse.mockReturnValueOnce(reservation.promise)
    const view = renderPanel(TaskPanel, { projectId: 1 })
    view.state().openAssisted(task(1))
    view.state().assisted.customerContactRef = 'fixture-contact'
    view.state().assistedSignatureFiles = [
      { raw: { name: 'signature.png', type: 'image/png', size: 1 } }
    ]
    const pending = view.state().submitAssisted()
    view.props.readonly = true
    await flush()
    reservation.resolve({ responseId: 123 })
    await expect(pending).rejects.toThrow('上下文已变化')
    expect(api.initializeAssistedFile).not.toHaveBeenCalled()
    expect(api.completeAssistedFile).not.toHaveBeenCalled()
    expect(api.submitAssisted).not.toHaveBeenCalled()
  })

  it('rechecks readonly after the result invalidation confirmation dialog', async () => {
    const confirm = deferred<void>()
    message.confirm.mockReturnValueOnce(confirm.promise)
    const view = renderPanel(ResultPanel, { projectId: 1 })
    const pending = view.state().invalidate(result(1))
    view.props.readonly = true
    await flush()
    confirm.resolve()
    await pending
    expect(api.invalidateResult).not.toHaveBeenCalled()
  })

  it('does not keep a previous project export task or timer after the context changes', async () => {
    const exported = deferred<any>()
    api.requestResultExport.mockReturnValueOnce(exported.promise)
    const view = renderPanel(ResultPanel, { projectId: 1 })
    const pending = view.state().startExport()
    view.props.projectId = 2
    await flush()
    exported.resolve({ taskId: 33, status: 'REQUESTED' })
    await pending
    expect(view.state().exportTask).toBeUndefined()
    expect(view.state().exporting).toBe(false)
    expect(api.getExportTask).not.toHaveBeenCalled()
  })

  it('does not request a file access ticket after its project context has changed', async () => {
    const download = deferred<any>()
    api.getResultDownload.mockReturnValueOnce(download.promise)
    const view = renderPanel(ResultPanel, { projectId: 1 })
    view.state().openDownload(result(1))
    const pending = view.state().download()
    view.props.projectId = 2
    await flush()
    download.resolve({ role: 'RESULT_DOCUMENT' })
    await pending
    expect(fileApi.createAccessTicket).not.toHaveBeenCalled()
  })

  it('preserves open assisted draft values on readonly changes and supports explicit discard', async () => {
    const view = renderPanel(TaskPanel, { projectId: 1 })
    view.state().openAssisted(task(1))
    view.state().assisted.customerContactRef = 'unsaved-contact'
    view.props.readonly = true
    await flush()
    expect(view.state().assisted.customerContactRef).toBe('unsaved-contact')
    expect(view.child.value.isDirty()).toBe(true)
    expect(view.child.value.discardChanges()).toBe(true)
    expect(view.child.value.isDirty()).toBe(false)
  })

  it('forwards the same project and readonly restriction through the existing top-level page', async () => {
    const view = renderPanel(Workbench, { projectId: 51, readonly: true })
    await flush()
    expect(api.listTasks).toHaveBeenCalledWith(51)
    expect(api.listResults).toHaveBeenCalledWith(51)
    expect(view.state().scoped).toBe(true)
    expect(view.child.value.isDirty()).toBe(false)
    expect(view.child.value.discardChanges()).toBe(true)
  })
})
