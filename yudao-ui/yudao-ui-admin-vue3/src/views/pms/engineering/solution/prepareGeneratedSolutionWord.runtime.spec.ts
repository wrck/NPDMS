// @vitest-environment jsdom
import { describe, expect, it, vi } from 'vitest'
import { execFileSync } from 'node:child_process'
import { Blob as NodeBlob, File as NodeFile } from 'node:buffer'
import JSZip from 'jszip'
vi.stubGlobal('Blob', NodeBlob)
vi.stubGlobal('File', NodeFile)
vi.mock('@/api/pms/platform/delivery', () => ({ getDeliveryTypes: async () => [{ typeCode: 'IMPLEMENTATION_PLAN' }] }))
vi.mock('@/components/DeliveryArtifact/uploadDeliveryFile', () => ({
  createDeliveryUploadAttempt: (owner: unknown, type: unknown, file: File, title: string, sourceKind: string) => ({ owner, type, file, title, sourceKind })
}))
import { prepareGeneratedSolutionWord } from './prepareGeneratedSolutionWord'

describe('implementation solution Word export', () => {
  it('rejects an unsaved solution', async () => {
    await expect(prepareGeneratedSolutionWord(0, '<p>正文</p>', '方案.docx')).rejects.toThrow('请先保存')
  })
  it('creates OOXML containing editable Chinese text, headings and table cells', async () => {
    const attempt = await prepareGeneratedSolutionWord(42, '<html><body><h1>实施方案</h1><h2>1. 项目概述</h2><p>项目背景正文</p><table><tr><th>设备名称</th><th>数量</th></tr><tr><td>防火墙</td><td>2</td></tr></table></body></html>', '方案.docx')
    expect(attempt.owner).toEqual({ ownerModule: 'SOL', entityType: 'solution', entityId: 42 })
    expect(attempt.file.type).toBe('application/vnd.openxmlformats-officedocument.wordprocessingml.document')
    const xml = execFileSync('python', ['-c', 'import sys,io,zipfile; z=zipfile.ZipFile(io.BytesIO(sys.stdin.buffer.read())); assert z.testzip() is None; sys.stdout.buffer.write(z.read("word/document.xml"))'], { input: Buffer.from(await attempt.file.arrayBuffer()) }).toString('utf8')
    expect(xml).toContain('项目背景正文')
    expect(xml).toContain('防火墙')
    expect(xml).toContain('<w:tbl>')
    expect(xml).not.toContain('altChunk')
  }, 20000)
  it('applies the inventory layout to the converted download file', async () => {
    const headers = ['产品编码', '产品型号', '产品描述', '项目数量', '发货数量', '未发货数量', '序列号']
    const values = ['02052300', 'FW1000-GA-GF', '防火墙', '2', '2', '0', '02052300N266000020、02052300N264000009']
    const html = `<html><body><table><thead><tr>${headers.map(value => `<th>${value}</th>`).join('')}</tr></thead><tbody><tr>${values.map(value => `<td>${value}</td>`).join('')}</tr></tbody></table></body></html>`
    const attempt = await prepareGeneratedSolutionWord(42, html, '方案.docx')
    const archive = await JSZip.loadAsync(await attempt.file.arrayBuffer())
    const xml = await archive.file('word/document.xml')!.async('string')
    const document = new DOMParser().parseFromString(xml, 'application/xml')
    const namespace = 'http://schemas.openxmlformats.org/wordprocessingml/2006/main'
    expect(document.getElementsByTagNameNS(namespace, 'gridCol')).toHaveLength(7)
    expect(document.getElementsByTagNameNS(namespace, 'tblHeader')).toHaveLength(1)
    expect(Array.from(document.getElementsByTagNameNS(namespace, 't')).map(node => node.textContent).join(''))
      .toContain(values[6])
  }, 20000)
})
