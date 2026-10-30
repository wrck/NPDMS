import axios, { type AxiosRequestConfig } from 'axios'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { exportProjectDevices } from './projectDeviceExport'

const { requests } = vi.hoisted(() => ({ requests: [] as AxiosRequestConfig[] }))
vi.mock('@/config/axios', () => ({
  default: {
    download: async (request: AxiosRequestConfig) => {
      requests.push(request)
      return new Blob(['xlsx'])
    }
  }
}))

afterEach(() => {
  requests.length = 0
  vi.unstubAllEnvs()
})

describe.each(['', 'https://pms.example.invalid'])('project export with host %s', host => {
  it('resolves the equipment Business API without the platform admin prefix', async () => {
    vi.stubEnv('VITE_BASE_URL', host)
    const client = axios.create({ baseURL: `${host}/admin-api` })

    await exportProjectDevices({ projectId: '9007199254740999', kind: 'equipment' })

    expect(client.getUri(requests[0])).toBe(`${host}/api/v1/pms/projects/9007199254740999/equipment/export`)
  })

  it('resolves filtered serial exports with the precise project and serial values', async () => {
    vi.stubEnv('VITE_BASE_URL', host)
    const client = axios.create({ baseURL: `${host}/admin-api` })

    await exportProjectDevices({
      projectId: '9007199254740999', kind: 'serials',
      filters: { sn: '00123456789012345678', productCode: '02010005' }
    })

    const resolved = new URL(client.getUri(requests[0]), 'https://local.example.invalid')
    expect(resolved.pathname).toBe('/api/v1/pms/projects/9007199254740999/serials/export')
    expect(resolved.origin).toBe(host || 'https://local.example.invalid')
    expect([...resolved.searchParams.entries()]).toEqual([
      ['sn', '00123456789012345678'], ['productCode', '02010005']
    ])
  })
})
