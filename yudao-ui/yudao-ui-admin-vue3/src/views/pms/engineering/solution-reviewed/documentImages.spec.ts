// @vitest-environment jsdom
import { afterEach, expect, it, vi } from 'vitest'
import { readFileSync } from 'node:fs'
import { execFileSync } from 'node:child_process'
import { prepareDocumentImages } from './documentImages'
import { prepareGeneratedSolutionWord } from '../solution/prepareGeneratedSolutionWord'

vi.mock('@/api/pms/platform/delivery', () => ({ getDeliveryTypes: async () => [{ typeCode: 'IMPLEMENTATION_PLAN' }] }))
vi.mock('@/components/DeliveryArtifact/uploadDeliveryFile', () => ({
  createDeliveryUploadAttempt: (owner: unknown, type: unknown, file: File) => ({ owner, type, file })
}))
afterEach(() => vi.unstubAllGlobals())

it('embeds actual image bytes into the Word package and deduplicates image reads', async () => {
  const png = readFileSync('src/views/pms/acceptance/completion-certificate/dptech-print-v2.png')
  const fetchImage = vi.fn(async () => new Response(png, { headers: { 'content-type': 'image/png' } }))
  vi.stubGlobal('fetch', fetchImage)
  const images = await prepareDocumentImages(['/files/topology.png', '/files/topology.png'])
  expect(fetchImage).toHaveBeenCalledTimes(1)
  const attempt = await prepareGeneratedSolutionWord(42,
    `<html><body><h1>拓扑图</h1><img src="${images['/files/topology.png']}" width="150" height="60" /></body></html>`, '方案.docx')
  const xml = execFileSync('python', ['-c',
    'import sys,io,zipfile; z=zipfile.ZipFile(io.BytesIO(sys.stdin.buffer.read())); assert z.testzip() is None; assert any(n.startswith("word/media/") and z.read(n).startswith(bytes([137,80,78,71])) for n in z.namelist()); sys.stdout.buffer.write(z.read("word/document.xml"))'
  ], { input: Buffer.from(await new Promise<ArrayBuffer>((resolve, reject) => {
    const reader = new FileReader()
    reader.onerror = () => reject(reader.error)
    reader.onload = () => resolve(reader.result as ArrayBuffer)
    reader.readAsArrayBuffer(attempt.file)
  })) }).toString('utf8')
  expect(xml).toContain('<w:drawing>')
  expect(xml).toContain('r:embed=')
}, 20000)

it.each([
  { status: 404, type: 'image/png' },
  { status: 200, type: 'text/html' }
])('rejects unavailable or non-image responses before document generation %j', async ({ status, type }) => {
  vi.stubGlobal('fetch', vi.fn(async () => new Response('not an image', { status, headers: { 'content-type': type } })))
  await expect(prepareDocumentImages(['/files/topology.png'])).rejects.toThrow('方案图片读取失败')
})
