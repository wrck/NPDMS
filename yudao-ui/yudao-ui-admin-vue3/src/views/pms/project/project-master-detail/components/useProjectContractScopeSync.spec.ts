import { defineComponent, h, nextTick, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useProjectContractScopeSync } from './useProjectContractScopeSync'
import * as api from '@/api/pms/project/project-splits/contract-scope'
import * as projects from '@/api/pms/project/projects'
import * as commerce from '@/api/pms/commerce'
import { mount } from '../../../platform/dynamic-form/components/runtimeTestHarness'

vi.mock('@/api/pms/project/project-splits/contract-scope', async original => ({
  ...await original<typeof api>(), previewContractScope: vi.fn(), synchronizeContractScope: vi.fn()
}))
vi.mock('@/config/axios', () => ({ default: {} }))
vi.mock('@/api/pms/project/projects', () => ({ getProject: vi.fn(), queryTree: vi.fn() }))
vi.mock('@/api/pms/commerce', () => ({ getDeliveryScopeVersion: vi.fn() }))
vi.mock('./useProjectSplitWorkspace', () => ({ splitErrorMessage: (failure: Error) => failure.message }))
vi.mock('@/utils', () => { let count = 0; return { generateUUID: () => `sync-${++count}` } })

const preview: api.ContractScopePreview = { projectId: 100, scopeVersion: 5, sourceDigest: 'source-a', lines: [], errors: [], changedLineCount: 2, valid: true }
const flush = async () => { for (let index = 0; index < 10; index++) { await Promise.resolve(); await nextTick() } }
const setup = () => {
  const projectId = ref(100), saved = vi.fn()
  let state!: ReturnType<typeof useProjectContractScopeSync>
  const view = mount(defineComponent({ setup() { state = useProjectContractScopeSync(projectId, saved); return () => h('div') } }))
  return { projectId, saved, state, ...view }
}
describe('contract order synchronization', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(projects.getProject).mockResolvedValue({ version: 3 } as never)
    vi.mocked(projects.queryTree).mockResolvedValue({ treeVersion: 4, updating: false } as never)
    vi.mocked(commerce.getDeliveryScopeVersion).mockResolvedValue(5)
    vi.mocked(api.previewContractScope).mockResolvedValue(structuredClone(preview))
    vi.mocked(api.synchronizeContractScope).mockResolvedValue({ projectId: 100, scopeVersion: 6, changedLineCount: 2 })
  })
  it('previews current owner versions and submits no client-selected order lines or quantities', async () => {
    const view = setup(); await flush()
    expect(api.previewContractScope).toHaveBeenCalledWith(100, { expectedProjectVersion: 3, expectedTreeVersion: 4, expectedScopeVersion: 5 })
    await view.state.submit()
    expect(api.synchronizeContractScope).toHaveBeenCalledWith(100,
      { expectedProjectVersion: 3, expectedTreeVersion: 4, expectedScopeVersion: 5, sourceDigest: 'source-a' }, expect.any(String))
    expect(view.saved).toHaveBeenCalledTimes(1)
    await view.state.submit()
    expect(api.synchronizeContractScope).toHaveBeenCalledTimes(1)
    view.app.unmount()
  })
  it('reuses the same command after a lost response and blocks concurrent clicks', async () => {
    let fail!: (reason: Error) => void
    vi.mocked(api.synchronizeContractScope).mockImplementationOnce(() => new Promise((_, reject) => { fail = reject }))
    const view = setup(); await flush()
    const pending = view.state.submit()
    await view.state.submit()
    expect(api.synchronizeContractScope).toHaveBeenCalledTimes(1)
    fail(new Error('响应中断')); await pending
    expect(view.state.error.value).toBe('响应中断')
    await view.state.submit()
    const calls = vi.mocked(api.synchronizeContractScope).mock.calls
    expect(calls[0][2]).toBe(calls[1][2]); expect(view.saved).toHaveBeenCalledTimes(1)
    view.app.unmount()
  })
  it('requires a fresh preview when sources change and uses a new command for the new source', async () => {
    vi.mocked(api.synchronizeContractScope).mockRejectedValueOnce(new Error('CONTRACT_SCOPE_SOURCE_CHANGED'))
    const view = setup(); await flush(); await view.state.submit()
    expect(view.state.error.value).toContain('预览后发生变化')
    expect(view.saved).not.toHaveBeenCalled()
    vi.mocked(api.previewContractScope).mockResolvedValueOnce({ ...preview, sourceDigest: 'source-b' })
    await view.state.refresh(); await view.state.submit()
    const calls = vi.mocked(api.synchronizeContractScope).mock.calls
    expect(calls[0][2]).not.toBe(calls[1][2]); expect(calls[1][1].sourceDigest).toBe('source-b')
    view.app.unmount()
  })
  it('does not submit conflicts or a no-change preview', async () => {
    vi.mocked(api.previewContractScope).mockResolvedValueOnce({ ...preview, valid: false, errors: ['ACCEPTANCE_SCOPE_LOCKED'] })
    const view = setup(); await flush(); await view.state.submit()
    expect(api.synchronizeContractScope).not.toHaveBeenCalled()
    vi.mocked(api.previewContractScope).mockResolvedValueOnce({ ...preview, changedLineCount: 0 })
    await view.state.refresh(); await view.state.submit()
    expect(api.synchronizeContractScope).not.toHaveBeenCalled()
    view.app.unmount()
  })
  it('does not preview against a tree under reconstruction', async () => {
    vi.mocked(projects.queryTree).mockResolvedValueOnce({ treeVersion: 4, updating: true } as never)
    const view = setup(); await flush()
    expect(view.state.error.value).toContain('项目关系正在更新')
    expect(api.previewContractScope).not.toHaveBeenCalled()
    view.app.unmount()
  })
  it('ignores an old project response after the route changes', async () => {
    let finish!: (value: api.ContractScopePreview) => void
    vi.mocked(api.previewContractScope).mockImplementationOnce(() => new Promise(resolve => { finish = resolve }))
    const view = setup(); await flush()
    view.projectId.value = 101; await flush()
    finish({ ...preview, projectId: 100, sourceDigest: 'obsolete' }); await flush()
    expect(view.state.preview.value?.sourceDigest).toBe('source-a')
    await view.state.submit()
    expect(api.synchronizeContractScope).toHaveBeenLastCalledWith(101, expect.anything(), expect.any(String))
    view.app.unmount()
  })
})
