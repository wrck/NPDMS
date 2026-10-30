import { expect, it, vi } from 'vitest'
import Panel from './InspectionUmcReportPanel.vue'
import {
  mount,
  passthrough,
  tableColumn
} from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
vi.mock('@/utils/permission', () => ({ checkPermi: () => true }))
vi.mock('@/utils/formatTime', () => ({ formatDate: () => '2026-10-28' }))
const sample = () => ({
  id: '10',
  statusCode: 'REPORT_PENDING',
  currentAttemptId: '20',
  reports: [
    {
      id: '6',
      attemptId: '20',
      sourceCode: 'UMC',
      statusCode: 'AVAILABLE',
      files: [
        { id: 'f6', formatCode: 'DOC', artifactId: 'a6', fileVersionNo: 1, referenceKey: 'r6' }
      ]
    },
    {
      id: '7',
      attemptId: '20',
      sourceCode: 'UMC',
      statusCode: 'AVAILABLE',
      files: [
        { id: 'f7', formatCode: 'DOC', artifactId: 'a7', fileVersionNo: 1, referenceKey: 'r7' }
      ]
    }
  ],
  devices: [
    { deviceId: '30', serialNumber: 'SN-A' },
    { deviceId: '31', serialNumber: 'SN-B' }
  ],
  executions: [
    { id: '80', attemptId: '20', deviceId: '30', statusCode: 'COMPLETE', purposeCode: 'OFFLINE' },
    { id: '81', attemptId: '20', deviceId: '31', statusCode: 'COMPLETE', purposeCode: 'OFFLINE' }
  ],
  umcResults: [
    {
      reportId: '6',
      attemptId: '20',
      executionId: '80',
      deviceId: '30',
      statusCode: 'AVAILABLE',
      result: { model: 'FW-A', rulelist: [{ id: 'r1' }] }
    },
    {
      reportId: '7',
      attemptId: '20',
      executionId: '81',
      deviceId: '31',
      statusCode: 'AVAILABLE',
      result: { model: 'FW-B', rulelist: [{ id: 'r2' }] }
    }
  ],
  results: [
    { id: '100', sourceCode: 'UMC', executionId: '80', outcome: 'ABNORMAL' },
    { id: '101', sourceCode: 'UMC', executionId: '81', outcome: 'PASS' }
  ]
})
const render = (task: unknown) => {
  const page = mount(
    Panel,
    { task, disabled: false },
    {
      ElTable: passthrough,
      ElTableColumn: tableColumn,
      ElCollapse: passthrough,
      ElCollapseItem: passthrough
    }
  )
  return { ...page, state: (page.vm as any).$.setupState }
}
it('presents one local DOC per device and requires complete formal results before confirming', () => {
  const page = render(sample())
  try {
    expect(page.state.readyCount).toBe(2)
    expect(page.state.selectedResults).toHaveLength(2)
    expect(page.state.canConfirm).toBe(true)
    expect(page.state.deviceReports.map((r: any) => r.file.id)).toEqual(['f6', 'f7'])
    expect(page.state.history).toHaveLength(0)
  } finally {
    page.app.unmount()
  }
})
it('does not adopt an incomplete report, partial rule set or an execution still in progress', () => {
  const incomplete = sample()
  incomplete.results.pop()
  const missingDoc = sample()
  missingDoc.reports[1].files = []
  const wrongPhase = { ...sample(), statusCode: 'EXECUTING' }
  for (const task of [incomplete, missingDoc, wrongPhase]) {
    const page = render(task)
    try {
      expect(page.state.canConfirm).toBe(false)
    } finally {
      page.app.unmount()
    }
  }
})
it('an adopted report keeps its frozen device files instead of following later executions', () => {
  const task: any = sample()
  task.currentReportId = '9'
  task.reports.push({
    id: '9',
    sourceCode: 'UMC',
    statusCode: 'AVAILABLE',
    componentReportIds: ['6', '7'],
    resultIds: ['100', '101']
  })
  task.executions.push({
    id: '82',
    attemptId: '20',
    deviceId: '30',
    purposeCode: 'OFFLINE',
    statusCode: 'COMPLETE'
  })
  task.umcResults.push({ ...task.umcResults[0], reportId: '8', executionId: '82' })
  task.reports.push({
    ...task.reports[0],
    id: '8',
    files: [{ ...task.reports[0].files[0], id: 'later' }]
  })
  const page = render(task)
  try {
    expect(page.state.deviceReports.map((r: any) => r.file.id)).toEqual(['f6', 'f7'])
    expect(page.state.canConfirm).toBe(false)
  } finally {
    page.app.unmount()
  }
})
