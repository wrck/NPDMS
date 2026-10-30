import { afterEach, expect, it, vi } from 'vitest'
import { defineComponent, h, inject, nextTick, provide, reactive } from 'vue'
import Results from './InspectionUmcExecutionResults.vue'
import type { TaskDetail, ExecutionView, ResultView } from '@/api/pms/inspection/tasks'
import {
  mount,
  passthrough,
  textOf,
  type TestNode
} from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

vi.mock('@vueuse/core', () => ({ useMediaQuery: () => false }))
vi.mock('@/utils/formatTime', () => ({ formatDate: () => '2026-10-28 16:18:01' }))
const permission = vi.hoisted(() => ({ execute: true }))
vi.mock('@/utils/permission', () => ({ checkPermi: () => permission.execute }))
const apps: Array<{ unmount: () => void }> = []
const deviceTable = defineComponent({
  props: { data: { type: Array, default: () => [] } },
  setup(props, { slots }) {
    provide('test-device-rows', () => props.data || [])
    return () => h('section', slots.default?.())
  }
})
const deviceColumn = defineComponent({
  setup(_, { slots }) {
    const rows = inject<() => unknown[]>('test-device-rows', () => [])
    return () =>
      h(
        'section',
        rows().map((row) => slots.default?.({ row }))
      )
  }
})
const collapse = defineComponent({
  props: { modelValue: { type: Array, default: () => [] } },
  emits: ['update:modelValue'],
  setup(props, { emit, slots }) {
    provide('test-item-collapse', {
      active: (name: string) => props.modelValue.includes(name),
      toggle: (name: string) =>
        emit(
          'update:modelValue',
          props.modelValue.includes(name)
            ? props.modelValue.filter((item) => item !== name)
            : [...props.modelValue, name]
        )
    })
    return () => h('div', slots.default?.())
  }
})
const collapseItem = defineComponent({
  props: { name: { type: String, required: true } },
  setup(props, { slots }) {
    const collapse = inject<{ active: (name: string) => boolean; toggle: (name: string) => void }>(
      'test-item-collapse'
    )!
    return () =>
      h('article', [
        h(
          'div',
          {
            role: 'button',
            'aria-expanded': collapse.active(props.name),
            'aria-controls': `content-${props.name}`,
            onClick: () => collapse.toggle(props.name)
          },
          slots.title?.()
        ),
        h(
          'section',
          {
            id: `content-${props.name}`,
            style: { display: collapse.active(props.name) ? '' : 'none' }
          },
          slots.default?.()
        )
      ])
  }
})
const dialog = defineComponent({
  props: { modelValue: Boolean, title: String },
  setup(props, { slots }) {
    return () =>
      props.modelValue
        ? h('section', { role: 'dialog' }, [props.title, slots.default?.(), slots.footer?.()])
        : null
  }
})
afterEach(() => {
  apps.splice(0).forEach((app) => app.unmount())
  permission.execute = true
})
const fixture = () => {
  const first: ExecutionView = {
    id: '9007199254740993',
    version: 1,
    attemptId: '20',
    deviceId: '30',
    purposeCode: 'OFFLINE',
    statusCode: 'COMPLETE',
    artifactId: '70',
    fileVersionNo: 1,
    fileReferenceKey: 'log-1',
    submittedAt: 1,
    completedAt: 2
  }
  const second: ExecutionView = {
    ...first,
    id: '9007199254740995',
    deviceId: '31',
    statusCode: 'PENDING'
  }
  const rules: ResultView[] = Array.from({ length: 27 }, (_, index) => ({
    id: String(1000 + index),
    attemptId: '20',
    executionId: first.id,
    deviceId: '30',
    sourceCode: 'UMC',
    reportId: '90',
    ruleName: `巡检项目${index + 1}`,
    categoryName: '运行状态检测',
    outcome: index === 0 ? 'ABNORMAL' : 'PASS',
    externalRule: {
      id: `r${index}`,
      ruleid: String(index + 1),
      name: `巡检项目${index + 1}`,
      type: '运行状态检测',
      level: index === 0 ? 2 : 1,
      support: true,
      result: index === 0,
      cmd: 'show version',
      output: index === 26 ? '<script>untrusted</script>\n' + '完整输出'.repeat(4000) : 'output',
      targetlist: [
        {
          id: `t${index}`,
          name: `指标${index + 1}`,
          result: index === 0,
          valuelist: Array.from({ length: index === 26 ? 214 : 1 }, (_, valueIndex) => ({
            id: `v${index}-${valueIndex}`,
            object: `对象${valueIndex + 1}`,
            value: `${valueIndex}`,
            result: false
          }))
        }
      ]
    }
  }))
  const task = {
    id: '10',
    statusCode: 'EXECUTING',
    owner: true,
    manager: false,
    currentAttemptId: '20',
    devices: [
      {
        id: '1',
        deviceId: '30',
        serialNumber: 'SN-30',
        deviceName: '防火墙',
        productTypeName: '安全设备'
      },
      { id: '2', deviceId: '31', serialNumber: 'SN-31', deviceName: '交换机' },
      { id: '3', deviceId: '32', serialNumber: 'SN-32', deviceName: '尚未上传的设备' }
    ],
    attempts: [
      { id: '20', attemptNo: 1, providerCode: 'UMC', mode: 'OFFLINE', statusCode: 'ACTIVE' }
    ],
    executions: [first, second],
    results: rules,
    umcResults: [
      {
        reportId: '90',
        attemptId: '20',
        executionId: first.id,
        deviceId: '30',
        statusCode: 'AVAILABLE',
        result: {
          id: 'external',
          deviceip: '192.0.2.31',
          sn: 'SN-30',
          version: 'S211C012D032',
          devmodel: 'FW1000-TA-A10',
          rulelist: rules.map((row) => row.externalRule)
        }
      }
    ]
  } as TaskDetail
  return { task, executions: [first, second], results: rules }
}
const render = (input = fixture(), disabled = false) => {
  const refresh = vi.fn(),
    downloadLog = vi.fn()
  const view = mount(
    Results,
    { ...input, disabled, onRefresh: refresh, onDownloadLog: downloadLog },
    {
      ElTable: deviceTable,
      ElTableColumn: deviceColumn,
      ElCheckbox: passthrough,
      ElCollapse: collapse,
      ElCollapseItem: collapseItem,
      ElDialog: dialog
    }
  )
  apps.push(view.app)
  return { ...view, state: (view.vm as any).$.setupState, refresh, downloadLog, input }
}
const nodes = (node: TestNode): TestNode[] => [node, ...node.children.flatMap(nodes)]
const click = (node: TestNode) => (node.props!.onClick as Function)({ stopPropagation: vi.fn() })

it('selects a device by row click and keeps current-row highlighting without a redundant view action', async () => {
  const view = render()
  const table = () => nodes(view.root).find((node) => node.props?.onRowClick)!
  expect(
    nodes(view.root).filter(
      (node) => node.type === 'button' && textOf(node).trim() === '查看巡检项目'
    )
  ).toHaveLength(0)
  expect(table().props?.['highlight-current-row']).toBeDefined()
  ;(table().props!.onRowClick as Function)(view.state.deviceRows[1])
  await nextTick()
  expect(view.state.selected.key).toBe(view.state.deviceRows[1].key)
  expect(table().props?.['current-row-key']).toBe(view.state.deviceRows[1].key)
  expect(view.state.visibleResults).toHaveLength(0)
})

it('keeps device selection keyboard reachable through the existing name with Enter and Space', async () => {
  const view = render()
  const selectors = () =>
    nodes(view.root).filter((node) => node.props?.['data-testid'] === 'inspection-device-selector')
  expect(selectors()).toHaveLength(3)
  for (const [index, key] of [
    [1, 'Enter'],
    [0, ' ']
  ] as const) {
    const selector = selectors()[index]
    expect(selector.props?.role).toBe('button')
    expect(Number(selector.props?.tabindex)).toBe(0)
    const stopPropagation = vi.fn(),
      preventDefault = vi.fn()
    const handlers = [selector.props!.onKeydown].flat() as Function[]
    handlers.forEach((handler) => handler({ key, stopPropagation, preventDefault }))
    await nextTick()
    expect(view.state.selected.key).toBe(view.state.deviceRows[index].key)
    expect(selectors()[index].props?.['aria-pressed']).toBe(true)
    expect(stopPropagation).toHaveBeenCalledTimes(1)
    expect(preventDefault).toHaveBeenCalledTimes(1)
  }
})

it('lists every device including devices with no upload and keeps rule detail on the selected execution', async () => {
  const view = render()
  expect(view.state.deviceRows).toHaveLength(3)
  expect(view.state.deviceRows[2].execution).toBeUndefined()
  expect(view.state.selected.execution.id).toBe('9007199254740993')
  expect(view.state.visibleResults).toHaveLength(27)
  expect(view.state.countSeverity(view.state.selected, 2)).toBe(1)
  expect(textOf(view.root)).toContain('FW1000-TA-A10')
  view.state.selectDevice(view.state.deviceRows[1])
  await nextTick()
  expect(view.state.visibleResults).toHaveLength(0)
  expect(textOf(view.root)).toContain('本设备尚无巡检判定结果，不能视为通过')
  view.state.selectDevice(view.state.deviceRows[2])
  await nextTick()
  expect(textOf(view.root)).toContain('本设备尚未上传日志')
})

it('keeps all project values and shows complete command output only in the selected detail dialog', async () => {
  const view = render()
  const text = textOf(view.root)
  expect(nodes(view.root).filter((node) => node.type === 'article')).toHaveLength(27)
  for (const expected of ['巡检项目27', '指标27', '对象214']) expect(text).toContain(expected)
  expect(text).not.toContain(view.input.results[26].externalRule!.output!)
  const last = nodes(view.root).filter((node) => node.type === 'article')[26]
  const open = nodes(last).find(
    (node) => node.type === 'button' && node.props?.['data-testid'] === 'inspection-umc-item-name'
  )!
  click(open)
  await nextTick()
  const detail = nodes(view.root).find((node) => node.props?.role === 'dialog')!
  for (const expected of [
    '检测依据',
    '完整命令',
    'show version',
    '对象214',
    view.input.results[26].externalRule!.output!
  ])
    expect(textOf(detail)).toContain(expected)
  expect(nodes(view.root).some((node) => node.type === 'script')).toBe(false)
})

it('collapses cards independently while keeping their headings, conclusions and detail action reachable', async () => {
  const view = render()
  const cards = () => nodes(view.root).filter((node) => node.type === 'article')
  const toggle = (index: number) =>
    nodes(cards()[index]).find(
      (node) => node.props?.role === 'button' && node.props?.['aria-expanded'] != null
    )!
  expect(toggle(0).props?.['aria-expanded']).toBe(false)
  ;(toggle(0).props!.onClick as Function)()
  await nextTick()
  expect(toggle(0).props?.['aria-expanded']).toBe(true)
  expect(toggle(1).props?.['aria-expanded']).toBe(false)
  ;(toggle(0).props!.onClick as Function)()
  await nextTick()
  expect(toggle(0).props?.['aria-expanded']).toBe(false)
  const content = nodes(cards()[0]).find(
    (node) => node.props?.id === toggle(0).props?.['aria-controls']
  )!
  expect((content.props?.style as Record<string, unknown>)?.display).toBe('none')
  const open = nodes(cards()[0]).find(
    (node) => node.type === 'button' && node.props?.['data-testid'] === 'inspection-umc-item-name'
  )!
  const stopPropagation = vi.fn()
  const preventDefault = vi.fn()
  expect(textOf(open)).toBe('运行状态检测 - 巡检项目1')
  expect(open.props?.['aria-haspopup']).toBe('dialog')
  expect(nodes(cards()[0]).some((node) => node.type === 'button' && textOf(node) === '详情')).toBe(
    false
  )
  ;(open.props!.onClick as Function)({ stopPropagation })
  expect(stopPropagation).toHaveBeenCalledTimes(1)
  expect(toggle(0).props?.['aria-expanded']).toBe(false)
  for (const key of ['Enter', ' ']) {
    ;(open.props!.onKeydown as Function)({ key, stopPropagation, preventDefault })
    ;(open.props!.onKeyup as Function)({ key, stopPropagation, preventDefault })
  }
  expect(stopPropagation).toHaveBeenCalledTimes(5)
  expect(preventDefault).not.toHaveBeenCalled()
  await nextTick()
  expect(nodes(view.root).some((node) => node.props?.role === 'dialog')).toBe(true)
  ;(toggle(0).props!.onClick as Function)()
  await nextTick()
  expect((content.props?.style as Record<string, unknown>)?.display).not.toBe('none')
})

it('keeps the original finding visible above hidden metrics without replacing or repeating its long text', async () => {
  const input = fixture()
  const finding = 'VCPU有历史异常记录，请及时检查\n' + '完整原始说明'.repeat(300)
  input.results[26].externalRule!.info = finding
  const view = render(input)
  const card = () => nodes(view.root).filter((node) => node.type === 'article')[26]
  const header = () => nodes(card()).find((node) => node.props?.role === 'button')!
  const descriptions = () =>
    nodes(card()).filter((node) => node.props?.class === 'item-description')
  expect(descriptions()).toHaveLength(1)
  expect(textOf(header())).toContain(finding)
  expect(header().props?.['aria-expanded']).toBe(false)
  expect(textOf(header())).toContain(finding)
  expect(descriptions()).toHaveLength(1)
  const detailRegion = nodes(card()).find(
    (node) => node.props?.id === header().props?.['aria-controls']
  )!
  expect((detailRegion.props?.style as Record<string, unknown>).display).toBe('none')
  expect(textOf(detailRegion)).toContain('对象214')
  expect(textOf(header())).not.toContain('对象214')
  click(header())
  await nextTick()
  expect((detailRegion.props?.style as Record<string, unknown>).display).not.toBe('none')
  expect(textOf(header())).toContain(finding)
})

it('starts every device, history execution and new task collapsed while retaining only user expansions through filters', async () => {
  const input = reactive(fixture())
  const historical = { ...input.executions[0], id: '9007199254740991', attemptId: '19' }
  input.executions.push(historical)
  input.task.attempts.push({
    id: '19',
    attemptNo: 0,
    providerCode: 'UMC',
    mode: 'OFFLINE',
    statusCode: 'COMPLETED'
  })
  input.results.push({
    ...input.results[0],
    id: '2000',
    attemptId: '19',
    executionId: historical.id
  })
  input.results.push({
    ...input.results[0],
    id: '3000',
    deviceId: '31',
    executionId: input.executions[1].id
  })
  const view = render(input)
  const headers = () =>
    nodes(view.root).filter(
      (node) => node.props?.role === 'button' && node.props?.['aria-expanded'] != null
    )
  expect(headers().every((node) => node.props?.['aria-expanded'] === false)).toBe(true)
  click(headers()[1])
  await nextTick()
  expect(headers()[1].props?.['aria-expanded']).toBe(true)
  view.state.onlyAttention = true
  await nextTick()
  expect(headers()).toHaveLength(1)
  expect(headers()[0].props?.['aria-expanded']).toBe(false)
  view.state.onlyAttention = false
  await nextTick()
  expect(headers()[1].props?.['aria-expanded']).toBe(true)
  expect(headers()[0].props?.['aria-expanded']).toBe(false)
  view.state.selectDevice(view.state.deviceRows.find((row: any) => row.device.deviceId === '31'))
  await nextTick()
  expect(headers()).toHaveLength(1)
  expect(headers()[0].props?.['aria-expanded']).toBe(false)
  click(headers()[0])
  await nextTick()
  view.state.selectDevice(
    view.state.deviceRows.find((row: any) => row.execution?.attemptId === '19')
  )
  await nextTick()
  expect(headers()).toHaveLength(1)
  expect(headers()[0].props?.['aria-expanded']).toBe(false)
  click(headers()[0])
  await nextTick()
  view.state.selectDevice(
    view.state.deviceRows.find((row: any) => row.execution?.id === input.executions[0].id)
  )
  await nextTick()
  expect(headers().every((node) => node.props?.['aria-expanded'] === false)).toBe(true)
  click(headers()[0])
  await nextTick()
  input.task.id = '11'
  await nextTick()
  expect(headers().every((node) => node.props?.['aria-expanded'] === false)).toBe(true)
})

it('does not invent a finding when UMC provides no original project description', async () => {
  const input = fixture()
  input.results[0].externalRule!.info = ''
  const view = render(input)
  const card = nodes(view.root).filter((node) => node.type === 'article')[0]
  const header = nodes(card).find((node) => node.props?.role === 'button')!
  click(header)
  await nextTick()
  expect(nodes(header).some((node) => node.props?.class === 'item-description')).toBe(false)
  for (const text of ['巡检项目1', '重要', '异常']) expect(textOf(header)).toContain(text)
})

it('shows severity tags in existing dictionary colors without treating missing or unknown levels as normal', () => {
  const input = fixture()
  for (const [index, level] of [3, 2, 1, undefined, 99].entries()) {
    input.results[index].externalRule!.level = level
  }
  input.results[5].externalRule = undefined
  input.results[5].severityCode = 'FATAL'
  input.results[5].severityName = '致命'
  const view = render(input)
  const levels = nodes(view.root).filter((node) => node.props?.class === 'item-level')
  expect(levels.slice(0, 6).map((node) => node.props?.type)).toEqual([
    'danger',
    'warning',
    'info',
    'info',
    'info',
    'danger'
  ])
  expect(levels.slice(0, 6).map(textOf)).toEqual(['致命', '重要', '一般', '未提供', '未知', '致命'])
  const first = nodes(view.root).find((node) => node.type === 'article')!
  expect(textOf(first)).toContain('异常')
  expect(
    nodes(first).some((node) => node.props?.['data-testid'] === 'inspection-umc-item-name')
  ).toBe(true)
})

it('closes old details after filtering or selecting another device and reports absent command evidence honestly', async () => {
  const input = fixture()
  input.results[1].externalRule!.cmd = undefined
  input.results[1].externalRule!.output = undefined
  const view = render(input, true)
  const open = () =>
    nodes(view.root).filter(
      (node) => node.type === 'button' && node.props?.['data-testid'] === 'inspection-umc-item-name'
    )[1]
  click(open())
  await nextTick()
  expect(textOf(view.root)).toContain('未提供执行命令')
  expect(textOf(view.root)).toContain('未提供命令输出')
  view.state.onlyAttention = true
  await nextTick()
  expect(nodes(view.root).some((node) => node.props?.role === 'dialog')).toBe(false)
  view.state.onlyAttention = false
  await nextTick()
  expect(nodes(view.root).some((node) => node.props?.role === 'dialog')).toBe(false)
  click(open())
  await nextTick()
  view.state.selectDevice(view.state.deviceRows[1])
  await nextTick()
  expect(nodes(view.root).some((node) => node.props?.role === 'dialog')).toBe(false)
})

it('provides an explicit attention filter without losing normal projects when switching devices', async () => {
  const view = render()
  view.state.onlyAttention = true
  await nextTick()
  expect(view.state.visibleResults).toHaveLength(1)
  view.state.selectDevice(view.state.deviceRows[0])
  await nextTick()
  expect(view.state.onlyAttention).toBe(false)
  expect(view.state.visibleResults).toHaveLength(27)
})

it('shows unsupported, errored, ungraded and empty-value facts without treating a report preview as formal completion', async () => {
  const input = fixture()
  input.results = [
    {
      ...input.results[0],
      id: 'preview',
      outcome: 'UNJUDGED',
      externalRule: {
        ...input.results[0].externalRule!,
        support: false,
        level: undefined,
        error: '命令执行失败',
        targetlist: [
          { id: 'empty', name: '缺少对象', error: '无输出', valuelist: [] },
          { id: 'blank', name: '空值指标', valuelist: [{ id: 'blank-value', value: '' }] }
        ]
      }
    }
  ]
  input.task.results = []
  const view = render(input)
  expect(view.state.statusLabel(view.state.selected)).toBe('报告已保存，待登记判定')
  expect(view.state.formalCount(view.state.selected)).toBe(0)
  expect(view.state.incompleteCount(view.state.selected)).toBe(0)
  expect(view.state.unsupportedCount(view.state.selected)).toBe(1)
  expect(textOf(view.root)).toContain('不支持')
  for (const expected of [
    '本设备不支持此规则',
    '命令执行失败',
    '本指标未返回对象值',
    '（空值）',
    '无输出'
  ])
    expect(textOf(view.root)).toContain(expected)
  const open = nodes(view.root).find(
    (node) => node.type === 'button' && node.props?.['data-testid'] === 'inspection-umc-item-name'
  )!
  click(open)
  await nextTick()
  expect(textOf(view.root)).toContain('尚未登记正式判定')
})

it('retains original execution actions and does not permit refreshing another attempt', () => {
  const view = render()
  const row = view.state.deviceRows[0]
  expect(view.state.canRefresh(row)).toBe(true)
  expect(view.state.hasLog(row.execution)).toBe(true)
  expect(view.state.canRefresh({ ...row, execution: { ...row.execution, attemptId: '19' } })).toBe(
    false
  )
  expect(view.state.canRefresh(view.state.deviceRows[2])).toBe(false)
  expect(view.state.canRefresh(view.state.deviceRows[1])).toBe(false)
  expect(
    view.state.canRefresh({ ...row, execution: { ...row.execution, statusCode: 'CANCELLED' } })
  ).toBe(false)
  const update = nodes(view.root).find(
    (node) => node.type === 'button' && textOf(node) === '更新结果'
  )!
  const download = nodes(view.root).find(
    (node) => node.type === 'button' && textOf(node) === '下载脱敏日志'
  )!
  view.state.selectDevice(view.state.deviceRows[1])
  const stopPropagation = vi.fn()
  ;(update.props!.onClick as Function)({ stopPropagation })
  ;(download.props!.onClick as Function)({ stopPropagation })
  expect(stopPropagation).toHaveBeenCalledTimes(2)
  expect(view.state.selected.key).toBe(view.state.deviceRows[1].key)
  expect(view.refresh).toHaveBeenCalledWith('9007199254740993')
  expect(view.downloadLog).toHaveBeenCalledWith(view.input.executions[0])
})

it('disables write actions during another operation while preserving read-only log downloads', () => {
  const view = render(fixture(), true)
  const update = nodes(view.root).find(
    (node) => node.type === 'button' && textOf(node) === '更新结果'
  )!
  const download = nodes(view.root).find(
    (node) => node.type === 'button' && textOf(node) === '下载脱敏日志'
  )!
  expect(update.props!.disabled).toBe(true)
  expect(download.props!.disabled).not.toBe(true)
})

it.each(['REPORT_PENDING', 'ANNOTATION_PENDING', 'ARCHIVED'])(
  'hides result updates in %s while preserving inspection viewing and log downloads',
  (statusCode) => {
    const input = fixture()
    input.task.statusCode = statusCode
    const view = render(input, true)
    expect(textOf(view.root)).not.toContain('更新结果')
    expect(textOf(view.root)).not.toContain('获取 / 重试结果')
    expect(
      nodes(view.root).some((node) => node.props?.['data-testid'] === 'inspection-device-selector')
    ).toBe(true)
    expect(textOf(view.root)).toContain('下载脱敏日志')
  }
)

it('hides result updates without execute permission instead of showing a disabled action', () => {
  permission.execute = false
  const view = render()
  expect(textOf(view.root)).not.toContain('更新结果')
  expect(textOf(view.root)).not.toContain('获取 / 重试结果')
  expect(
    nodes(view.root).some((node) => node.props?.['data-testid'] === 'inspection-device-selector')
  ).toBe(true)
})

it('hides result updates for a read-only viewer who is neither the task owner nor its manager', () => {
  const input = fixture()
  input.task.owner = false
  const view = render(input)
  expect(
    nodes(view.root).some(
      (node) => node.type === 'button' && ['更新结果', '获取 / 重试结果'].includes(textOf(node))
    )
  ).toBe(false)
  expect(textOf(view.root)).toContain('下载脱敏日志')
})

it('keeps platform attempts read-only in the UMC device list', () => {
  const input = fixture()
  input.task.attempts[0].providerCode = 'PLATFORM'
  const view = render(input)
  expect(
    nodes(view.root).filter(
      (node) => node.type === 'button' && ['更新结果', '获取 / 重试结果'].includes(textOf(node))
    )
  ).toHaveLength(0)
  expect(textOf(view.root)).toContain('下载脱敏日志')
})

it('preserves legacy historical status rather than relabelling it as a pending UMC result', () => {
  const input = fixture()
  input.task.attempts[0].providerCode = 'PLATFORM'
  const view = render(input)
  expect(view.state.statusLabel(view.state.selected)).toBe('判定完成')
})

it('shows saved historical partial reports as available and excludes unsupported rules from attention', async () => {
  const input = fixture()
  input.executions[0].statusCode = 'PARTIAL'
  input.results[0].outcome = 'UNJUDGED'
  input.results[0].externalRule!.support = false
  const view = render(input)
  expect(view.state.statusLabel(view.state.selected)).toBe('报告已保存')
  expect(view.state.unsupportedCount(view.state.selected)).toBe(1)
  expect(view.state.incompleteCount(view.state.selected)).toBe(0)
  view.state.onlyAttention = true
  await nextTick()
  expect(view.state.visibleResults).toHaveLength(0)
})
