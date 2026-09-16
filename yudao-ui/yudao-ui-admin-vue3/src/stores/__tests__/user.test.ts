import { beforeEach, describe, expect, it, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

// Hoisted mocks so we can keep stable references to the mock functions.
const mocks = vi.hoisted(() => ({
  loginApi: vi.fn(),
  logoutApi: vi.fn(),
  getUserInfoApi: vi.fn(),
  routerPush: vi.fn()
}))

// Mock the auth API module — the store delegates all HTTP work to it.
vi.mock('@/api/auth', () => ({
  login: mocks.loginApi,
  logout: mocks.logoutApi,
  getUserInfo: mocks.getUserInfoApi
}))

// Mock the router to avoid pulling in the full router + all lazy views.
vi.mock('@/router', () => ({
  default: { push: mocks.routerPush }
}))

// Mock the request module so transitively-imported axios / element-plus
// modules are not loaded. The store only needs the TOKEN_KEY constant.
vi.mock('@/utils/request', () => ({
  TOKEN_KEY: 'pms_token'
}))

import { useUserStore } from '@/stores/user'
import router from '@/router'

describe('useUserStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    vi.clearAllMocks()
  })

  describe('initial state', () => {
    it('token is empty when localStorage has no token', () => {
      const store = useUserStore()
      expect(store.token).toBe('')
      expect(store.userInfo).toBeNull()
      expect(store.permissions).toEqual([])
    })

    it('token is hydrated from localStorage', () => {
      localStorage.setItem('pms_token', 'preloaded-token')
      const store = useUserStore()
      expect(store.token).toBe('preloaded-token')
    })
  })

  describe('login', () => {
    it('stores token, userInfo and permissions and persists token', async () => {
      const mockResult = {
        token: 'fake-token',
        userInfo: {
          id: 1,
          username: 'admin',
          nickname: 'Admin',
          permissions: ['sys:user:list', 'sys:role:list']
        }
      }
      mocks.loginApi.mockResolvedValue(mockResult)

      const store = useUserStore()
      const res = await store.login({ username: 'admin', password: '123456' })

      expect(mocks.loginApi).toHaveBeenCalledWith({ username: 'admin', password: '123456' })
      expect(res).toEqual(mockResult)
      expect(store.token).toBe('fake-token')
      expect(store.userInfo).toEqual(mockResult.userInfo)
      expect(store.permissions).toEqual(['sys:user:list', 'sys:role:list'])
      expect(localStorage.getItem('pms_token')).toBe('fake-token')
    })

    it('defaults permissions to [] when userInfo has no permissions', async () => {
      mocks.loginApi.mockResolvedValue({
        token: 't',
        userInfo: { id: 1, username: 'u', nickname: 'u' }
      })

      const store = useUserStore()
      await store.login({ username: 'u', password: '123456' })

      expect(store.permissions).toEqual([])
    })

    it('does not swallow login errors (lets them propagate)', async () => {
      mocks.loginApi.mockRejectedValue(new Error('bad credentials'))

      const store = useUserStore()
      await expect(store.login({ username: 'u', password: 'wrong' })).rejects.toThrow(
        'bad credentials'
      )
      // Token should not have been set
      expect(store.token).toBe('')
      expect(localStorage.getItem('pms_token')).toBeNull()
    })
  })

  describe('fetchUserInfo', () => {
    it('stores userInfo and permissions', async () => {
      const mockInfo = {
        id: 2,
        username: 'user2',
        nickname: 'User Two',
        permissions: ['a', 'b']
      }
      mocks.getUserInfoApi.mockResolvedValue(mockInfo)

      const store = useUserStore()
      const info = await store.fetchUserInfo()

      expect(mocks.getUserInfoApi).toHaveBeenCalled()
      expect(info).toEqual(mockInfo)
      expect(store.userInfo).toEqual(mockInfo)
      expect(store.permissions).toEqual(['a', 'b'])
    })
  })

  describe('logout', () => {
    it('clears token/userInfo/permissions and redirects to /login', async () => {
      mocks.logoutApi.mockResolvedValue(undefined)

      const store = useUserStore()
      store.token = 'some-token'
      store.userInfo = { id: 1, username: 'x', nickname: 'x' }
      store.permissions = ['x']

      await store.logout()

      expect(mocks.logoutApi).toHaveBeenCalled()
      expect(store.token).toBe('')
      expect(store.userInfo).toBeNull()
      expect(store.permissions).toEqual([])
      expect(localStorage.getItem('pms_token')).toBeNull()
      expect(router.push).toHaveBeenCalledWith('/login')
    })

    it('still clears local state and redirects when the logout API fails', async () => {
      mocks.logoutApi.mockRejectedValue(new Error('network'))

      const store = useUserStore()
      store.token = 'some-token'

      await store.logout()

      expect(store.token).toBe('')
      expect(store.userInfo).toBeNull()
      expect(localStorage.getItem('pms_token')).toBeNull()
      expect(router.push).toHaveBeenCalledWith('/login')
    })
  })

  describe('reset', () => {
    it('clears local state without calling the logout API or redirecting', () => {
      const store = useUserStore()
      store.token = 'some-token'
      store.userInfo = { id: 1, username: 'x', nickname: 'x' }
      store.permissions = ['x']

      store.reset()

      expect(store.token).toBe('')
      expect(store.userInfo).toBeNull()
      expect(store.permissions).toEqual([])
      expect(localStorage.getItem('pms_token')).toBeNull()
      expect(mocks.logoutApi).not.toHaveBeenCalled()
      expect(router.push).not.toHaveBeenCalled()
    })
  })
})
