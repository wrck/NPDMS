import { describe, expect, it } from 'vitest'
import { locationSnapshotText } from './locationSnapshot'

describe('installation location snapshot display', () => {
  it('displays fallbackLocation without exposing the structured snapshot', () => {
    expect(locationSnapshotText(JSON.stringify({ fallbackLocation: '地址 / 站点 / 机房 / 机柜', site: { id: 21, name: '旧站点名' } })))
      .toBe('地址 / 站点 / 机房 / 机柜')
  })

  it('preserves historical plain text and JSON string addresses', () => {
    expect(locationSnapshotText('历史安装地址')).toBe('历史安装地址')
    expect(locationSnapshotText(JSON.stringify('历史安装地址'))).toBe('历史安装地址')
  })

  it('does not display JSON or manufacture an address when fallbackLocation is absent or invalid', () => {
    for (const snapshot of [undefined, null, '', '{}', 'null', '[]', '{"site":{"id":21}}', '{"fallbackLocation":{"id":21}}']) {
      expect(locationSnapshotText(snapshot)).toBe('')
    }
  })
})
