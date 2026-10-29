// @vitest-environment jsdom
import { describe, expect, it } from 'vitest'
import JSZip from 'jszip'
import { formatSolutionInventoryWord } from './formatSolutionInventoryWord'

const NS = 'http://schemas.openxmlformats.org/wordprocessingml/2006/main'
const headers = ['产品编码', '产品型号', '产品描述', '项目数量', '发货数量', '未发货数量', '序列号']
const row = (values: string[]) => `<w:tr><w:trPr><w:cantSplit/></w:trPr>${values.map(value =>
  `<w:tc><w:tcPr/><w:p><w:r><w:t>${value}</w:t></w:r></w:p></w:tc>`).join('')}</w:tr>`
const cellText = (document: Document) => Array.from(document.getElementsByTagNameNS(NS, 'tc')).map(cell =>
  Array.from(cell.getElementsByTagNameNS(NS, 't')).map(text => text.textContent).join(''))

describe('generated solution inventory Word layout', () => {
  it('keeps every serial and ZIP part while fixing the grid and repeating the header', async () => {
    const serials = Array.from({ length: 44 }, (_, index) => `02011769T2610${String(index).padStart(6, '0')}`).join('、')
    const values = ['02011769', 'SFPP-SX-MM850-C', '万兆光模块', '44', '44', '0', serials]
    const gridXml = `<w:tblGrid>${'<w:gridCol w:w="1376"/>'.repeat(7)}</w:tblGrid>`
    const xml = `<w:document xmlns:w="${NS}"><w:body><w:tbl><w:tblPr><w:tblW w:w="9638" w:type="dxa"/><w:tblCellMar/></w:tblPr>${gridXml}${gridXml}${row(headers)}${row(values)}</w:tbl></w:body></w:document>`
    const archive = new JSZip()
    archive.file('word/document.xml', xml)
    archive.file('word/media/topology.png', new Uint8Array([137, 80, 78, 71, 1, 2, 3]))
    archive.file('word/_rels/document.xml.rels', '<Relationships>unchanged</Relationships>')
    const original = await archive.generateAsync({ type: 'arraybuffer' })

    const result = await JSZip.loadAsync(await formatSolutionInventoryWord(original))
    const document = new DOMParser().parseFromString((await result.file('word/document.xml')!.async('string')), 'application/xml')

    expect(document.getElementsByTagName('parsererror')).toHaveLength(0)
    expect(cellText(document)).toEqual([...headers, ...values])
    const grid = Array.from(document.getElementsByTagNameNS(NS, 'gridCol')).map(column => Number(column.getAttributeNS(NS, 'w')))
    expect(grid).toHaveLength(7)
    expect(new Set(grid).size).toBeGreaterThan(1)
    expect(grid.reduce((sum, value) => sum + value, 0)).toBe(9638)
    expect(grid[6]).toBeGreaterThan(grid[0])
    expect(document.getElementsByTagNameNS(NS, 'tblHeader')).toHaveLength(1)
    expect(document.getElementsByTagNameNS(NS, 'cantSplit')).toHaveLength(2)
    expect(Array.from(document.getElementsByTagNameNS(NS, 'sz')).every(size => size.getAttributeNS(NS, 'val') === '20')).toBe(true)
    expect(document.getElementsByTagNameNS(NS, 'tblLayout')[0].getAttributeNS(NS, 'type')).toBe('fixed')
    const cells = Array.from(document.getElementsByTagNameNS(NS, 'tcW')).map(cell => Number(cell.getAttributeNS(NS, 'w')))
    expect(cells).toEqual([...grid, ...grid])
    expect(await result.file('word/media/topology.png')!.async('uint8array'))
      .toEqual(await archive.file('word/media/topology.png')!.async('uint8array'))
    expect(await result.file('word/_rels/document.xml.rels')!.async('string'))
      .toEqual(await archive.file('word/_rels/document.xml.rels')!.async('string'))
  })

  it('leaves documents without the project inventory unchanged', async () => {
    const archive = new JSZip()
    archive.file('word/document.xml', `<w:document xmlns:w="${NS}"><w:body><w:tbl>${row(['设备名称', '数量'])}${row(['防火墙', '2'])}</w:tbl></w:body></w:document>`)
    const original = await archive.generateAsync({ type: 'arraybuffer' })
    expect(await formatSolutionInventoryWord(original)).toBe(original)
  })
})
