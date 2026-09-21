import { describe, expect, it, vi } from 'vitest'
vi.mock('@/api/device-ops', () => ({ deviceOpsApi: { get: vi.fn(), put: vi.fn(), delete: vi.fn() } }))
import { deviceOpsApi } from '@/api/device-ops'
import { scheduleApi, parseSchedule } from '@/api/schedule-management'
describe('SCHEDULE_DUE contract', () => {
  it('forces first save disabled and preserves project route scope', async () => {
    vi.mocked(deviceOpsApi.put).mockResolvedValue({ data: {} })
    const input = { namespace: 'n', projectKey: 'p/x', projectHint: 'p', deviceKeyHints: ['d'], scriptKey: 's', scriptVersion: '1', cron: '0 0 * * * *', timezone: 'UTC', callbackUri: 'https://example.test/callback', enabled: true }
    const request = parseSchedule(JSON.stringify(input), 'p/x', 'n')
    await scheduleApi.save('p/x', 's', request, false)
    expect(deviceOpsApi.put).toHaveBeenCalledWith('projects/p%2Fx/schedules/s', { ...input, enabled: false })
    expect(() => parseSchedule(JSON.stringify(input), 'other', 'n')).toThrow()
  })
})
