import { describe, expect, it, vi } from 'vitest'
import { execFileSync } from 'node:child_process'
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
})
