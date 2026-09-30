import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h } from 'vue'

// 工作台结构契约：项目内直接展开方案正文（无列表+弹窗），全局保留台账列表。
// 章节表单/分级审核弹窗打桩，只断言 index 自身的结构层次与状态动作。
const { getSolutionPage, getSolution, policyMock, getProject, submitSolution, createSolution, tieredOpen } = vi.hoisted(() => ({
  getSolutionPage: vi.fn(),
  getSolution: vi.fn(),
  policyMock: vi.fn(),
  getProject: vi.fn(),
  submitSolution: vi.fn(),
  createSolution: vi.fn(),
  tieredOpen: vi.fn()
}))

vi.mock('@/api/pms/engineering/solution', () => ({
  getSolutionPage,
  getSolution,
  createSolution,
  updateSolution: vi.fn(),
  deleteSolution: vi.fn(),
  submitSolution,
  generateDraft: vi.fn()
}))
vi.mock('@/api/pms/engineering/solution-review', () => ({ policy: policyMock }))
// 模板里 `import * as ProjectApi` 被编译为 unref 访问，mock 命名空间需放行 __v_isRef 探测
vi.mock('@/api/pms/project/projects', () => ({ __v_isRef: false, getProjectPage: vi.fn(), getProjectMembers: vi.fn(), getProject }))
vi.mock('@/utils/permission', () => ({ checkPermi: () => true }))
vi.mock('@/utils/dict', () => ({
  DICT_TYPE: { PMS_APPROVAL_STATUS: 'pms_approval_status' },
  getIntDictOptions: () => [],
  getDictLabel: () => ''
}))
vi.mock('@/hooks/web/useMessage', () => ({
  useMessage: () => ({
    success: vi.fn(),
    warning: vi.fn(),
    error: vi.fn(),
    confirm: vi.fn(async () => true),
    delConfirm: vi.fn(async () => true)
  })
}))
vi.mock('./SolutionReviewChapterForm.vue', () => ({
  default: defineComponent({
    props: {
      readOnly: { type: Boolean, default: false },
      saving: { type: Boolean, default: false }
    },
    emits: ['save', 'submit-review'],
    setup(props, { emit }) {
      return () =>
        h('div', { 'data-testid': 'chapter-form-stub' }, [
          `CHAPTER readOnly=${props.readOnly}`,
          h('button', { 'data-testid': 'stub-submit', onClick: () => emit('submit-review') }, '提交审核')
        ])
    }
  })
}))
vi.mock('./SolutionDocMetaForm.vue', () => ({
  default: defineComponent({
    props: {
      readOnly: { type: Boolean, default: false }
    },
    setup(props) {
      return () => h('div', { 'data-testid': 'meta-form-stub' }, `META readOnly=${props.readOnly}`)
    }
  })
}))
vi.mock('./SolutionTieredReviewDialog.vue', () => ({
  default: defineComponent({
    setup(_, { expose }) {
      expose({ open: tieredOpen })
      return () => h('div', 'TIERED_REVIEW_STUB')
    }
  })
}))

import Workbench from './index.vue'
import {
  findByTestId,
  mount,
  passthrough,
  tableColumn,
  textOf,
  type TestNode
} from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

// 模板注释会以 #comment 节点混入 textOf，断言用跳过注释的渲染文本
const renderedText = (node: TestNode): string =>
  `${node.type === '#comment' ? '' : node.text || ''}${node.children.map(renderedText).join('')}`

const buttonLabels = (node: TestNode): string[] => {
  const labels: string[] = []
  if (node.type === 'button') labels.push(textOf(node).trim())
  for (const child of node.children) labels.push(...buttonLabels(child))
  return labels
}

const findButton = (node: TestNode, label: string): TestNode | undefined => {
  if (node.type === 'button' && textOf(node).trim() === label) return node
  for (const child of node.children) {
    const found = findButton(child, label)
    if (found) return found
  }
}

// 弹窗壳：同时渲染默认与 footer 插槽，断言弹窗内动作可触达
const dialogShell = defineComponent({
  inheritAttrs: false,
  props: { modelValue: { type: Boolean, default: false } },
  setup(_, { attrs, slots }) {
    return () => h('section', attrs, [slots.default?.(), slots.footer?.()])
  }
})

const mountWorkbench = async (props: Record<string, unknown>) => {
  const { root } = mount(Workbench, props, {
    PmsEntitySelect: passthrough,
    DictTag: passthrough,
    ElSelect: passthrough,
    ElOption: passthrough,
    ElTable: passthrough,
    ElTableColumn: tableColumn,
    ElDescriptions: passthrough,
    ElDescriptionsItem: passthrough,
    ElInput: passthrough,
    ElRadioGroup: passthrough,
    ElRadio: passthrough,
    UploadFile: passthrough,
    Dialog: dialogShell
  })
  await vi.waitFor(() => expect(textOf(root)).toContain('TIERED_REVIEW_STUB'))
  return root
}

const draftRow = (overrides: Partial<Record<string, unknown>> = {}) =>
  ({ id: 11, projectId: 123, name: '实施方案', versionLabel: 'V1', status: 0, reviewLevel: 0, version: 1, ...overrides }) as any

beforeEach(() => {
  vi.clearAllMocks()
  policyMock.mockResolvedValue({ configured: true, reviewLevel: 0, reason: '' })
  getProject.mockResolvedValue({ projectCode: 'PJT-TEST-001', projectName: '测试项目' })
})

describe('实施方案页面结构', () => {
  it('项目工作台：无列表与弹窗，自动选中首个方案并内联展开正文', async () => {
    getSolutionPage.mockResolvedValue({ list: [draftRow()], total: 1 })
    getSolution.mockResolvedValue(draftRow())

    const root = await mountWorkbench({ projectId: 123 })

    // 引导（分页→自动选中→详情）完成后正文才渲染，直接呈现最终内容
    await vi.waitFor(() => expect(renderedText(root)).toContain('CHAPTER readOnly=false'))
    expect(getSolution).toHaveBeenCalledWith(11)
    const text = renderedText(root)
    expect(text).toContain('当前方案')
    // 项目内不出现全局台账的查询层，也不弹编辑弹窗；方案信息不再单设展示区
    expect(text).not.toContain('查询')
    expect(text).not.toContain('META readOnly=')
    // 草稿可编辑；普通审核方案走正文操作条的提交，头部不重复提交入口
    expect(text).toContain('CHAPTER readOnly=false')
    expect(buttonLabels(root)).toEqual(expect.arrayContaining(['新增方案', '删除']))
    expect(buttonLabels(root)).not.toContain('提交')
    expect(buttonLabels(root)).not.toContain('开始审核')
    expect(buttonLabels(root)).not.toContain('分级审核')
    expect(findByTestId(root, 'solution-review-result')).toBeFalsy()
    expect(findByTestId(root, 'solution-workbench-toolbar')).toBeTruthy()
    // 唯一提交入口：普通方案提交同样弹出审批发起框，流程内网关按冻结级别分支
    const submit = findByTestId(root, 'stub-submit')
    ;(submit!.props!.onClick as Function)()
    await vi.waitFor(() => expect(tieredOpen).toHaveBeenCalled())
    expect(submitSolution).not.toHaveBeenCalled()
  })

  it('项目工作台：加载目标未确定前不渲染任何方案内容，就绪后整块一次呈现', async () => {
    // 分页挂起模拟引导期：此时选中方案与详情均未确定
    let resolvePage: (value: { list: any[]; total: number }) => void = () => {}
    getSolutionPage.mockReturnValue(new Promise(resolve => { resolvePage = resolve }))
    getSolution.mockResolvedValue(draftRow())

    const root = await mountWorkbench({ projectId: 123 })

    // 引导未完成：工具栏与方案内容均不渲染（避免空九章闪现与内容区跳变），仅骨架占位
    expect(findByTestId(root, 'solution-workbench-toolbar')).toBeFalsy()
    expect(findByTestId(root, 'chapter-form-stub')).toBeFalsy()
    expect(renderedText(root)).not.toContain('CHAPTER')

    resolvePage({ list: [draftRow()], total: 1 })
    await vi.waitFor(() => expect(renderedText(root)).toContain('CHAPTER readOnly=false'))
    expect(findByTestId(root, 'solution-workbench-toolbar')).toBeTruthy()
    expect(getSolution).toHaveBeenCalledWith(11)
  })

  it('项目工作台：审批中的方案头部仅提供审批进度，正文只读', async () => {
    getSolutionPage.mockResolvedValue({ list: [draftRow({ id: 12, status: 2 })], total: 1 })
    getSolution.mockResolvedValue(draftRow({ id: 12, status: 2 }))

    const root = await mountWorkbench({ projectId: 123 })
    await vi.waitFor(() => expect(textOf(root)).toContain('CHAPTER readOnly=true'))
    expect(getSolution).toHaveBeenCalledWith(12)
    const labels = buttonLabels(root)

    expect(labels).toEqual(expect.arrayContaining(['审批进度']))
    expect(labels).not.toContain('通过')
    expect(labels).not.toContain('驳回')
    expect(labels).not.toContain('撤回')
    expect(labels).not.toContain('终止')
    expect(labels).not.toContain('删除')
    expect(textOf(root)).not.toContain('META readOnly=')
    expect(textOf(root)).toContain('CHAPTER readOnly=true')
  })

  it('项目工作台：重大方案与普通方案共用同一提交入口，界面无级别区分', async () => {
    policyMock.mockResolvedValue({ configured: true, reviewLevel: 1, reason: '' })
    getSolutionPage.mockResolvedValue({ list: [draftRow({ id: 13, reviewLevel: 1 })], total: 1 })
    getSolution.mockResolvedValue(draftRow({ id: 13, reviewLevel: 1 }))

    const root = await mountWorkbench({ projectId: 123 })
    await vi.waitFor(() => expect(textOf(root)).toContain('CHAPTER readOnly=false'))
    expect(getSolution).toHaveBeenCalledWith(13)
    const labels = buttonLabels(root)

    // 头部与操作列不出现任何按级别区分的入口；提交审核统一弹出审批发起框
    expect(labels).toContain('删除')
    expect(labels).not.toContain('分级审核')
    expect(labels).not.toContain('开始审核')
    expect(renderedText(root)).not.toContain('审核级别')
    expect(textOf(root)).toContain('CHAPTER readOnly=false')
    const submit = findByTestId(root, 'stub-submit')
    ;(submit!.props!.onClick as Function)()
    await vi.waitFor(() => expect(tieredOpen).toHaveBeenCalled())
    expect(submitSolution).not.toHaveBeenCalled()
  })

  it('项目工作台：项目暂无方案时进入新建草稿态', async () => {
    getSolutionPage.mockResolvedValue({ list: [], total: 0 })

    const root = await mountWorkbench({ projectId: 123 })

    expect(getSolution).not.toHaveBeenCalled()
    // 新建草稿态引导完成后整块呈现；同步弹出新增登记：客户方案须在创建弹窗一次性选定
    await vi.waitFor(() => expect(textOf(root)).toContain('CHAPTER readOnly=false'))
    expect(buttonLabels(root)).toContain('新增方案')
    await vi.waitFor(() => expect(findByTestId(root, 'solution-create-dialog')).toBeTruthy())
    // 版本标签按方案版本自动生成（项目首份为 V1），名称按 项目编码_项目名称_实施方案_版本标签 收尾
    ;(findByTestId(root, 'stub-submit')!.props!.onClick as Function)()
    await vi.waitFor(() => expect(createSolution).toHaveBeenCalled())
    const draftPayload = createSolution.mock.calls[0][0] as any
    expect(draftPayload.versionLabel).toBe('V1')
    expect(draftPayload.name).toMatch(/^PJT-TEST-001_测试项目_实施方案_V1$/)
  })

  it('项目工作台：新增方案经弹窗登记客户方案，确定后写入登记信封且不直接建记录', async () => {
    getSolutionPage.mockResolvedValue({ list: [draftRow()], total: 1 })
    getSolution.mockResolvedValue(draftRow())

    const root = await mountWorkbench({ projectId: 123 })
    await vi.waitFor(() => expect(renderedText(root)).toContain('CHAPTER readOnly=false'))

    ;(findButton(root, '新增方案')!.props!.onClick as Function)()
    await vi.waitFor(() => expect(findByTestId(root, 'solution-create-dialog')).toBeTruthy())

    ;(findButton(root, '确定')!.props!.onClick as Function)()
    // 弹窗确定只落到本地草稿，记录创建仍由保存草稿/提交审核承载
    expect(createSolution).not.toHaveBeenCalled()
    // 经提交入口建记录：登记信封随载荷写入；版本标签按方案版本自动顺延（已有 1 份 → V2）
    ;(findByTestId(root, 'stub-submit')!.props!.onClick as Function)()
    await vi.waitFor(() => expect(createSolution).toHaveBeenCalled())
    const draftPayload = createSolution.mock.calls[0][0] as any
    expect(JSON.parse(draftPayload.remark)).toMatchObject({ hasCustomerPlan: 'no', customerPlanUrl: '' })
    expect(draftPayload.versionLabel).toBe('V2')
  })

  it('全局视图：保留台账列表与编辑弹窗，不做工作台头部', async () => {
    getSolutionPage.mockResolvedValue({ list: [draftRow()], total: 1 })

    const root = await mountWorkbench({})
    const text = renderedText(root)

    expect(getSolution).not.toHaveBeenCalled()
    expect(text).toContain('查询')
    expect(text).not.toContain('当前方案')
    // 弹窗内表单随打开即渲染：未选记录时为新建草稿态（可编辑）
    expect(text).toContain('CHAPTER readOnly=false')
  })
})
