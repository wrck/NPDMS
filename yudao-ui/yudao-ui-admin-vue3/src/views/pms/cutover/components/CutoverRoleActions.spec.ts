import { defineComponent, nextTick, type Component } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { button, findByTestId, passthrough, renderer, textOf, type TestNode } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
import DecisionForm from './CutoverApprovalDecisionForm.vue'
import FileReference from '@/components/PmsFileArtifact/PmsFileVersionReferenceList.vue'
import * as FileApi from '@/api/pms/platform/file/version-reference'

vi.mock('@/api/pms/platform/file/version-reference', () => ({ getArtifact: vi.fn(), createAccessTicket: vi.fn() }))
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => ({ warning: vi.fn() }) }))
vi.mock('@/utils/download', () => ({ default: { file: vi.fn() } }))
vi.mock('@/components/PmsFileArtifact/PmsFileVersionReferenceHistory.vue', () => ({ default: defineComponent({ render: () => null }) }))

const flush = async () => { for (let i = 0; i < 8; i++) { await Promise.resolve(); await nextTick() } }
const mountWithoutMenuPermissions = (component: Component, props: Record<string, unknown>) => {
  const root: TestNode = { type: 'root', children: [] }
  const app = renderer.createApp(component, props)
  app.provide(Symbol.for('v-scx'), { modules: new Set<string>() })
  for (const name of ['ElInput', 'ElRadio', 'ElRadioGroup', 'ElSkeleton', 'ElTag', 'ElEmpty', 'ElAlert']) app.component(name, passthrough)
  app.component('ElButton', button)
  // A role task does not add permissions to the frontend's global menu cache.
  app.directive('hasPermi', { mounted(node: TestNode) {
    if (node.parent) node.parent.children = node.parent.children.filter(child => child !== node)
  } })
  app.mount(root)
  return { root, app }
}
const findButton = (node: TestNode, label: string): TestNode | undefined => {
  if (node.type === 'button' && textOf(node) === label) return node
  for (const child of node.children) { const found = findButton(child, label); if (found) return found }
}
const key = { ownerContext: 'CUT', objectType: 'CUTOVER_PLAN', objectId: '101', purposeCode: 'FULL_PLAN', referenceKey: 'frozen-plan' }

describe('当前角色待办使用服务端允许动作', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.stubGlobal('window', { open: vi.fn() })
  })
  afterEach(() => vi.unstubAllGlobals())

  it('无全局菜单权限也能提交当前节点的五项评审，同时保留必填校验', async () => {
    const decide = vi.fn()
    const page = mountWithoutMenuPermissions(DecisionForm, { nodeCode: 'SECOND_LINE', allowedActions: ['APPROVE', 'REJECT'], busy: false, onDecide: decide })
    try {
      await flush()
      expect(findByTestId(page.root, 'approve-approval')?.props?.disabled).toBe(true)
      ;(findByTestId(page.root, 'approval-feedback')!.props!['onUpdate:modelValue'] as (value: string) => void)('平台角色评审样例')
      await flush()
      expect(findByTestId(page.root, 'approve-approval')?.props?.disabled).toBe(false)
      ;(findByTestId(page.root, 'approve-approval')!.props!.onClick as () => void)()
      expect(decide).toHaveBeenCalledWith(expect.objectContaining({ action: 'APPROVE', feedback: '平台角色评审样例', assessmentReview: null,
        reviewItems: expect.arrayContaining(['PREPARATION', 'BUSINESS_TEST', 'EXECUTION', 'ROLLBACK', 'OTHER'].map(itemCode => ({ itemCode, decision: 'YES', unreasonableReason: null }))) }))
    } finally { page.app.unmount() }
  })

  it('服务端没有授予当前节点动作时不显示审批按钮', async () => {
    const page = mountWithoutMenuPermissions(DecisionForm, { nodeCode: 'RND', allowedActions: [], busy: false })
    try { await flush(); expect(findByTestId(page.root, 'approve-approval')).toBeUndefined(); expect(findByTestId(page.root, 'reject-approval')).toBeUndefined() }
    finally { page.app.unmount() }
  })

  it('展示冻结文件的受控操作，但授权票据被拒绝时不会导航到文件', async () => {
    vi.mocked(FileApi.getArtifact).mockResolvedValue({ artifactId: '201', name: 'sample.pdf', reference: { ...key, versionNo: 1, status: 'ACTIVE' }, allowedActions: ['READ', 'PREVIEW', 'DOWNLOAD'] } as any)
    vi.mocked(FileApi.createAccessTicket).mockRejectedValue(new Error('role task completed'))
    const popup = { opener: {}, location: { replace: vi.fn() }, close: vi.fn() }
    vi.mocked(window.open).mockReturnValue(popup as any)
    const page = mountWithoutMenuPermissions(FileReference, { ...key, artifactId: '201', versionNo: 1 })
    try {
      await flush()
      expect(findButton(page.root, '下载')).toBeDefined()
      expect(findButton(page.root, '预览')).toBeDefined()
      await (findButton(page.root, '预览')!.props!.onClick as () => Promise<void>)()
      await flush()
      expect(FileApi.createAccessTicket).toHaveBeenCalledWith('201', 1, 'PREVIEW', key)
      expect(popup.location.replace).not.toHaveBeenCalled()
      expect(popup.close).toHaveBeenCalledOnce()
      expect(textOf(page.root)).toContain('文件预览未完成')
    } finally { page.app.unmount() }
  })

  it('只授予文件读取时不展示预览和下载', async () => {
    vi.mocked(FileApi.getArtifact).mockResolvedValue({ artifactId: '201', name: 'sample.pdf', reference: { ...key, versionNo: 1, status: 'ACTIVE' }, allowedActions: ['READ'] } as any)
    const page = mountWithoutMenuPermissions(FileReference, { ...key, artifactId: '201', versionNo: 1 })
    try { await flush(); expect(findButton(page.root, '预览')).toBeUndefined(); expect(findButton(page.root, '下载')).toBeUndefined() }
    finally { page.app.unmount() }
  })
})
