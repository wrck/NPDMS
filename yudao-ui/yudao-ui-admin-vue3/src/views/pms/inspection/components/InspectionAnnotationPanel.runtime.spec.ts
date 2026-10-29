import { beforeEach, expect, it, vi } from 'vitest'
import { defineComponent, h, inject, nextTick, provide, reactive } from 'vue'
import Panel from './InspectionAnnotationPanel.vue'
import {
  mount,
  passthrough,
  tableColumn,
  textOf,
  findByTestId,
  type TestNode
} from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
const confirmDiscard = vi.hoisted(() => vi.fn())
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => ({ confirm: confirmDiscard }) }))
beforeEach(() => confirmDiscard.mockReset())

vi.mock('@/views/system/user/components/UserSelect.vue', async () => {
  const { defineComponent, h } = await import('vue')
  return {
    default: defineComponent({
      props: { modelValue: [String, Number], currentLabel: String },
      setup: (props) => () =>
        h('user-select', { modelValue: props.modelValue, currentLabel: props.currentLabel })
    })
  }
})
vi.mock('@/utils/dict', () => ({ getStrDictOptions: () => [] }))
vi.mock('@vueuse/core', () => ({ useMediaQuery: () => false }))
vi.mock('@/utils/formatTime', () => ({ formatDate: () => '' }))
vi.mock('@/api/system/user', () => ({ getSimpleUserList: vi.fn() }))
vi.mock('@/store/modules/user', () => ({
  useUserStore: () => ({ getUser: { id: '9', nickname: '当前工程师' } })
}))

const render = (actionsTarget?: TestNode, renderEditor = false, umcMode = false) => {
  const task = reactive({
    id: '10',
    version: '1',
    currentReportId: '20',
    statusCode: 'ANNOTATION_PENDING',
    reports: [{ id: '20', statusCode: 'AVAILABLE', resultIds: ['31', '32'] }],
    results: [
      { id: '31', outcome: 'ABNORMAL', ruleName: undefined as string | undefined },
      { id: '32', outcome: 'ABNORMAL' }
    ],
    annotations: [],
    devices: []
  })
  const annotate = vi.fn()
  const page = mount(
    Panel,
    { task, disabled: false, onAnnotate: annotate, actionsTarget, umcMode },
    {
      ElDescriptions: passthrough,
      ElDescriptionsItem: passthrough,
      ElForm: defineComponent({
        setup(_, { expose, slots }) {
          expose({ clearValidate: vi.fn(), validate: async () => true })
          return () => h('section', slots.default?.())
        }
      }),
      ElTable: defineComponent({
        props: { data: Array },
        setup(props, { expose, slots }) {
          provide('inspection-test-table-row', () => props.data?.[0])
          expose({ toggleRowExpansion: vi.fn() })
          return () => h('section', slots.default?.())
        }
      }),
      ElTableColumn: renderEditor
        ? defineComponent({
            props: { label: String },
            setup: (props, { slots }) => {
              const row = inject<() => unknown>('inspection-test-table-row', () => undefined)
              return () => {
                const item = row() || task.results[0]
                return h(
                  'section',
                  { label: props.label },
                  item ? slots.default?.({ row: item }) : undefined
                )
              }
            }
          })
        : tableColumn,
      ElInput: passthrough,
      ElRadioGroup: passthrough,
      ElRadio: passthrough,
      ElSelect: passthrough,
      ElOption: passthrough,
      ElDatePicker: passthrough,
      ElCollapse: passthrough,
      ElTag: passthrough,
      ElDialog: defineComponent({
        props: { modelValue: Boolean, title: String },
        setup:
          (props, { slots }) =>
          () =>
            props.modelValue
              ? h('section', { role: 'dialog', 'aria-label': props.title }, [
                  slots.default?.(),
                  slots.footer?.()
                ])
              : null
      }),
      ElCollapseItem: defineComponent({
        setup:
          (_, { slots }) =>
          () =>
            h('article', [slots.title?.(), slots.default?.()])
      })
    }
  )
  return { ...page, task, annotate, state: (page.vm as any).$.setupState }
}

it('preserves expanded and collapsed rows when an annotation save refreshes task data', async () => {
  const page = render()
  try {
    page.state.expandedResultIds = ['31']
    page.state.openEditor(page.task.results[0])
    await nextTick()
    page.state.form.kind = 'IGNORE'
    page.state.form.reason = 'reviewed evidence'
    page.state.formRef = { validate: async () => true, clearValidate: vi.fn() }
    await page.state.submitAnnotation()
    expect(page.annotate).toHaveBeenCalledWith(
      expect.objectContaining({ resultId: '31', kind: 'IGNORE' })
    )
    expect(page.state.selectedResultId).toBe('31')
    page.task.version = '2'
    page.task.results = page.task.results.map((row) => ({ ...row }))
    await nextTick()
    expect(page.state.expandedResultIds).toEqual(['31'])
    expect(page.state.selectedResultId).toBe('')
    expect(page.state.dirty).toBe(false)
    page.state.expandedResultIds = []
    page.task.version = '3'
    await nextTick()
    expect(page.state.expandedResultIds).toEqual([])
  } finally {
    page.app.unmount()
  }
})

it('keeps one validated save action inside the annotation dialog instead of the page action bar', async () => {
  const target: TestNode = { type: 'footer', children: [] }
  const page = render(target, true)
  const nodes = (node: TestNode): TestNode[] => [node, ...node.children.flatMap(nodes)]
  try {
    page.state.openEditor(page.task.results[0])
    await nextTick()
    page.state.form.kind = 'IGNORE'
    page.state.form.reason = '已核对原始证据'
    page.state.formRef = { validate: async () => true, clearValidate: vi.fn() }
    const dialog = findByTestId(page.root, 'inspection-annotation-editor')!
    expect(dialog).toBeTruthy()
    const save = nodes(dialog).find(
      (node) => node.type === 'button' && textOf(node) === '保存标注'
    )!
    expect(save.props?.['native-type']).toBe('button')
    await (save.props!.onClick as Function)()
    expect(page.annotate).toHaveBeenCalledTimes(1)
    expect(page.annotate).toHaveBeenCalledWith(
      expect.objectContaining({ resultId: '31', kind: 'IGNORE', reason: '已核对原始证据' })
    )
    expect(
      nodes(target).filter((node) => node.type === 'button' && textOf(node) === '保存标注')
    ).toHaveLength(0)
  } finally {
    page.app.unmount()
  }
})

it.each([false, true])(
  'opens the shared annotation dialog without expanding the result row (UMC=%s)',
  async (umcMode) => {
    const page = render(undefined, true, umcMode)
    try {
      page.task.results[0].ruleName = 'CPU异常检查'
      page.state.openEditor(page.task.results[0])
      await nextTick()
      const dialog = findByTestId(page.root, 'inspection-annotation-editor')!
      expect(dialog?.props?.role).toBe('dialog')
      expect(dialog.props?.['aria-label']).toContain('填写标注')
      expect(dialog.props?.['aria-label']).toContain('CPU异常检查')
      expect(textOf(dialog)).toContain('保存标注')
      expect(page.state.expandedResultIds).toEqual([])
    } finally {
      page.app.unmount()
    }
  }
)

it('retains a dirty dialog when close is cancelled and discards only after confirmation', async () => {
  const page = render(undefined, true, true)
  try {
    page.state.openEditor(page.task.results[0])
    page.state.form.kind = 'IGNORE'
    page.state.form.reason = '尚未保存的依据'
    await nextTick()
    const done = vi.fn()
    confirmDiscard.mockRejectedValueOnce(new Error('cancel'))
    await page.state.closeEditor(done)
    expect(done).not.toHaveBeenCalled()
    expect(page.state.selectedResultId).toBe('31')
    expect(page.state.form.reason).toBe('尚未保存的依据')
    confirmDiscard.mockResolvedValueOnce(undefined)
    await page.state.closeEditor(done)
    expect(done).toHaveBeenCalledTimes(1)
    expect(page.state.selectedResultId).toBe('')
    expect(page.state.dirty).toBe(false)
    expect(page.annotate).not.toHaveBeenCalled()
  } finally {
    page.app.unmount()
  }
})

it('requires confirmation before switching away from another unsaved annotation', async () => {
  const page = render()
  try {
    page.state.openEditor(page.task.results[0])
    page.state.form.kind = 'IGNORE'
    page.state.form.reason = '第一项未保存'
    confirmDiscard.mockRejectedValueOnce(new Error('cancel'))
    await page.state.openEditor(page.task.results[1])
    expect(page.state.selectedResultId).toBe('31')
    expect(page.state.form.reason).toBe('第一项未保存')
    confirmDiscard.mockResolvedValueOnce(undefined)
    await page.state.openEditor(page.task.results[1])
    expect(page.state.selectedResultId).toBe('32')
    expect(page.state.form.reason).toBe('')
    expect(page.annotate).not.toHaveBeenCalled()
  } finally {
    page.app.unmount()
  }
})

it('does not close or switch the annotation dialog during validation', async () => {
  const page = render()
  try {
    page.state.openEditor(page.task.results[0])
    page.state.validating = true
    await page.state.closeEditor()
    await page.state.openEditor(page.task.results[1])
    expect(page.state.selectedResultId).toBe('31')
    expect(confirmDiscard).not.toHaveBeenCalled()
  } finally {
    page.app.unmount()
  }
})

it('keeps invalid annotation input in the dialog without submitting or closing', async () => {
  const page = render()
  try {
    page.state.openEditor(page.task.results[0])
    await nextTick()
    page.state.form.kind = 'IGNORE'
    page.state.formRef = { validate: async () => false, clearValidate: vi.fn() }
    await page.state.submitAnnotation()
    expect(page.annotate).not.toHaveBeenCalled()
    expect(page.state.selectedResultId).toBe('31')
    expect(page.state.form.kind).toBe('IGNORE')
  } finally {
    page.app.unmount()
  }
})

it('clears expansion when switching to another report or task', async () => {
  const page = render()
  try {
    page.state.expandedResultIds = ['31']
    page.task.currentReportId = '21'
    await nextTick()
    expect(page.state.expandedResultIds).toEqual([])
    page.state.expandedResultIds = ['32']
    page.task.id = '11'
    await nextTick()
    expect(page.state.expandedResultIds).toEqual([])
  } finally {
    page.app.unmount()
  }
})

it('uses the Demo issue table with a shared details dialog for only frozen abnormal items and no internal IDs', async () => {
  const target: TestNode = { type: 'footer', children: [] }
  const page = render(target, true, true)
  try {
    page.task.results = [
      {
        id: '31',
        deviceId: '40',
        outcome: 'ABNORMAL',
        ruleName: 'CPU异常繁忙检查',
        externalRule: {
          id: '99',
          ruleid: 'INTERNAL-RULE-ID',
          info: 'VCPU有历史异常记录，请及时检查',
          cmd: 'show busy-cpu all',
          output: '完整长命令输出'.repeat(300),
          targetlist: []
        }
      },
      { id: '32', outcome: 'PASS', ruleName: '正常项目不可确认' },
      { id: '99', outcome: 'ABNORMAL', ruleName: '新执行结果不能混入冻结报告' }
    ] as any
    await nextTick()
    expect(page.state.abnormalResults.map((row: any) => row.id)).toEqual(['31'])
    const nodes = (node: TestNode): TestNode[] => [node, ...node.children.flatMap(nodes)]
    expect(nodes(page.root).map((node) => node.props?.label)).toEqual(
      expect.arrayContaining(['序号', '检出问题', '严重程度', '标注', '标注操作'])
    )
    expect(
      nodes(page.root).some((node) => node.props?.['data-testid'] === 'inspection-umc-items')
    ).toBe(false)
    expect(textOf(page.root)).toContain('CPU异常繁忙检查')
    expect(textOf(page.root)).toContain('VCPU有历史异常记录，请及时检查')
    expect(textOf(page.root)).not.toContain('正常项目不可确认')
    expect(textOf(page.root)).not.toContain('新执行结果不能混入冻结报告')
    const finding = nodes(page.root).find(
      (node) => node.type === 'button' && textOf(node) === 'CPU异常繁忙检查'
    )!
    expect(finding).toBeTruthy()
    ;(finding.props!.onClick as Function)()
    await nextTick()
    expect(textOf(page.root)).toContain('检测依据')
    expect(textOf(page.root)).toContain('完整长命令输出'.repeat(300))
    for (const label of [
      '结果记录',
      'UMC规则ID',
      '来源执行',
      '报告记录',
      '最新标注记录',
      'INTERNAL-RULE-ID'
    ])
      expect(textOf(page.root)).not.toContain(label)
    page.state.openEditor(page.task.results[0])
    await nextTick()
    page.state.form.kind = 'TRACK'
    page.state.form.reason = '确认此冻结异常项'
    page.state.form.responsibleUserId = '9'
    page.state.form.dueAt = new Date(2026, 9, 29)
    page.state.formRef = { validate: async () => true, clearValidate: vi.fn() }
    await page.state.submitAnnotation()
    expect(page.annotate).toHaveBeenCalledWith(
      expect.objectContaining({
        reportId: '20',
        resultId: '31',
        kind: 'TRACK',
        responsibleUserId: '9'
      })
    )
  } finally {
    page.app.unmount()
  }
})

it('prevents UMC annotation if the adopted report frozen result set is incomplete', async () => {
  const page = render(undefined, false, true)
  try {
    page.task.results = [{ id: '31', outcome: 'ABNORMAL', ruleName: '唯一已读取结果' }]
    await nextTick()
    expect(page.state.frozenResultsComplete).toBe(false)
    page.state.openEditor(page.task.results[0])
    expect(page.state.selectedResultId).toBe('')
    await page.state.submitAnnotation()
    expect(page.annotate).not.toHaveBeenCalled()
  } finally {
    page.app.unmount()
  }
})

it('omits report metadata in the UMC confirmation view while preserving the native report summary', () => {
  const umc = render(undefined, false, true),
    native = render()
  const nodes = (node: TestNode): TestNode[] => [node, ...node.children.flatMap(nodes)]
  try {
    expect(nodes(umc.root).some((node) => node.props?.class === 'report-summary')).toBe(false)
    expect(nodes(native.root).some((node) => node.props?.class === 'report-summary')).toBe(true)
    expect(textOf(umc.root)).not.toContain('当前报告 R')
    expect(textOf(native.root)).toContain('当前报告 R')
  } finally {
    umc.app.unmount()
    native.app.unmount()
  }
})

it('shows only saved annotation status in the UMC column and keeps type selection in the editor', async () => {
  const page = render(undefined, true, true)
  const nodes = (node: TestNode): TestNode[] => [node, ...node.children.flatMap(nodes)]
  try {
    page.task.annotations = [
      {
        id: '70',
        reportId: '20',
        resultId: '31',
        revisionNo: 1,
        kind: 'TRACK',
        reason: '原跟踪原因'
      }
    ] as any
    await nextTick()
    const cell = () => nodes(page.root).find((node) => node.props?.label === '标注')!
    expect(textOf(cell()).split('确认跟踪').length - 1).toBe(1)
    expect(nodes(cell()).some((node) => node.type === 'button')).toBe(false)
    page.state.openEditor(page.task.results[0])
    page.state.form.kind = 'IGNORE'
    await nextTick()
    expect(textOf(cell())).toContain('待保存')
    expect(textOf(cell())).toContain('确认跟踪')
    expect(textOf(cell())).not.toContain('不关注')
    expect(nodes(page.root).some((node) => node.props?.label === '标注类型')).toBe(true)
    expect(page.annotate).not.toHaveBeenCalled()
  } finally {
    page.app.unmount()
  }
})

it('defaults new tracking to the current user and preserves a manually chosen responsibility', async () => {
  const page = render()
  try {
    page.state.openEditor(page.task.results[0])
    page.state.form.kind = 'TRACK'
    await nextTick()
    expect(page.state.form.responsibleUserId).toBe('9')
    page.state.form.responsibleUserId = '8'
    page.state.form.kind = 'IGNORE'
    await nextTick()
    page.state.form.kind = 'TRACK'
    await nextTick()
    expect(page.state.form.responsibleUserId).toBe('8')
    page.state.form.reason = '确认跟踪'
    page.state.form.dueAt = new Date(2026, 9, 29)
    page.state.formRef = { validate: async () => true, clearValidate: vi.fn() }
    await page.state.submitAnnotation()
    expect(page.annotate).toHaveBeenCalledWith(expect.objectContaining({ responsibleUserId: '8' }))
  } finally {
    page.app.unmount()
  }
})

it('preserves the existing responsible user and deadline when revising tracking', async () => {
  const page = render()
  try {
    const dueAt = new Date(2026, 10, 5).getTime()
    page.task.annotations = [
      {
        id: '70',
        reportId: '20',
        resultId: '31',
        revisionNo: 1,
        kind: 'TRACK',
        reason: '原跟踪原因',
        responsibleUserId: '87',
        dueAt
      }
    ] as any
    await nextTick()
    page.state.openEditor(page.task.results[0])
    expect(page.state.form.responsibleUserId).toBe('87')
    expect(page.state.form.dueAt.getTime()).toBe(dueAt)
    page.state.form.changeReason = '补充跟踪依据'
    page.state.formRef = { validate: async () => true, clearValidate: vi.fn() }
    await page.state.submitAnnotation()
    expect(page.annotate).toHaveBeenCalledWith(
      expect.objectContaining({ responsibleUserId: '87', dueAt })
    )
  } finally {
    page.app.unmount()
  }
})

it('supplies the known login name immediately without labeling another responsible user as the current user', async () => {
  const page = render(undefined, true, true)
  const nodes = (node: TestNode): TestNode[] => [node, ...node.children.flatMap(nodes)]
  try {
    page.state.openEditor(page.task.results[0])
    page.state.form.kind = 'TRACK'
    await nextTick()
    const picker = () => nodes(page.root).find((node) => node.type === 'user-select')!
    expect(picker().props?.modelValue).toBe('9')
    expect(picker().props?.currentLabel).toBe('当前工程师')
    page.state.form.responsibleUserId = '87'
    await nextTick()
    expect(picker().props?.modelValue).toBe('87')
    expect(picker().props?.currentLabel).toBe('')
  } finally {
    page.app.unmount()
  }
})

it('renders annotation history names without loading the responsibility candidate dropdown', async () => {
  const page = render(undefined, true, true)
  const nodes = (node: TestNode): TestNode[] => [node, ...node.children.flatMap(nodes)]
  try {
    page.task.annotations = [
      {
        id: '70',
        reportId: '20',
        resultId: '31',
        revisionNo: 1,
        kind: 'TRACK',
        responsibleUserId: '87',
        responsibleName: '原责任工程师',
        annotatedBy: '88',
        annotatedByName: '历史标注工程师'
      }
    ] as any
    await nextTick()
    const responsible = nodes(page.root).find((node) => node.props?.label === '责任人')!
    const annotator = nodes(page.root).find((node) => node.props?.label === '标注人')!
    expect(textOf(responsible)).toBe('原责任工程师')
    expect(textOf(annotator)).toBe('历史标注工程师')
    expect(page.state.users).toEqual([])
    expect(page.state.selectedResultId).toBe('')
  } finally {
    page.app.unmount()
  }
})

it('uses the authenticated name only for the same historical identity and retains unknown-user IDs', async () => {
  const page = render(undefined, true, true)
  const nodes = (node: TestNode): TestNode[] => [node, ...node.children.flatMap(nodes)]
  try {
    page.task.annotations = [
      {
        id: '70',
        reportId: '20',
        resultId: '31',
        kind: 'TRACK',
        responsibleUserId: '87',
        annotatedBy: '9'
      }
    ] as any
    await nextTick()
    expect(textOf(nodes(page.root).find((node) => node.props?.label === '责任人')!)).toBe('用户 87')
    expect(textOf(nodes(page.root).find((node) => node.props?.label === '标注人')!)).toBe(
      '当前工程师'
    )
  } finally {
    page.app.unmount()
  }
})

it.each([false, true])(
  'shows the finding from the annotation historical report, not the current report (UMC=%s)',
  async (umcMode) => {
    const page = render(undefined, true, umcMode)
    const nodes = (node: TestNode): TestNode[] => [node, ...node.children.flatMap(nodes)]
    try {
      page.task.results = [
        { id: '31', outcome: 'ABNORMAL', ruleName: '当前报告检查' },
        { id: '33', outcome: 'ABNORMAL', sourceCode: 'PLATFORM', ruleName: '历史冻结规则检查' }
      ] as any
      page.task.reports.push({ id: '19', statusCode: 'AVAILABLE', resultIds: ['33'] })
      page.task.annotations = [{ id: '70', reportId: '19', resultId: '33', kind: 'IGNORE' }] as any
      await nextTick()
      const column = nodes(page.root)
        .filter((node) => node.props?.label === '检出问题')
        .at(-1)!
      expect(textOf(column)).toBe('历史冻结规则检查')
      expect(page.task.annotations[0]).toEqual({
        id: '70',
        reportId: '19',
        resultId: '33',
        kind: 'IGNORE'
      })
    } finally {
      page.app.unmount()
    }
  }
)

it('resolves a UMC source snapshot through the adopted report membership without coercing long IDs', async () => {
  const page = render(undefined, true, true)
  const nodes = (node: TestNode): TestNode[] => [node, ...node.children.flatMap(nodes)]
  try {
    page.task.results = [
      {
        id: '2115387679367467008',
        sourceCode: 'UMC',
        reportId: '12',
        externalRule: { name: 'UMC来源巡检项目' }
      },
      { id: '2115387679367467009', ruleName: '不能误用的相邻编号项目' }
    ] as any
    page.task.reports.push({
      id: '19',
      statusCode: 'AVAILABLE',
      resultIds: ['2115387679367467008']
    })
    page.task.annotations = [
      { id: '70', reportId: '19', resultId: '2115387679367467008', kind: 'TRACK' }
    ] as any
    await nextTick()
    expect(
      textOf(
        nodes(page.root)
          .filter((node) => node.props?.label === '检出问题')
          .at(-1)!
      )
    ).toBe('UMC来源巡检项目')
  } finally {
    page.app.unmount()
  }
})

it('does not borrow another report finding when the historical source or result is unavailable', async () => {
  const page = render(undefined, true, true)
  const nodes = (node: TestNode): TestNode[] => [node, ...node.children.flatMap(nodes)]
  try {
    page.task.results[0].ruleName = '当前结果不得冒充历史来源'
    page.task.annotations = [{ id: '70', reportId: '19', resultId: '31', kind: 'IGNORE' }] as any
    const label = () =>
      textOf(
        nodes(page.root)
          .filter((node) => node.props?.label === '检出问题')
          .at(-1)!
      )
    await nextTick()
    expect(label()).toBe('来源检出问题不可用')
    page.task.reports.push({ id: '19', statusCode: 'AVAILABLE', resultIds: ['999'] })
    await nextTick()
    expect(label()).toBe('来源检出问题不可用')
    page.task.reports.at(-1)!.resultIds = ['31']
    page.task.results = []
    await nextTick()
    expect(label()).toBe('来源检出问题不可用')
  } finally {
    page.app.unmount()
  }
})

it.each([false, true])(
  'opens the historical result details for the selected annotation (UMC=%s)',
  async (umcMode) => {
    const page = render(undefined, true, umcMode)
    const nodes = (node: TestNode): TestNode[] => [node, ...node.children.flatMap(nodes)]
    try {
      page.task.results = [
        { id: '31', ruleName: '当前检查', detailMessage: '当前说明' },
        { id: '33', ruleName: '历史检查', detailMessage: '历史说明', outputSummary: '历史输出' }
      ] as any
      page.task.reports.push({ id: '19', statusCode: 'AVAILABLE', resultIds: ['33'] })
      page.task.annotations = [{ id: '70', reportId: '19', resultId: '33', kind: 'IGNORE' }] as any
      await nextTick()
      const column = nodes(page.root)
        .filter((node) => node.props?.label === '检出问题')
        .at(-1)!
      const link = nodes(column).find(
        (node) => node.type === 'button' && textOf(node) === '历史检查'
      )!
      expect(link).toBeTruthy()
      await (link.props!.onClick as Function)()
      await nextTick()
      expect(page.state.umcDetailId).toBe('33')
      expect(textOf(page.root)).toContain('历史说明')
      expect(textOf(page.root)).toContain('历史输出')
      page.task.reports.at(-1)!.resultIds = ['31']
      await nextTick()
      expect(
        nodes(column).some((node) => node.type === 'button' && textOf(node) === '历史检查')
      ).toBe(false)
    } finally {
      page.app.unmount()
    }
  }
)
