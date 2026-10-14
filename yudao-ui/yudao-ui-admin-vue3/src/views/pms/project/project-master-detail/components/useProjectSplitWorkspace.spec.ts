import { defineComponent, h, nextTick, ref } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useProjectSplitWorkspace } from './useProjectSplitWorkspace'
import * as api from '@/api/pms/project/project-splits/workspace'
import { mount } from '../../../platform/dynamic-form/components/runtimeTestHarness'

vi.mock('@/api/pms/project/project-splits/workspace', async importOriginal => ({
  ...await importOriginal<typeof api>(), getDraft: vi.fn(), createDraft: vi.fn(), updateDraft: vi.fn(),
  previewDraft: vi.fn(), applyDraft: vi.fn(), getScopeOptions: vi.fn()
}))
vi.mock('@/config/axios', () => ({ default: { get: vi.fn(), post: vi.fn(), put: vi.fn() } }))
vi.mock('@/api/system/dept', () => ({ getSimpleDeptList: vi.fn(async () => []) }))
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => ({ warning: vi.fn(), success: vi.fn() }) }))
vi.mock('@/utils', () => { let sequence = 0; return { generateUUID: () => `key-${++sequence}` } })
const fixture: api.SplitDraft = {
  id: '20261012001', parentProjectId: 100, status: 'DRAFT', draftVersion: 2,
  parentVersion: 3, scopeVersion: 4, treeVersion: 5,
  items: [{ clientItemKey: 'child-a', projectName: '子项目甲', departmentCode: 'HZ', templateRevisionId: '9001',
    scopes: [{ orderLineId: 10, departmentCode: 'HZ', allocatedQty: 1, serialNo: 'QA-A' },
      { orderLineId: 10, departmentCode: 'HZ', allocatedQty: 1, serialNo: 'QA-B' }] }]
}
const preview: api.ProjectSplitPreviewVO = { requestId: fixture.id, draftVersion: 2,
  parentVersion: 3, scopeVersion: 4, treeVersion: 5, valid: true, errors: [], items: [] }
const flush = async () => { for (let index = 0; index < 8; index++) { await Promise.resolve(); await nextTick() } }
const setup = () => {
  const projectId = ref(100), applied = vi.fn()
  let state!: ReturnType<typeof useProjectSplitWorkspace>
  const view = mount(defineComponent({ setup() { state = useProjectSplitWorkspace(projectId, applied); return () => h('div') } }))
  return { state, projectId, applied, ...view }
}

describe('project split workspace business operations', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.stubGlobal('localStorage', { getItem: vi.fn(() => fixture.id), setItem: vi.fn(), removeItem: vi.fn() })
    vi.mocked(api.getDraft).mockResolvedValue(structuredClone(fixture))
    vi.mocked(api.getScopeOptions).mockResolvedValue([])
    vi.mocked(api.updateDraft).mockResolvedValue({ ...fixture, draftVersion: 3 })
    vi.mocked(api.previewDraft).mockResolvedValue(preview)
  })
  afterEach(() => vi.unstubAllGlobals())

  it('restores SN groups and round-trips the real departmentCode contract', async () => {
    const view = setup(); await flush()
    expect(view.state.items.value[0].scopes).toEqual([{ orderLineId: 10, departmentCode: 'HZ', quantity: 2, serialNumbers: ['QA-A', 'QA-B'] }])
    await view.state.saveDraft()
    expect(api.updateDraft).toHaveBeenCalledWith(expect.anything(), expect.objectContaining({ items: [expect.objectContaining({
      departmentCode: 'HZ', scopes: [expect.objectContaining({ departmentCode: 'HZ', quantity: 2, serialNumbers: ['QA-A', 'QA-B'] })]
    })] }), expect.any(String))
    expect(vi.mocked(api.updateDraft).mock.calls[0][1].items[0]).not.toHaveProperty('officeDepartmentCode')
    view.app.unmount()
  })

  it('an edit invalidates the preview and prevents applying unvalidated changes', async () => {
    const view = setup(); await flush(); await view.state.previewDraft()
    expect(view.state.preview.value?.valid).toBe(true)
    view.state.items.value[0].projectName = '修改后的名称'
    expect(view.state.preview.value).toBeUndefined()
    await view.state.applyDraft()
    expect(api.applyDraft).not.toHaveBeenCalled()
    view.app.unmount()
  })

  it('reuses the application key after a failed response and prevents a second application after success', async () => {
    vi.mocked(api.applyDraft).mockRejectedValueOnce(new Error('连接中断')).mockResolvedValueOnce({
      requestId: fixture.id, treeVersion: 6, scopeVersion: 5, replayed: true,
      projects: [{ clientItemKey: 'child-a', projectId: 101, projectCode: 'PJT-QA-CHILD' }]
    })
    const view = setup(); await flush(); await view.state.previewDraft()
    await view.state.applyDraft(); await view.state.applyDraft(); await view.state.applyDraft()
    expect(api.applyDraft).toHaveBeenCalledTimes(2)
    expect(vi.mocked(api.applyDraft).mock.calls[0][1]).toBe(vi.mocked(api.applyDraft).mock.calls[1][1])
    expect(view.applied).toHaveBeenCalledTimes(1)
    expect(view.state.appliedAlready.value).toBe(true)
    view.app.unmount()
  })

  it('preserves an unreadable draft reference and prevents replacing it with a new draft silently', async () => {
    vi.mocked(api.getDraft).mockRejectedValueOnce(new Error('暂时无法读取'))
    const view = setup(); await flush()
    expect(view.state.restoreFailed.value).toBe(true)
    expect(localStorage.removeItem).not.toHaveBeenCalled()
    await view.state.saveDraft()
    expect(api.createDraft).not.toHaveBeenCalled()
    view.app.unmount()
  })

  it('refreshes draft watermarks before preview after the parent order scope changes', async () => {
    const view = setup(); await flush(); await view.state.previewDraft()
    view.state.refreshParentScope()
    expect(view.state.preview.value).toBeUndefined()
    expect(view.state.dirty.value).toBe(true)
    vi.mocked(api.updateDraft).mockResolvedValueOnce({ ...fixture, scopeVersion: 9, draftVersion: 3 })
    await view.state.previewDraft()
    expect(api.updateDraft).toHaveBeenCalledTimes(1)
    expect(api.previewDraft).toHaveBeenLastCalledWith(expect.objectContaining({ scopeVersion: 9, draftVersion: 3 }), expect.any(String))
    view.app.unmount()
  })

  it('ignores the previous parent response when the user changes projects', async () => {
    let resolve!: (value: api.SplitDraft) => void
    vi.mocked(api.getDraft).mockImplementationOnce(() => new Promise(done => { resolve = done }))
      .mockResolvedValueOnce({ ...fixture, id: 22, parentProjectId: 200 })
    const view = setup(); view.projectId.value = 200; await flush()
    resolve(fixture); await flush()
    expect(view.state.draft.value?.parentProjectId).toBe(200)
    expect(view.state.draft.value?.id).toBe(22)
    view.app.unmount()
  })
})
