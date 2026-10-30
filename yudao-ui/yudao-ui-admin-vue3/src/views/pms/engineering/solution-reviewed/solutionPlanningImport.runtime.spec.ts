import { beforeAll, describe, expect, it, vi } from 'vitest'
import { JSDOM } from 'jsdom'
import JSZip from 'jszip'
import { defineComponent, h, nextTick, ref } from 'vue'
import PlanningImport from './SolutionPlanningImport.vue'
import { planningTemplate } from './solutionPlanningImport'
import { planningWorkbook } from './solutionPlanningWorkbook'
import download from '@/utils/download'
import { mount } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

vi.mock('@/utils/download', () => ({ default: { file: vi.fn() } }))
beforeAll(() => { vi.stubGlobal('DOMParser', new JSDOM().window.DOMParser) })

const render = () => {
  const readOnly = ref(false)
  const imported = vi.fn()
  let state: any
  const wrapper = defineComponent({ setup: () => () => h(PlanningImport, {
    section: 'interface', readOnly: readOnly.value, onImported: imported,
    ref: value => { if (value) state = (value as any).$.setupState }
  }) })
  const mounted = mount(wrapper)
  const select = (text: () => Promise<string>) => { state.input = { files: [{ name: '接口.csv', text }], value: 'selected' } }
  return { ...mounted, imported, readOnly, select, get state() { return state } }
}

describe('planning spreadsheet import interaction', () => {
  it('downloads a genuine XLSX and imports the complete workbook batch', async () => {
    const mounted = render()
    try {
      await mounted.state.downloadTemplate()
      const [blob, name] = vi.mocked(download.file).mock.calls.at(-1)!
      expect(name).toBe('接口互联规划导入模板.xlsx')
      expect(blob.type).toBe('application/vnd.openxmlformats-officedocument.spreadsheetml.sheet')
      const zip = await JSZip.loadAsync(await blob.arrayBuffer())
      const sheet = await zip.file('xl/worksheets/sheet1.xml')!.async('string')
      zip.file('xl/worksheets/sheet1.xml', sheet.replace('</sheetData>', '<row r="2"><c r="A2" t="inlineStr"><is><t>001</t></is></c><c r="B2" t="inlineStr"><is><t>0/01</t></is></c></row></sheetData>'))
      const bytes = await zip.generateAsync({ type: 'uint8array' })
      mounted.state.input = { files: [{ name: '接口.xlsx', arrayBuffer: async () => bytes }], value: 'selected' }
      await mounted.state.importFile()
      expect(mounted.imported).toHaveBeenCalledWith([{ localDevice: '001', localPort: '0/01', peerPort: '', portType: '', cableModel: '', remark: '' }])
      expect(mounted.state.input.value).toBe('')
    } finally { mounted.app.unmount() }
  })
  it('rejects an XLSX for the other section without emitting any batch', async () => {
    const mounted = render()
    try {
      mounted.state.input = { files: [{ name: 'IP.xlsx', arrayBuffer: () => planningWorkbook('ip') }], value: 'selected' }
      await mounted.state.importFile()
      expect(mounted.imported).not.toHaveBeenCalled()
      expect(mounted.state.error).toContain('表头')
    } finally { mounted.app.unmount() }
  })
  it('emits the complete parsed batch and clears the selection for a repeat import', async () => {
    const mounted = render()
    try {
      mounted.select(async () => planningTemplate('interface') + '001,GE1,GE2,光口,模块,说明\r\n')
      await mounted.state.importFile()
      expect(mounted.imported).toHaveBeenCalledOnce()
      expect(mounted.imported.mock.calls[0][0]).toEqual([{ localDevice: '001', localPort: 'GE1', peerPort: 'GE2', portType: '光口', cableModel: '模块', remark: '说明' }])
      expect(mounted.state.input.value).toBe('')
    } finally { mounted.app.unmount() }
  })
  it('rejects a malformed later row without importing the preceding valid row', async () => {
    const mounted = render()
    try {
      mounted.select(async () => planningTemplate('interface') + '001,GE1,GE2,光口,模块,说明\r\nwrong,row\r\n')
      await mounted.state.importFile()
      expect(mounted.imported).not.toHaveBeenCalled()
      expect(mounted.state.error).toContain('第3行')
      expect(mounted.state.loading).toBe(false)
    } finally { mounted.app.unmount() }
  })
  it('does not import after the form becomes read-only while the file is being read', async () => {
    const mounted = render()
    let finish!: (value: string) => void
    try {
      mounted.select(() => new Promise(resolve => { finish = resolve }))
      const pending = mounted.state.importFile()
      mounted.readOnly.value = true
      await nextTick()
      finish(planningTemplate('interface') + '001,GE1,GE2,光口,模块,说明\r\n')
      await pending
      expect(mounted.imported).not.toHaveBeenCalled()
    } finally { mounted.app.unmount() }
  })
})
