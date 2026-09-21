import { afterEach, expect, it, vi } from 'vitest'
import { downloadInputRecord, downloadSessionRecord } from './session-export'
import type { CollectionDetails } from '@/types/collection'
afterEach(() => { vi.unstubAllGlobals(); vi.restoreAllMocks() })
it('downloads BOM input and distinguishes empty available, unavailable and restricted without substituting createdAt', () => {
  const contents: string[] = []
  vi.stubGlobal('Blob', class { constructor(parts: string[]) { contents.push(parts.join('')) } })
  URL.createObjectURL = vi.fn(() => 'blob:test'); URL.revokeObjectURL = vi.fn()
  vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
  const details = { collectionId: 'c', namespace: 'ns', status: 'SUCCEEDED', targets: [] } as unknown as CollectionDetails
  downloadInputRecord('device', 'show original')
  expect(contents[0]).toBe('\uFEFFshow original')
  downloadSessionRecord('device', '', details, '', 'AVAILABLE', '2026-09-08')
  expect(contents[1]).not.toContain('原始脚本不可用')
  expect(contents[1]).toContain('submittedAt=unknown')
  expect(contents[1]).toContain('createdAt=2026-09-08')
  downloadSessionRecord('device', 'must not leak', details, '', 'RESTRICTED')
  expect(contents[2]).toContain('RESTRICTED')
  expect(contents[2]).not.toContain('must not leak')
})
