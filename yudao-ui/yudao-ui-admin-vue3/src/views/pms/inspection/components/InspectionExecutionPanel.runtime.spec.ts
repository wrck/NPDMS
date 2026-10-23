import { expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import Panel from './InspectionExecutionPanel.vue'
import {
  mount,
  passthrough,
  tableColumn
} from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

vi.mock('@/utils/dict', () => ({ getStrDictOptions: () => [] }))
vi.mock('@vueuse/core', () => ({ useMediaQuery: () => false }))
vi.mock('@/utils/formatTime', () => ({ formatDate: () => '' }))

const render = (selectedDeviceId = '30') => {
  const upload = vi.fn()
  const uploadBatch = vi.fn()
  const page = mount(
    Panel,
    {
      task: {
        id: '10',
        version: '5',
        currentAttemptId: '20',
        taskCode: 'INS10',
        attempts: [{ id: '20', mode: 'OFFLINE', statusCode: 'ACTIVE' }],
        devices: [],
        executions: [],
        results: [],
        ruleSnapshots: []
      },
      selectedDeviceId,
      disabled: false,
      commands: [],
      onUpload: upload,
      onUploadBatch: uploadBatch
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
  return { ...page, state: (page.vm as any).$.setupState, upload, uploadBatch }
}

it('keeps single-device uploads on their original event and requires a selected device', () => {
  const page = render()
  try {
    const file = new File(['one device log'], 'single.log')
    page.state.acceptFile(file)
    page.state.submitUpload()
    expect(page.upload).toHaveBeenCalledWith({ deviceId: '30', file })
    expect(page.uploadBatch).not.toHaveBeenCalled()
  } finally {
    page.app.unmount()
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
    page.state.submitUpload()
    expect(page.uploadBatch).toHaveBeenCalledWith(file)
    expect(page.upload).not.toHaveBeenCalled()
    page.state.uploadMode = 'single'
    await nextTick()
    expect(page.state.selectedFile).toBeNull()
    expect(page.state.commandHint).toBe('')
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
