import type { ConfirmationDocumentKind } from './confirmationPrintForm'
interface WordPrintPdf {
  getNumberOfPages(): number
  setPage(page: number): unknown
  addImage(data: string, format: string, x: number, y: number, width: number, height: number): unknown
}

// Prepare a separate rendering tree so PDF margins do not change the visible A4 preview.
export const prepareWordPrintExport = (paper: HTMLElement) => {
  const host = document.createElement('div')
  Object.assign(host.style, { position: 'fixed', left: '-10000px', top: '0', width: '180mm' })
  const copy = paper.cloneNode(true) as HTMLElement
  Object.assign(copy.style, { width: '100%', padding: '0' })
  const sheet = copy.querySelector<HTMLElement>('.word-print-sheet')!
  Object.assign(sheet.style, { width: '100%', padding: '0', minHeight: '0' })
  copy.querySelectorAll<HTMLElement>('.word-print-header, .word-print-footer').forEach(element => { element.style.display = 'none' })
  copy.querySelectorAll<HTMLElement>('.word-print-table').forEach(table => { table.style.marginLeft = '0'; table.style.maxWidth = '100%' })
  const watermark = copy.querySelector<HTMLElement>('.word-print-watermark')!
  watermark.style.left = '-15mm'
  watermark.style.top = '112mm'
  host.appendChild(copy)
  document.body.appendChild(host)
  return { element: copy, remove: () => host.remove() }
}

export const repeatWordPrintWatermarks = (document: Document) => {
  const sheet = document.querySelector<HTMLElement>('.html2pdf__container .word-print-sheet')
  if (!sheet) return
  const watermark = sheet.querySelector<HTMLElement>('.word-print-watermark')!
  const body = sheet.querySelector<HTMLElement>('.word-print-body')!
  const pageHeight = 253
  const pages = Math.ceil(sheet.getBoundingClientRect().height / Math.floor(pageHeight * 96 / 25.4))
  for (let page = 1; page < pages; page++) {
    const copy = watermark.cloneNode(true) as HTMLElement
    copy.style.top = `${112 + page * pageHeight}mm`
    sheet.insertBefore(copy, body)
  }
}

export const addWordPrintPageDecorations = (pdf: WordPrintPdf, kind: ConfirmationDocumentKind, logo: HTMLImageElement) => {
  const canvas = document.createElement('canvas')
  canvas.width = 1280
  canvas.height = 94
  const ctx = canvas.getContext('2d')!
  const width = canvas.width / 2
  ctx.scale(2, 2)
  ctx.fillStyle = '#fff'
  ctx.fillRect(0, 0, width, 47)
  ctx.drawImage(logo, 0, 0, 91.5, 35.25)
  ctx.fillStyle = '#000'
  ctx.font = '12px SimSun, 宋体, serif'
  ctx.textAlign = 'center'
  ctx.fillText(kind === 'COMPLETION' ? '工程完工证明' : '客户满意度调查表', width / 2, 44)
  ctx.strokeStyle = '#000'
  ctx.beginPath(); ctx.moveTo(0, 46); ctx.lineTo(width, 46); ctx.stroke()
  const header = canvas.toDataURL('image/png')
  const pages = pdf.getNumberOfPages()
  for (let page = 1; page <= pages; page++) {
    pdf.setPage(page)
    pdf.addImage(header, 'PNG', 15, 8, 180, 12)
    ctx.clearRect(0, 0, width, 47)
    ctx.font = '10px SimSun, 宋体, serif'
    ctx.textAlign = 'left'
    ctx.fillText('版权所有，侵权必究 All rights reserved', 0, 14)
    ctx.textAlign = 'right'
    ctx.fillText(`第${page}页，共${pages}页 Page ${page}, Total ${pages}`, width, 14)
    pdf.addImage(canvas.toDataURL('image/png'), 'PNG', 15, 277, 180, 12)
  }
}
