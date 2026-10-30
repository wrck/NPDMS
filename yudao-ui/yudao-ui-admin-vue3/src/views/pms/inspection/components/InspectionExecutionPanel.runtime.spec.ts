import { expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import Panel from './InspectionExecutionPanel.vue'
import {
  mount,
  passthrough,
  tableColumn,
  textOf,
  type TestNode
} from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

vi.mock('@/utils/dict', () => ({ getStrDictOptions: () => [] }))
vi.mock('@/utils/permission', () => ({ checkPermi: () => true }))
vi.mock('@vueuse/core', () => ({ useMediaQuery: () => false }))
vi.mock('@/utils/formatTime', () => ({ formatDate: () => '' }))

const render = (
  selectedDeviceId = '30',
  umcResults: unknown[] = [],
  taskPatch: Record<string, unknown> = {},
  umcMode = false,
  actionsTarget?: TestNode
) => {
  const upload = vi.fn()
  const uploadBatch = vi.fn()
  const uploadFiles = vi.fn()
  const page = mount(
    Panel,
    {
      task: {
        id: '10',
        version: '5',
        currentAttemptId: '20',
        taskCode: 'INS10',
        attempts: [{ id: '20', mode: 'OFFLINE', statusCode: 'ACTIVE' }],
        devices: [{ deviceId: '30', serialNumber: 'QA-SN' }],
        executions: [],
        results: [],
        umcResults,
        ruleSnapshots: [],
        ...taskPatch
      },
      selectedDeviceId,
      umcMode,
      actionsTarget,
      disabled: false,
      commands: [],
      onUpload: upload,
      onUploadBatch: uploadBatch,
      onUploadFiles: uploadFiles
    },
    {
      ElRadioGroup: passthrough,
      ElRadioButton: passthrough,
      ElSelect: passthrough,
      ElOption: passthrough,
      ElTable: passthrough,
      ElTableColumn: tableColumn,
      ElCollapse: passthrough,
      ElCollapseItem: passthrough
    }
  )
  return { ...page, state: (page.vm as any).$.setupState, upload, uploadBatch, uploadFiles }
}

it('keeps single-device uploads on their original event and requires a selected device', () => {
  const page = render()
  try {
    const file = new File(['one device log'], 'single.log')
    page.state.acceptFile(file)
    page.state.sourceHost = '192.0.2.10'
    page.state.submitUpload()
    expect(page.upload).toHaveBeenCalledWith({ deviceId: '30', file, sourceHost: '192.0.2.10' })
    expect(page.uploadBatch).not.toHaveBeenCalled()
  } finally {
    page.app.unmount()
  }
})

it('uses one UMC multi-file entry, preserves append/remove order and emits the original file objects atomically', async () => {
  const page = render(
    '',
    [],
    {
      devices: [
        { deviceId: '30', serialNumber: 'SN-A' },
        { deviceId: '31', serialNumber: 'SN-B' }
      ]
    },
    true
  )
  try {
    expect(textOf(page.root)).not.toContain('单设备日志')
    const first = new File(['A'], 'first.txt')
    const second = new File(['B'], 'second.log')
    page.state.selectFile({ target: { files: [first, second] } })
    await nextTick()
    expect(page.state.selectedFiles).toEqual([first, second])
    page.state.submitUpload()
    expect(page.uploadFiles).not.toHaveBeenCalled()
    expect(page.state.fileError).toContain('来源IP')
    page.state.sourceHosts['30'] = '192.0.2.10'
    page.state.sourceHosts['31'] = '192.0.2.11'
    page.state.submitUpload()
    expect(page.uploadFiles).toHaveBeenCalledWith({
      files: [first, second],
      sourceHosts: [
        { deviceId: '30', host: '192.0.2.10' },
        { deviceId: '31', host: '192.0.2.11' }
      ]
    })
    delete page.state.sourceHosts['31']
    page.state.submitUpload()
    expect(page.uploadFiles).toHaveBeenLastCalledWith({
      files: [first, second],
      sourceHosts: [{ deviceId: '30', host: '192.0.2.10' }]
    })
    expect(page.upload).not.toHaveBeenCalled()
    expect(page.uploadBatch).not.toHaveBeenCalled()
    page.state.addFiles([first])
    expect(page.state.fileNotice).toContain('重复')
    expect(page.state.fileError).toBe('')
    expect(page.state.selectedFiles).toHaveLength(2)
    page.state.removeFile(0)
    expect(page.state.selectedFiles).toEqual([second])
    page.state.clearDraft()
    expect(page.state.selectedFiles).toEqual([])
  } finally {
    page.app.unmount()
  }
})

it('skips selected and intra-addition duplicates while retaining every new valid file and original upload objects', () => {
  const page = render('', [], {}, true)
  try {
    const first = new File(['A'], 'first.txt')
    const second = new File(['B'], 'second.txt')
    const third = new File(['C'], 'third.txt')
    page.state.addFiles([first])
    page.state.addFiles([second, first, third, second])
    expect(page.state.selectedFiles).toEqual([first, second, third])
    expect(page.state.fileError).toBe('')
    expect(page.state.fileNotice).toContain('first.txt')
    expect(page.state.fileNotice).toContain('second.txt')
    page.state.sourceHosts['30'] = '192.0.2.10'
    page.state.submitUpload()
    expect(page.uploadFiles).toHaveBeenCalledWith({
      files: [first, second, third],
      sourceHosts: [{ deviceId: '30', host: '192.0.2.10' }]
    })
    page.state.clearDraft()
    expect(page.state.fileNotice).toBe('')
  } finally {
    page.app.unmount()
  }
})

it('passes filename IP logs to the backend for SN binding without guessing task-device order', () => {
  const page = render('', [], {}, true)
  const multi = render('', [], { devices: [{ deviceId: '30' }, { deviceId: '31' }] }, true)
  try {
    const file = new File(['device log'], '设备甲(192.0.2.10)')
    page.state.addFiles([file])
    expect(page.state.inferredSourceHost).toBe('192.0.2.10')
    page.state.submitUpload()
    expect(page.uploadFiles).toHaveBeenCalledWith({ files: [file], sourceHosts: [] })
    page.state.sourceHosts['30'] = '192.0.2.11'
    page.state.submitUpload()
    expect(page.uploadFiles).toHaveBeenLastCalledWith({
      files: [file],
      sourceHosts: [{ deviceId: '30', host: '192.0.2.11' }]
    })
    multi.state.addFiles([file])
    expect(multi.state.inferredSourceHost).toBe('')
    multi.state.submitUpload()
    expect(multi.uploadFiles).toHaveBeenCalledWith({ files: [file], sourceHosts: [] })
  } finally {
    page.app.unmount()
    multi.app.unmount()
  }
})

it('recognizes txt and log filename IPs in multiple files and preserves original files for server SN matching', () => {
  const page = render('', [], { devices: [{ deviceId: '31' }, { deviceId: '30' }] }, true)
  try {
    const files = [new File(['A'], '10.27.35.201.txt'), new File(['B'], '设备乙(10.27.35.202).LOG')]
    page.state.addFiles(files)
    expect(page.state.originalFilenameHost(files[0].name)).toBe('10.27.35.201')
    expect(page.state.originalFilenameHost(files[1].name)).toBe('10.27.35.202')
    page.state.submitUpload()
    expect(page.uploadFiles).toHaveBeenCalledWith({ files, sourceHosts: [] })
    expect(page.state.originalFilenameHost('10.27.35.999.txt')).toBe('')
    expect(page.state.originalFilenameHost('10.27.35.201.txt.exe')).toBe('')
  } finally {
    page.app.unmount()
  }
})

it('rejects invalid or oversized additions without losing the valid files and blocks disabled submissions', async () => {
  const page = render('', [], {}, true)
  try {
    const first = new File(['A'], 'first.txt')
    page.state.addFiles([first])
    for (const invalid of [new File(['data'], 'bad.exe'), new File([], 'empty.txt')]) {
      page.state.addFiles([invalid])
      expect(page.state.fileError).not.toBe('')
      expect(page.state.selectedFiles).toEqual([first])
    }
    const large = new File(['B'], 'large.log')
    Object.defineProperty(large, 'size', { value: 20 * 1024 * 1024 })
    page.state.addFiles([large])
    expect(page.state.fileError).toContain('合计')
    expect(page.state.selectedFiles).toEqual([first])
    page.state.sourceHosts['30'] = '999.1.1.1'
    page.state.submitUpload()
    expect(page.uploadFiles).not.toHaveBeenCalled()
    expect(page.state.fileError).toContain('IPv4')
    ;(page.vm as any).$.props.disabled = true
    await nextTick()
    page.state.sourceHosts['30'] = '192.0.2.10'
    page.state.removeFile(0)
    page.state.submitUpload()
    expect(page.state.selectedFiles).toEqual([first])
    expect(page.uploadFiles).not.toHaveBeenCalled()
  } finally {
    page.app.unmount()
  }
})

it('moves upload and completion into the shared action target and submits through the original upload validation', async () => {
  const target: TestNode = { type: 'footer', children: [] }
  const page = render('30', [], {}, true, target)
  const nodes = (node: TestNode): TestNode[] => [node, ...node.children.flatMap(nodes)]
  const action = () =>
    nodes(target).find((node) => node.type === 'button' && textOf(node) === '上传并执行巡检')!
  try {
    expect(action().props?.disabled).toBe(true)
    const file = new File(['device log'], 'device.log')
    page.state.addFiles([file])
    page.state.sourceHosts['30'] = '192.0.2.10'
    await nextTick()
    expect(action().props?.disabled).toBe(false)
    expect(action().props?.['native-type']).toBe('button')
    await (action().props!.onClick as Function)()
    expect(page.uploadFiles).toHaveBeenCalledTimes(1)
    expect(page.uploadFiles).toHaveBeenCalledWith({
      files: [file],
      sourceHosts: [{ deviceId: '30', host: '192.0.2.10' }]
    })
    expect(
      nodes(page.root).filter((node) => node.type === 'button' && textOf(node) === '上传并执行巡检')
    ).toHaveLength(0)
    expect(
      nodes(target).filter(
        (node) => node.type === 'button' && textOf(node) === '完成执行并进入报告'
      )
    ).toHaveLength(1)
  } finally {
    page.app.unmount()
  }
})

it('requires formal references rather than a report preview, deduplicates adopted rules and keeps history separate', async () => {
  const rule = { id: 'r1', ruleid: '1', name: 'UMC检查', support: true, result: true }
  const saved = {
    reportId: '90',
    attemptId: '20',
    executionId: '80',
    deviceId: '30',
    statusCode: 'AVAILABLE',
    result: { rulelist: [rule] }
  }
  const formal = {
    id: '1000',
    attemptId: '20',
    executionId: '80',
    deviceId: '30',
    reportId: '90',
    sourceCode: 'UMC',
    externalRule: rule,
    outcome: 'ABNORMAL'
  }
  const execution = { id: '80', attemptId: '20', deviceId: '30', statusCode: 'COMPLETE' }
  const nodes = (node: TestNode): TestNode[] => [node, ...node.children.flatMap(nodes)]
  const finish = (root: TestNode) =>
    nodes(root).find(
      (node) => node.type === 'button' && textOf(node).includes('完成执行并进入报告')
    )!
  const preview = render('30', [saved], { executions: [execution] }, true)
  try {
    expect(finish(preview.root).props?.disabled).toBe(true)
  } finally {
    preview.app.unmount()
  }
  const accepted = render(
    '30',
    [saved],
    {
      executions: [execution],
      results: [formal, { ...formal, id: 'old', reportId: 'old-report', attemptId: '19' }]
    },
    true
  )
  try {
    expect(accepted.state.displayResults).toHaveLength(1)
    expect(accepted.state.displayResults[0].id).toBe('1000')
    expect(finish(accepted.root).props?.disabled).toBe(false)
    accepted.state.showHistory = true
    await nextTick()
    expect(accepted.state.displayResults).toHaveLength(2)
  } finally {
    accepted.app.unmount()
  }
  const historicalPartial = render(
    '30',
    [saved],
    { executions: [{ ...execution, statusCode: 'PARTIAL' }], results: [formal] },
    true
  )
  try {
    expect(finish(historicalPartial.root).props?.disabled).toBe(false)
  } finally {
    historicalPartial.app.unmount()
  }
  for (const patch of [
    { executions: [{ ...execution, statusCode: 'RUNNING' }] },
    { devices: [{ deviceId: '30' }, { deviceId: '31' }] }
  ]) {
    const blocked = render(
      '30',
      [saved],
      { executions: [execution], results: [formal], ...patch },
      true
    )
    try {
      expect(finish(blocked.root).props?.disabled).toBe(true)
    } finally {
      blocked.app.unmount()
    }
  }
})

it('uploads a multi-device log without a manual device and clears the draft when switching modes', async () => {
  const page = render('')
  try {
    const file = new File(['two device logs'], 'batch.log')
    page.state.acceptFile(file)
    expect(page.state.selectedFile).toBeNull()
    page.state.uploadMode = 'batch'
    await nextTick()
    page.state.acceptFile(file)
    page.state.sourceHosts['30'] = '192.0.2.10'
    page.state.submitUpload()
    expect(page.uploadBatch).toHaveBeenCalledWith({
      file,
      sourceHosts: [{ deviceId: '30', host: '192.0.2.10' }]
    })
    expect(page.upload).not.toHaveBeenCalled()
    page.state.uploadMode = 'single'
    await nextTick()
    expect(page.state.selectedFile).toBeNull()
    expect(page.state.commandHint).toBe('')
  } finally {
    page.app.unmount()
  }
})

it('keeps upload on the original workflow and rejects missing source IP before emitting', () => {
  const page = render()
  try {
    page.state.acceptFile(new File(['test'], 'single.log'))
    page.state.submitUpload()
    expect(page.upload).not.toHaveBeenCalled()
    expect(page.state.fileError).toContain('来源设备IP')
  } finally {
    page.app.unmount()
  }
})

it('applies the batch size limit while preserving the larger single-device limit', async () => {
  const page = render()
  try {
    const file = new File(['log'], 'large.log')
    Object.defineProperty(file, 'size', { value: 21 * 1024 * 1024 })
    page.state.acceptFile(file)
    expect(page.state.selectedFile).toBe(file)
    page.state.uploadMode = 'batch'
    await nextTick()
    page.state.acceptFile(file)
    expect(page.state.selectedFile).toBeNull()
    expect(page.state.fileError).toContain('20MB')
    page.state.submitUpload()
    expect(page.uploadBatch).not.toHaveBeenCalled()
  } finally {
    page.app.unmount()
  }
})

it('online connection checks never replace collected UMC judgements', () => {
  const precheck = {
    id: '81',
    attemptId: '20',
    deviceId: '30',
    purposeCode: 'PRECHECK',
    statusCode: 'PASSED'
  }
  const collected = {
    id: '80',
    attemptId: '20',
    deviceId: '30',
    purposeCode: 'COLLECT',
    statusCode: 'COMPLETE'
  }
  const result = {
    id: '1000',
    attemptId: '20',
    executionId: '80',
    deviceId: '30',
    reportId: '90',
    sourceCode: 'UMC',
    outcome: 'PASS',
    externalRule: { id: 'r1' }
  }
  const base = {
    attempts: [{ id: '20', mode: 'ONLINE', providerCode: 'UMC', statusCode: 'ACTIVE' }],
    results: [result]
  }
  const saved = [
    {
      reportId: '90',
      attemptId: '20',
      executionId: '80',
      deviceId: '30',
      statusCode: 'AVAILABLE',
      result: { rulelist: [{ id: 'r1' }] }
    }
  ]
  const ready = render('30', saved, { ...base, executions: [collected, precheck] }, true)
  const onlyCheck = render('30', [], { ...base, executions: [precheck] }, true)
  const running = render(
    '30',
    [],
    { ...base, executions: [collected, { ...precheck, statusCode: 'RUNNING' }] },
    true
  )
  try {
    expect(ready.state.umcComplete).toBe(true)
    expect(onlyCheck.state.umcComplete).toBe(false)
    expect(running.state.umcComplete).toBe(false)
  } finally {
    ready.app.unmount()
    onlyCheck.app.unmount()
    running.app.unmount()
  }
})

it('allows completion for a saved report with an explicitly empty rule list', () => {
  const execution = { id: '80', attemptId: '20', deviceId: '30', statusCode: 'COMPLETE' }
  const saved = {
    reportId: '90',
    attemptId: '20',
    executionId: '80',
    deviceId: '30',
    statusCode: 'AVAILABLE',
    result: { rulelist: [] }
  }
  const page = render('30', [saved], { executions: [execution], results: [] }, true)
  try {
    expect(page.state.umcComplete).toBe(true)
  } finally {
    page.app.unmount()
  }
})

it('shows every UMC rule in the existing judgement results and preserves all nested metric values', () => {
  const rules = [
    {
      id: 'a',
      ruleid: '85',
      name: '硬件检测',
      support: true,
      result: false,
      targetlist: [
        {
          id: 't',
          name: '槽位',
          valuelist: [
            { id: 'v1', object: 'slot 1', value: '0' },
            { id: 'v2', object: 'slot 2', value: '' }
          ]
        }
      ]
    },
    {
      id: 'b',
      ruleid: '86',
      name: '不支持项',
      support: false,
      error: 'unsupported',
      targetlist: []
    },
    { id: 'c', ruleid: '87', name: '异常项', support: true, result: true }
  ]
  const page = render('30', [
    {
      reportId: '90',
      attemptId: '20',
      executionId: '80',
      deviceId: '30',
      statusCode: 'AVAILABLE',
      result: { rulelist: rules }
    }
  ])
  try {
    expect(page.state.displayResults).toHaveLength(3)
    expect(page.state.displayResults.map((row: any) => row.outcome)).toEqual([
      'PASS',
      'UNJUDGED',
      'ABNORMAL'
    ])
    expect(page.state.displayResults[0].externalRule.targetlist[0].valuelist).toEqual(
      rules[0].targetlist?.[0].valuelist
    )
    expect(page.state.displayResults.map((row: any) => row.executionId)).toEqual(['80', '80', '80'])
    expect(page.state.displayResults[1].detailMessage).toContain('unsupported')
  } finally {
    page.app.unmount()
  }
})

it('hides the redundant UMC history heading unless there are actual executions outside the current view', async () => {
  const row = {
    id: '31',
    attemptId: '20',
    deviceId: '30',
    statusCode: 'COMPLETE',
    purposeCode: 'OFFLINE'
  }
  const current = render('30', [], { executions: [row] }, true)
  const oldAttempt = render(
    '30',
    [],
    { executions: [row, { ...row, id: '29', attemptId: '19' }] },
    true
  )
  const replaced = render('30', [], { executions: [{ ...row, id: '30' }, row] }, true)
  const native = render('30', [], { executions: [row] }, false)
  try {
    expect(current.state.hasExecutionHistory).toBe(false)
    expect(textOf(current.root)).not.toContain('显示历史执行与尝试')
    expect(textOf(current.root)).not.toContain('执行记录')
    for (const page of [oldAttempt, replaced]) {
      expect(page.state.hasExecutionHistory).toBe(true)
      expect(textOf(page.root)).toContain('显示历史执行与尝试')
      expect(page.state.displayedExecutions).toHaveLength(1)
      page.state.showHistory = true
      await nextTick()
      expect(page.state.displayedExecutions).toHaveLength(2)
    }
    expect(textOf(native.root)).toContain('执行记录')
    expect(textOf(native.root)).toContain('当前与历史尝试均保留')
  } finally {
    ;[current, oldAttempt, replaced, native].forEach((page) => page.app.unmount())
  }
})
