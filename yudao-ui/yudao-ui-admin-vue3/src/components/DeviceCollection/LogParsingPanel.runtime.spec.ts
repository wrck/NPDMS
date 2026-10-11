import { beforeEach, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import Panel from './LogParsingPanel.vue'
import * as Api from '@/api/pms/platform/deviceLogParsing'
import { mount, passthrough, tableColumn } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

vi.mock('@/api/pms/platform/deviceLogParsing', () => ({ pageLogParses: vi.fn(), submitLogParse: vi.fn(), getLogParse: vi.fn() }))
vi.mock('@/utils/permission', () => ({ checkPermi: () => true }))
vi.mock('@/utils', () => ({ generateUUID: () => 'request' }))
vi.mock('./LogParseResult.vue', () => ({ default: {} }))
const flush = async () => { for (let i = 0; i < 12; i++) await nextTick() }
const render = () => {
  const m = mount(Panel, { projectId: 1002 }, { ElTable: passthrough, ElTableColumn: tableColumn, ElUpload: passthrough })
  return { ...m, state: (m.vm as any).$.setupState }
}
beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(Api.pageLogParses).mockResolvedValue({ list: [], total: 0 })
  vi.mocked(Api.submitLogParse).mockResolvedValue({ id: 1, state: 'SUCCEEDED' } as any)
  vi.mocked(Api.getLogParse).mockResolvedValue({ id: 1, state: 'SUCCEEDED' } as any)
})
it('automatically reads GBK before submitting the unchanged filename and UTF-8 text', async () => {
  const m = render()
  try {
    m.state.chooseFile({ name: '设备.log', raw: new File([new Uint8Array([0xba, 0xba, 0x80])], '设备.log') })
    await flush()
    expect(m.state.inputText).toBe('汉€')
    await m.state.submit()
    expect(Api.submitLogParse).toHaveBeenCalledWith(expect.objectContaining({ projectId: 1002, inputName: '设备.log', inputText: '汉€' }))
  } finally { m.app.unmount() }
})
it('reads UTF-16 BOM without requiring encoding selection', async () => {
  const m = render()
  try {
    m.state.chooseFile({ name: '设备.txt', raw: new File([new Uint8Array([0xff, 0xfe, 0x49, 0x6c])], '设备.txt') })
    await flush()
    expect(m.state.inputText).toBe('汉')
  } finally { m.app.unmount() }
})
it('clears previously valid input when the newly selected file is malformed or binary', async () => {
  const m = render()
  try {
    m.state.chooseFile({ name: '设备.txt', raw: new File(['valid'], '设备.txt') })
    await flush()
    expect(m.state.inputText).toBe('valid')
    for (const bytes of [new Uint8Array([0xff]), new Uint8Array([0x41, 0])]) {
      m.state.chooseFile({ name: '设备.txt', raw: new File([bytes], '设备.txt') })
      await flush()
      expect(m.state.inputText).toBe('')
      expect(m.state.error).toContain('无法自动读取日志')
    }
    expect(Api.submitLogParse).not.toHaveBeenCalled()
  } finally { m.app.unmount() }
})
