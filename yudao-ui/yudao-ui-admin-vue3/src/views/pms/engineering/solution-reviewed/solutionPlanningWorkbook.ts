import { parsePlanningRows, planningColumns, type PlanningSection } from './solutionPlanningImport'

const spreadsheetNs = 'http://schemas.openxmlformats.org/spreadsheetml/2006/main'
const relationshipNs = 'http://schemas.openxmlformats.org/officeDocument/2006/relationships'
const packageNs = 'http://schemas.openxmlformats.org/package/2006/relationships'
const xml = (text: string) => `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>${text}`
const escapeXml = (text: string) => text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')

/** Minimal SpreadsheetML template; text formatting keeps port numbers and leading zeroes intact in Excel. */
export const planningWorkbook = async (section: PlanningSection): Promise<Uint8Array> => {
  const { default: JSZip } = await import('jszip')
  const zip = new JSZip()
  zip.file('[Content_Types].xml', xml('<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/></Types>'))
  zip.file('_rels/.rels', xml(`<Relationships xmlns="${packageNs}"><Relationship Id="rId1" Type="${relationshipNs}/officeDocument" Target="xl/workbook.xml"/></Relationships>`))
  zip.file('xl/workbook.xml', xml(`<workbook xmlns="${spreadsheetNs}" xmlns:r="${relationshipNs}"><sheets><sheet name="${section === 'interface' ? '接口互联规划' : 'IP与VLAN规划'}" sheetId="1" r:id="rId1"/></sheets></workbook>`))
  zip.file('xl/_rels/workbook.xml.rels', xml(`<Relationships xmlns="${packageNs}"><Relationship Id="rId1" Type="${relationshipNs}/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="${relationshipNs}/styles" Target="styles.xml"/></Relationships>`))
  zip.file('xl/styles.xml', xml(`<styleSheet xmlns="${spreadsheetNs}"><fonts count="1"><font><sz val="11"/><name val="Calibri"/></font></fonts><fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills><borders count="1"><border/></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="2"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="49" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/></cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>`))
  const columns = planningColumns[section]
  const cells = columns.map(([, label], index) => `<c r="${String.fromCharCode(65 + index)}1" t="inlineStr" s="1"><is><t>${escapeXml(label)}</t></is></c>`).join('')
  zip.file('xl/worksheets/sheet1.xml', xml(`<worksheet xmlns="${spreadsheetNs}"><cols><col min="1" max="${columns.length}" width="24" customWidth="1" style="1"/></cols><sheetData><row r="1">${cells}</row></sheetData></worksheet>`))
  return zip.generateAsync({ type: 'uint8array', compression: 'DEFLATE' })
}

const elements = (node: Document | Element, name: string) => Array.from(node.getElementsByTagNameNS('*', name))
const parseXml = (source: string) => {
  const document = new DOMParser().parseFromString(source, 'application/xml')
  if (elements(document, 'parsererror').length) throw new Error('XLSX文件内容损坏，请使用模板重新保存。')
  return document
}
const cellText = (node: Element) => elements(node, 't').map(text => text.textContent || '').join('')

/** Read the first worksheet referenced by the workbook, including Excel shared strings and sparse cells. */
export const parsePlanningWorkbook = async (section: PlanningSection, source: ArrayBuffer | Uint8Array): Promise<Record<string, string>[]> => {
  const { default: JSZip } = await import('jszip')
  const zip = await JSZip.loadAsync(source).catch(() => { throw new Error('请选择有效的XLSX文件。') })
  const read = async (path: string) => {
    const part = zip.file(path)
    if (!part) throw new Error('XLSX缺少工作表，请下载模板重新填写。')
    return parseXml(await part.async('string'))
  }
  const workbook = await read('xl/workbook.xml')
  const sheetId = elements(workbook, 'sheet')[0]?.getAttributeNS(relationshipNs, 'id')
  const relations = await read('xl/_rels/workbook.xml.rels')
  const sheet = elements(relations, 'Relationship').find(item => item.getAttribute('Id') === sheetId)
  if (!sheet || sheet.getAttribute('TargetMode') === 'External' || !sheet.getAttribute('Type')?.endsWith('/worksheet')) {
    throw new Error('XLSX缺少可导入的工作表，请下载模板重新填写。')
  }
  const path = new URL(sheet.getAttribute('Target') || '', 'https://workbook.invalid/xl/workbook.xml').pathname.slice(1)
  const document = await read(path)
  const strings = zip.file('xl/sharedStrings.xml')
  const shared = strings ? elements(parseXml(await strings.async('string')), 'si').map(cellText) : []
  const rowNumbers: number[] = []
  const width = planningColumns[section].length
  const rows = elements(document, 'row').map((row, index) => {
    const number = Number(row.getAttribute('r')) || index + 1
    rowNumbers.push(number)
    const values = Array<string>(width).fill('')
    elements(row, 'c').forEach((cell, cellIndex) => {
      if (elements(cell, 'f').length) throw new Error(`第${number}行包含公式，请改为文本，本次未导入任何行。`)
      const reference = cell.getAttribute('r')?.match(/^([A-Z]+)\d+$/)?.[1]
      const column = reference ? [...reference].reduce((sum, ch) => sum * 26 + ch.charCodeAt(0) - 64, 0) - 1 : cellIndex
      const type = cell.getAttribute('t')
      const raw = elements(cell, 'v')[0]?.textContent || ''
      const value = type === 's' ? shared[Number(raw)] : type === 'inlineStr' ? cellText(cell) : raw
      if (type === 'e' || value === undefined) throw new Error(`第${number}行单元格无效，本次未导入任何行。`)
      if (column >= width) {
        if (value.trim()) values[column] = value
        return
      }
      values[column] = value
    })
    return values
  })
  return parsePlanningRows({ section, rows, rowNumbers })
}
