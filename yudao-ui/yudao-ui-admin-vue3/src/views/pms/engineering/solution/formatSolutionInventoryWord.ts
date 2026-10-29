const WORD_NS = 'http://schemas.openxmlformats.org/wordprocessingml/2006/main'
const HEADERS = ['产品编码', '产品型号', '产品描述', '项目数量', '发货数量', '未发货数量', '序列号']
// A4 width minus the export's left/right margins, in twips.
const COLUMN_WIDTHS = [1320, 1540, 2080, 720, 720, 720, 2538]

const children = (parent: Element, name: string) => Array.from(parent.children)
  .filter(child => child.namespaceURI === WORD_NS && child.localName === name)
const text = (element: Element) => Array.from(element.getElementsByTagNameNS(WORD_NS, 't'))
  .map(node => node.textContent || '').join('')
const create = (document: Document, name: string) => document.createElementNS(WORD_NS, `w:${name}`)
const firstProperty = (parent: Element, name: string) => {
  const existing = children(parent, name)[0]
  if (existing) return existing
  const property = create(parent.ownerDocument, name)
  parent.insertBefore(property, parent.firstChild)
  return property
}

/** Format only the generated project inventory; preserve all text and other ZIP parts. */
export const formatSolutionInventoryWord = async (content: Blob | ArrayBuffer): Promise<ArrayBuffer> => {
  const { default: JSZip } = await import('jszip')
  const archive = await JSZip.loadAsync(content instanceof Blob ? await content.arrayBuffer() : content)
  const original = await archive.file('word/document.xml')?.async('string')
  if (!original) throw new Error('Word 正文缺失，无法生成实施方案')
  const document = new DOMParser().parseFromString(original, 'application/xml')
  if (document.getElementsByTagName('parsererror').length) throw new Error('Word 正文格式无效')
  let changed = false
  for (const table of Array.from(document.getElementsByTagNameNS(WORD_NS, 'tbl'))) {
    const rows = children(table, 'tr')
    const headers = rows[0] && children(rows[0], 'tc').map(text)
    if (!headers || headers.length !== HEADERS.length || headers.some((value, index) => value !== HEADERS[index])) continue
    if (rows.some(row => children(row, 'tc').length !== HEADERS.length)) throw new Error('项目清单列数不一致')
    const properties = firstProperty(table, 'tblPr')
    const width = firstProperty(properties, 'tblW')
    width.setAttributeNS(WORD_NS, 'w:type', 'dxa')
    width.setAttributeNS(WORD_NS, 'w:w', String(COLUMN_WIDTHS.reduce((sum, value) => sum + value, 0)))
    const layout = children(properties, 'tblLayout')[0] || create(document, 'tblLayout')
    layout.setAttributeNS(WORD_NS, 'w:type', 'fixed')
    properties.insertBefore(layout, Array.from(properties.children).find(child =>
      ['tblCellMar', 'tblLook', 'tblCaption', 'tblDescription', 'tblPrChange'].includes(child.localName)) || null)
    const grid = children(table, 'tblGrid')[0] || create(document, 'tblGrid')
    children(table, 'tblGrid').slice(1).forEach(duplicate => duplicate.remove())
    grid.replaceChildren(...COLUMN_WIDTHS.map(value => {
      const column = create(document, 'gridCol')
      column.setAttributeNS(WORD_NS, 'w:w', String(value))
      return column
    }))
    if (!grid.parentNode) table.insertBefore(grid, properties.nextSibling)
    rows.forEach((row, rowIndex) => {
      const rowProperties = firstProperty(row, 'trPr')
      if (rowIndex === 0) {
        // WordprocessingML tblHeader repeats the first row after automatic page breaks.
        if (!children(rowProperties, 'tblHeader').length) rowProperties.appendChild(create(document, 'tblHeader'))
      }
      children(row, 'tc').forEach((cell, index) => {
        const cellWidth = firstProperty(firstProperty(cell, 'tcPr'), 'tcW')
        cellWidth.setAttributeNS(WORD_NS, 'w:type', 'dxa')
        cellWidth.setAttributeNS(WORD_NS, 'w:w', String(COLUMN_WIDTHS[index]))
        for (const run of Array.from(cell.getElementsByTagNameNS(WORD_NS, 'r'))) {
          const runProperties = firstProperty(run, 'rPr')
          for (const name of ['sz', 'szCs']) {
            const size = children(runProperties, name)[0] || create(document, name)
            size.setAttributeNS(WORD_NS, 'w:val', '20')
            if (!size.parentNode) runProperties.appendChild(size)
          }
        }
      })
    })
    changed = true
  }
  if (!changed) return content instanceof Blob ? content.arrayBuffer() : content
  archive.file('word/document.xml', new XMLSerializer().serializeToString(document))
  return archive.generateAsync({ type: 'arraybuffer', compression: 'DEFLATE' })
}
