import { beforeEach, expect, it, vi } from 'vitest'
import { getCurrentDeviceSummaries } from './index'

const { calls, rows } = vi.hoisted(() => ({
  calls: [] as { url: string; data: { deviceIds: (number | string)[] } }[],
  rows: [] as Record<string, unknown>[]
}))
vi.mock('@/config/axios', () => ({ default: {
  post: async (request: { url: string; data: { deviceIds: (number | string)[] } }) => {
    calls.push(request)
    return rows
  }
} }))

beforeEach(() => { calls.length = 0; rows.length = 0 })

it('keeps large wire IDs exact in the request and authorized subset response', async () => {
  const large = '2113043793829797942'
  rows.push({ deviceId: large, tenantId: 1, sn: 'SN', productModel: 'MODEL', name: '设备' })

  const response = await getCurrentDeviceSummaries([large, 8], '2113043793829797943')

  expect(calls).toEqual([{ url: '/pms/asset/devices/current-summaries', data: { deviceIds: [large, 8], projectId: '2113043793829797943' } }])
  const wireRequest = JSON.parse(JSON.stringify(calls[0].data))
  expect(wireRequest.deviceIds).toEqual([large, 8])
  expect(typeof wireRequest.deviceIds[0]).toBe('string')
  const wireResponse = JSON.parse(JSON.stringify(response))
  expect(wireResponse[0].deviceId).toBe(large)
  expect(response).toHaveLength(1)
  expect(wireRequest.projectId).toBe('2113043793829797943')
  expect(Object.keys(calls[0].data)).toEqual(['deviceIds', 'projectId'])
})

it('keeps the optional project hint absent for general asset callers', async () => {
  await getCurrentDeviceSummaries([8])
  expect(calls[0].data).toEqual({ deviceIds: [8] })
})
