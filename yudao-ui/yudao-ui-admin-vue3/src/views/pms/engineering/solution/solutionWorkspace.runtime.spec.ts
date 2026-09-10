import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick } from 'vue'
import Solution from './index.vue'
import * as SolutionApi from '@/api/pms/engineering/solution'
import { mount, passthrough, tableColumn, type TestNode } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

const permissions = vi.hoisted(() => ({ write: true }))
const warning = vi.hoisted(() => vi.fn())
vi.mock('@/utils/permission', () => ({ checkPermi: () => permissions.write }))
vi.mock('@/api/pms/project/project', () => ({ __v_isRef: false, getProjectPage: vi.fn() }))
vi.mock('@/api/pms/engineering/solution', () => ({ getSolutionPage: vi.fn(), getSolution: vi.fn(), createSolution: vi.fn(), updateSolution: vi.fn(), deleteSolution: vi.fn(), generateDraft: vi.fn() }))
vi.mock('@/utils/dict', () => ({ DICT_TYPE: { PMS_APPROVAL_STATUS: 'approval', PMS_REVIEW_LEVEL: 'review' }, getIntDictOptions: () => [] }))
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => ({ warning, success: vi.fn(), delConfirm: vi.fn() }) }))
const formStub = defineComponent({ setup(_, { slots, attrs, expose }) {
  expose({ validate: async () => true })
  return () => h('form', attrs, slots.default?.())
} })
const editorStub = defineComponent({ setup(_, { attrs }) { return () => h('editor', attrs) } })
const editors = (node: TestNode): TestNode[] => [...(node.type === 'editor' ? [node] : []), ...node.children.flatMap(editors)]
const flush = async () => { for (let i = 0; i < 5; i++) await nextTick() }
const render = () => {
  const mounted = mount(Solution, {}, { ElTable: passthrough, ElTableColumn: tableColumn, ElForm: formStub, ElRow: passthrough, ElCol: passthrough, ElInput: passthrough, ElSelect: passthrough, ElOption: passthrough, PmsEntitySelect: passthrough, Editor: editorStub })
  return { ...mounted, state: (mounted.vm as any).$.setupState }
}
const approved = { id: 8, projectId: 1, code: 'SOL-APPROVED', name: '已通过方案', status: 3, version: 4, baselineVersion: 4, approvedBy: 17, approvalOpinion: '已通过', background: '<p>冻结正文</p>' }

describe('existing solution drafting and read-only viewing', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    permissions.write = true
    vi.mocked(SolutionApi.getSolutionPage).mockResolvedValue({ list: [], total: 0 })
    vi.mocked(SolutionApi.getSolution).mockImplementation(async id => id === 8 ? { ...approved } : { id, projectId: 1, code: 'SOL-DRAFT', name: '草稿', status: 0, version: 2 })
  })

  it('keeps the unavailable major-review route out of the ordinary approval dialog', async () => {
    const mounted = render()
    try {
      mounted.state.openApprove({ ...approved, status: 2, reviewLevel: 1 }, 'approve')
      expect(mounted.state.approveVisible).toBe(false)
      expect(warning).toHaveBeenCalledWith('重大方案复审尚未接入，不能直接通过')
    } finally { mounted.app.unmount() }
  })

  it('opens approved content read-only and does not issue save or delete commands', async () => {
    const mounted = render()
    try {
      mounted.state.openForm(approved); await flush()
      expect(mounted.state.readOnly).toBe(true)
      expect(editors(mounted.root)).toHaveLength(6)
      expect(editors(mounted.root).every(editor => editor.props?.readonly === true)).toBe(true)
      await mounted.state.save()
      await mounted.state.remove(approved)
      expect(SolutionApi.updateSolution).not.toHaveBeenCalled()
      expect(SolutionApi.deleteSolution).not.toHaveBeenCalled()
    } finally { mounted.app.unmount() }
  })

  it('does not carry approval or status fields into a new manual draft after viewing a baseline', async () => {
    const mounted = render()
    try {
      mounted.state.openForm(approved); await flush()
      mounted.state.openForm(); await flush()
      Object.assign(mounted.state.form, { projectId: 1, code: 'SOL-NEW', name: '新草稿' })
      expect(mounted.state.readOnly).toBe(false)
      await mounted.state.save()
      const payload = vi.mocked(SolutionApi.createSolution).mock.calls[0][0]
      expect(payload.status).toBe(0)
      expect(payload.baselineVersion).toBeUndefined()
      expect(payload.approvedBy).toBeUndefined()
      expect(payload.approvalOpinion).toBeUndefined()
      expect(SolutionApi.generateDraft).not.toHaveBeenCalled()
    } finally { mounted.app.unmount() }
  })

  it('keeps existing draft editing and its optimistic version payload', async () => {
    const mounted = render()
    try {
      mounted.state.openForm({ id: 9, projectId: 1, code: 'SOL-DRAFT', name: '草稿', status: 0, version: 2 }); await flush()
      expect(editors(mounted.root).every(editor => editor.props?.readonly === false)).toBe(true)
      mounted.state.form.name = '更新草稿'
      await mounted.state.save()
      expect(SolutionApi.updateSolution).toHaveBeenCalledWith(expect.objectContaining({ id: 9, name: '更新草稿', status: 0, version: 2 }))
    } finally { mounted.app.unmount() }
  })

  it('does not make a draft writable for a read-only operator', async () => {
    permissions.write = false
    vi.mocked(SolutionApi.getSolution).mockResolvedValue({ ...approved, status: 0 })
    const mounted = render()
    try {
      mounted.state.openForm({ ...approved, status: 0 }); await flush()
      expect(mounted.state.readOnly).toBe(true)
      await mounted.state.save()
      expect(SolutionApi.updateSolution).not.toHaveBeenCalled()
    } finally { mounted.app.unmount() }
  })

  it('reopens the current server record instead of the stale list row and displays review results', async () => {
    const mounted = render()
    try {
      vi.mocked(SolutionApi.getSolution).mockResolvedValue({ ...approved, approvalOpinion: '请补充部署步骤', status: 4, version: 5 })
      await mounted.state.openForm({ ...approved, status: 0, version: 1 })
      await flush()
      expect(SolutionApi.getSolution).toHaveBeenCalledWith(8)
      expect(mounted.state.form).toMatchObject({ status: 4, version: 5, approvalOpinion: '请补充部署步骤' })
      expect(mounted.state.readOnly).toBe(true)
      const find = (node: TestNode): TestNode[] => [...(node.props?.['data-testid'] === 'solution-review-result' ? [node] : []), ...node.children.flatMap(find)]
      expect(find(mounted.root)).toHaveLength(1)
      const text = (node: TestNode): string => (node.text || '') + node.children.map(text).join('')
      expect(text(find(mounted.root)[0])).toContain('请补充部署步骤')
    } finally { mounted.app.unmount() }
  })

  it('blocks saving while loading and after failure, then supports reloading the same record', async () => {
    let fail!: (reason: Error) => void
    vi.mocked(SolutionApi.getSolution).mockReturnValueOnce(new Promise((_resolve, reject) => { fail = reject }))
    const mounted = render()
    try {
      const opening = mounted.state.openForm({ ...approved, status: 0 })
      expect(mounted.state.readOnly).toBe(true)
      await mounted.state.save()
      fail(new Error('network unavailable'))
      await opening
      expect(mounted.state.detailError).toContain('加载失败')
      await mounted.state.save()
      expect(SolutionApi.updateSolution).not.toHaveBeenCalled()
      expect(SolutionApi.createSolution).not.toHaveBeenCalled()
      vi.mocked(SolutionApi.getSolution).mockResolvedValue({ ...approved, status: 0 })
      await mounted.state.openForm(mounted.state.form)
      expect(mounted.state.detailError).toBe('')
      expect(mounted.state.readOnly).toBe(false)
    } finally { mounted.app.unmount() }
  })

  it('does not replace a new draft with a late response from the previously opened record', async () => {
    let resolve!: (row: typeof approved) => void
    vi.mocked(SolutionApi.getSolution).mockReturnValueOnce(new Promise(done => { resolve = done }))
    const mounted = render()
    try {
      const opening = mounted.state.openForm(approved)
      await mounted.state.openForm()
      mounted.state.form.name = '正在编制的新方案'
      resolve(approved)
      await opening
      expect(mounted.state.form.id).toBeUndefined()
      expect(mounted.state.form.name).toBe('正在编制的新方案')
      expect(mounted.state.detailLoading).toBe(false)
    } finally { mounted.app.unmount() }
  })

  it('keeps an unavailable record closed to editing and rejects an unselected project', async () => {
    vi.mocked(SolutionApi.getSolution).mockResolvedValue(null)
    const mounted = render()
    try {
      await mounted.state.openForm(approved)
      expect(mounted.state.readOnly).toBe(true)
      expect(mounted.state.detailError).toContain('不存在')
      const callback = vi.fn()
      mounted.state.rules.projectId[1].validator({}, 0, callback)
      expect(callback).toHaveBeenCalledWith(expect.any(Error))
      callback.mockClear()
      mounted.state.rules.projectId[1].validator({}, 1001, callback)
      expect(callback).toHaveBeenCalledWith(undefined)
    } finally { mounted.app.unmount() }
  })
})
