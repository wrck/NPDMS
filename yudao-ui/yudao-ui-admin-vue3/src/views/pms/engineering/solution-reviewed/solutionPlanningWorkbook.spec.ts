// @vitest-environment jsdom
import { describe, expect, it } from 'vitest'
import JSZip from 'jszip'
import { planningWorkbook, parsePlanningWorkbook } from './solutionPlanningWorkbook'

const withRows = async (rows: string, section: 'interface' | 'ip' = 'interface') => {
  const zip = await JSZip.loadAsync(await planningWorkbook(section))
  const sheet = await zip.file('xl/worksheets/sheet1.xml')!.async('string')
  zip.file('xl/worksheets/sheet1.xml', sheet.replace('</sheetData>', `${rows}</sheetData>`))
  return zip
}
describe('planning XLSX workbook', () => {
  it.each(['interface', 'ip'] as const)('downloads a true %s workbook with text columns and rejects an empty template', async section => {
    const bytes = await planningWorkbook(section)
    expect([...bytes.slice(0, 2)]).toEqual([80, 75])
    const zip = await JSZip.loadAsync(bytes)
    expect(await zip.file('xl/worksheets/sheet1.xml')!.async('string')).toContain('style="1"')
    await expect(parsePlanningWorkbook(section, bytes)).rejects.toThrow('没有可导入')
  })
  it('imports Excel shared strings, inline text, literal formula-looking text and omitted optional cells', async () => {
    const zip = await withRows('<row r="2"><c r="A2" t="s"><v>0</v></c><c r="B2" t="inlineStr"><is><t>0/01</t></is></c><c r="C2" t="inlineStr"><is><t>=literal</t></is></c><c r="F2" t="inlineStr"><is><t xml:space="preserve">第一行\n第二行 &amp; 备注</t></is></c></row>')
    zip.file('xl/sharedStrings.xml', '<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><si><t>001</t></si></sst>')
    expect(await parsePlanningWorkbook('interface', await zip.generateAsync({ type: 'uint8array' }))).toEqual([
      { localDevice: '001', localPort: '0/01', peerPort: '=literal', portType: '', cableModel: '', remark: '第一行\n第二行 & 备注' }
    ])
  })
  it('rejects the other section, extra columns, malformed archives and formulas before applying any rows', async () => {
    const zip = await withRows('<row r="2"><c r="A2" t="inlineStr"><is><t>设备</t></is></c></row><row r="3"><c r="A3"><f>1+1</f><v>2</v></c></row>')
    await expect(parsePlanningWorkbook('interface', await zip.generateAsync({ type: 'uint8array' }))).rejects.toThrow('第3行包含公式')
    const extra = await withRows('<row r="2"><c r="G2"><v>1</v></c></row>')
    await expect(parsePlanningWorkbook('interface', await extra.generateAsync({ type: 'uint8array' }))).rejects.toThrow('第2行列数')
    await expect(parsePlanningWorkbook('ip', await planningWorkbook('interface'))).rejects.toThrow('表头')
    await expect(parsePlanningWorkbook('ip', new TextEncoder().encode('not an xlsx'))).rejects.toThrow('有效的XLSX')
  })
})
