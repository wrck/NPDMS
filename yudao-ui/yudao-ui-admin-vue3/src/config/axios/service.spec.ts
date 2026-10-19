import { AxiosError, AxiosHeaders, type InternalAxiosRequestConfig } from 'axios'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

vi.unmock('@/config/axios/service')
const messages = vi.hoisted(() => ({ error: vi.fn() }))
vi.mock('element-plus', () => ({ ElMessage: messages, ElMessageBox: {}, ElNotification: {} }))
vi.mock('@/utils/auth', () => ({
  getAccessToken: vi.fn(), getRefreshToken: vi.fn(), getTenantId: vi.fn(),
  getVisitTenantId: vi.fn(), removeToken: vi.fn(), setToken: vi.fn()
}))
vi.mock('@/router', () => ({ resetRouter: vi.fn() }))
vi.mock('@/hooks/web/useCache', () => ({ deleteUserCache: vi.fn() }))
vi.mock('@/utils/encrypt', () => ({ ApiEncrypt: { getEncryptHeader: () => 'X-Encrypt' } }))

import { service } from './service'
import { cutoverPlanRecoveryAction } from '@/views/pms/cutover/shared/cutoverTaskInteraction'

const translations: Record<string, string> = {
  'sys.api.apiRequestFailed': '请求出错，请稍后重试',
  'sys.api.errorMessage': '网络连接异常，请检查网络',
  'sys.api.apiTimeoutMessage': '请求超时，请稍后重试'
}

describe('HTTP 错误信息展示', () => {
  beforeEach(() => {
    messages.error.mockClear()
    vi.stubGlobal('useI18n', () => ({ t: (key: string) => translations[key] || key }))
    vi.spyOn(console, 'log').mockImplementation(() => {})
  })
  afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals() })

  const rejectResponse = async (data: unknown, silentError = false) => {
    const rejected = new AxiosError('Request failed with status code 400', 'ERR_BAD_REQUEST')
    const promise = service.get('/api/v1/pms/cutover-tasks/test/checklist', {
      ...({ silentError } as object),
      adapter: async (config: InternalAxiosRequestConfig) => {
        rejected.config = config
        rejected.response = { data, status: 400, statusText: 'Bad Request', headers: new AxiosHeaders(), config }
        throw rejected
      }
    })
    await expect(promise).rejects.toBe(rejected)
    return rejected
  }

  it('显示字段缺项的具体原因，并保留原始错误供业务恢复处理', async () => {
    const reason = '会话同步是否开启：事实说明及特殊情况备注尚未填写'
    const rejected = await rejectResponse({ code: 1011005000, msg: reason })
    expect(messages.error).toHaveBeenCalledExactlyOnceWith(reason)
    expect(rejected.response?.status).toBe(400)
  })

  it('没有业务说明时显示友好文字，不拼接状态码', async () => {
    await rejectResponse('<html>Bad Request</html>')
    expect(messages.error).toHaveBeenCalledExactlyOnceWith('请求出错，请稍后重试')
  })

  it('尊重由页面自行呈现错误的静默请求', async () => {
    await rejectResponse({ msg: '方案内容尚未完整' }, true)
    expect(messages.error).not.toHaveBeenCalled()
  })

  it('将 HTTP 200 中明确的业务拒绝保留为响应错误，不误判为结果未知', async () => {
    const data = { code: 1011005122, msg: '存在多个有效已发布割接配置', data: null }
    const promise = service.post('/api/v1/pms/cutover-tasks', {}, {
      ...({ silentError: true, preserveBusinessError: true } as object),
      adapter: async config => ({ data, status: 200, statusText: 'OK', headers: new AxiosHeaders(), config, request: {} })
    })
    const error = await promise.catch(error => error)
    expect(error).toBeInstanceOf(AxiosError)
    expect(error.response.data).toEqual(data)
    expect(cutoverPlanRecoveryAction(error)).toBe('SURFACE_ERROR')
    expect(messages.error).not.toHaveBeenCalled()
    expect(cutoverPlanRecoveryAction(new AxiosError('Network Error'))).toBe('RETRY_SAME_KEY')
  })

  it.each([
    ['Network Error', '网络连接异常，请检查网络'],
    ['timeout of 30000ms exceeded', '请求超时，请稍后重试']
  ])('保留 %s 的友好提示', async (reason, expected) => {
    const rejected = new AxiosError(reason)
    await expect(service.get('/test', { adapter: async () => { throw rejected } })).rejects.toBe(rejected)
    expect(messages.error).toHaveBeenCalledExactlyOnceWith(expected)
  })
})
