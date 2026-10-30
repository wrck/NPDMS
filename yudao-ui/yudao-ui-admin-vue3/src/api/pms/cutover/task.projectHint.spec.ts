import { beforeEach, expect, it, vi } from 'vitest'
import { resolveCreationDevices } from '@/api/pms/cutover/task'

const http = vi.hoisted(() => ({ post: vi.fn() }))
vi.mock('@/api/pms/cutover/request', () => ({ default: http, baseUrl: '/api/v1/pms/cutover-tasks' }))
beforeEach(() => vi.clearAllMocks())

it('preserves the original serial-only body when the project hint is absent', async () => {
  await resolveCreationDevices(['SN-401'])
  expect(http.post).toHaveBeenCalledExactlyOnceWith({ url: '/api/v1/pms/cutover-tasks/creation-devices/resolve', data: { serialNumbers: ['SN-401'] } })
})

it('keeps a bound project ID larger than Number.MAX_SAFE_INTEGER as a string', async () => {
  const projectId = '9007199254741999'
  await resolveCreationDevices(['SN-401'], projectId)
  expect(http.post).toHaveBeenCalledExactlyOnceWith({ url: '/api/v1/pms/cutover-tasks/creation-devices/resolve', data: { serialNumbers: ['SN-401'], projectId } })
})
