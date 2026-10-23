// @vitest-environment happy-dom
import { afterEach, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, provide, ref, shallowRef } from 'vue'
import { mount, passthrough } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
import { operationClientKey, operationCheckKey, useOperationHost } from '@/components/BusinessView/operationHost'
import { inspectOperationCapabilities } from '@/api/pms/project/execution-operations'
import request from '@/config/axios'
import Editor from './ReportDraftEditor.vue'

vi.mock('@/api/pms/project/execution-operations', () => ({ inspectOperationCapabilities: vi.fn() }))
vi.mock('@/config/axios', () => ({ default: { post: vi.fn() } }))
vi.mock('@/config/axios/service', () => ({ service: { defaults: { transformResponse: [] } } }))
vi.mock('@/store/modules/user', () => ({ useUserStore: () => ({ getUser: { id: 1, nickname: '验收用户' } }) }))
vi.mock('@/utils/permission', () => ({ checkPermi: () => true }))
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => ({ warning: vi.fn(), success: vi.fn(), confirm: vi.fn() }) }))
vi.mock('@/components/PmsFileArtifact/PmsFileVersionReferenceList.vue', () => ({ default: { setup: () => () => null } }))
vi.mock('@/components/PmsFileArtifact', async () => {
  const { defineComponent } = await import('vue')
  return { PmsFileUploader: defineComponent({ setup(_, { expose }) {
    expose({ isBusy: () => false, hasPendingFile: () => false })
    return () => null
  } }) }
})

const execution = { task: { projectId: '7', taskId: '2', executionContractId: '3', contractVersion: 1, planVersionId: '4', executionId: '5' }, stage: null }
const capability = (allowed = true) => ({
  node: { projectId: '7', id: '2', kind: 'TASK', status: 'IN_PROGRESS' }, execution,
  reason: null, presentation: { status: 'AVAILABLE' }, ownerFactVersion: 'v1',
  actions: ['UPDATE_DRAFT', 'PUBLISH'].map(code => ({ operationCode: `ACC.ACCEPTANCE_REPORT.${code}`, operationVersion: 1,
    ownerPermitted: allowed, executionPermitted: allowed, runtimeAvailable: true, allowed,
    pre: { outcome: 'NO_ADDITIONAL_RULE' }, post: { outcome: 'NOT_EVALUATED' } }))
})
const flush = async () => { for (let i = 0; i < 12; i++) { await Promise.resolve(); await nextTick() } }
const apps: Array<{ unmount: () => void }> = []
afterEach(() => { apps.splice(0).forEach(app => app.unmount()); vi.resetAllMocks() })

it.each([true, false])('waits for an embedded capability refresh and then honors publish authority=%s', async allowed => {
  vi.mocked(inspectOperationCapabilities).mockResolvedValue(capability() as never)
  const editor = ref<any>()
  let host!: ReturnType<typeof useOperationHost>
  const root = mount(defineComponent({ setup() {
    host = useOperationHost(shallowRef({
      registration: { id: '8', componentKey: 'ACC_ACCEPTANCE_REPORT', componentVersion: '1', ownerContext: 'ACC', entityType: 'ACCEPTANCE', viewSource: 'PAGE' },
      resolvedContext: { project: { id: '7' }, taskId: '2', taskExecution: execution.task, businessObjectId: '12' },
      allowedActions: ['QUERY', 'UPDATE', 'PUBLISH', 'FILE_WRITE']
    } as any), vi.fn())
    provide(operationClientKey, host.client)
    provide(operationCheckKey, host.waitForCheck)
    return () => h(Editor, { ref: editor, allowedActions: host.allowedActions.value })
  } }), {}, { ElRow: passthrough, ElCol: passthrough })
  apps.push(root.app)
  await flush()
  await editor.value.open({ id: 12, projectId: 7, activityStatus: 'PENDING', acceptanceType: 'PRELIMINARY', version: 0 },
    { id: 31, acceptanceId: 12, reportVersionNo: 1, reportStatus: 'DRAFT', attachments: [{ sequence: 1, artifactId: 11, versionNo: 1, referenceKey: 'saved' }] })
  let finishRefresh!: (value: any) => void
  let refresh!: Promise<void>
  vi.mocked(request.post).mockImplementationOnce(async () => {
    vi.mocked(inspectOperationCapabilities).mockImplementationOnce(() => new Promise(resolve => { finishRefresh = resolve }))
    refresh = host.refresh(true)
    await flush()
    return { ownerContext: 'ACC', objectType: 'ACCEPTANCE', objectId: '12', revisionId: '31', objectVersion: 0,
      businessFactVersion: 'v2', resultCode: 'REPORT_DRAFT_SAVED', replayed: false,
      response: { acceptanceId: 12, reportVersionId: 31, reportVersionNo: 1, reportStatus: 'DRAFT', replayed: false } } as any
  }).mockResolvedValueOnce({ ownerContext: 'ACC', objectType: 'ACCEPTANCE', objectId: '12', revisionId: '31', objectVersion: 1,
    businessFactVersion: 'v3', resultCode: 'REPORT_VERSION_PUBLISHED', replayed: false,
    response: { acceptanceId: 12, reportVersionId: 31, reportVersionNo: 1, reportStatus: 'EFFECTIVE', replayed: false } } as any)
  const state = editor.value.$.setupState
  let done = false
  const saving = state.saveAndComplete().then((value: boolean) => { done = true; return value })
  await flush(); await flush(); await flush(); await flush()
  expect(host.checking.value).toBe(true)
  expect(done).toBe(false)
  finishRefresh(capability(allowed))
  await refresh; await flush()
  vi.mocked(inspectOperationCapabilities).mockResolvedValue(capability(allowed) as never)
  expect(await saving).toBe(allowed)
  expect(request.post).toHaveBeenCalledTimes(allowed ? 2 : 1)
  expect(state.visible).toBe(!allowed)
})
