import { afterEach, expect, it, vi } from 'vitest'
import { applyOfficeReadonlyRequest, officeProjectReadContext, officeReadonlyRequestAllowed } from './officeProjectReadonly'

afterEach(() => vi.unstubAllGlobals())
const readonlyPage = { pathname: '/pms/project/office-readonly', search: '?projectId=42&projectName=%E9%A1%B9%E7%9B%AE%2B%E7%94%B2&contractNo=C%25_' }
it('adds original exact criteria only on the isolated readonly route and preserves normal requests', () => {
  vi.stubGlobal('window', { location: readonlyPage })
  const config = { url: '/pms/projects/42', method: 'get', headers: {} }
  applyOfficeReadonlyRequest(config)
  expect(config.headers).toEqual({ 'X-Pms-Office-Project': '42', 'X-Pms-Office-Name': encodeURIComponent('项目+甲'), 'X-Pms-Office-Contract': 'C%25_' })
  expect(officeProjectReadContext()).toEqual({ projectId: '42', projectName: '项目+甲', contractNo: 'C%_' })
  vi.stubGlobal('window', { location: { pathname: '/pms/project-management/projects', search: readonlyPage.search } })
  const normal = { url: '/pms/projects', method: 'post', headers: {} }
  applyOfficeReadonlyRequest(normal)
  expect(normal.headers).toEqual({})
})

it('blocks commands, downloads, exports and access tickets including JSON and binary responses', () => {
  vi.stubGlobal('window', { location: readonlyPage })
  for (const method of ['post', 'put', 'delete', 'patch']) {
    expect(() => applyOfficeReadonlyRequest({ url: '/pms/projects/42', method, headers: {} })).toThrow('不能办理或下载')
  }
  for (const url of ['/pms/results/1/download', '/pms/training/1/pdf:access-ticket', '/pms/reports/export', '/infra/receipts/content']) {
    expect(officeReadonlyRequestAllowed('GET', url)).toBe(false)
  }
  expect(officeReadonlyRequestAllowed('GET', '/pms/raw-file', 'blob')).toBe(false)
  expect(officeReadonlyRequestAllowed('GET', '/pms/tasks?projectId=42')).toBe(true)
})
