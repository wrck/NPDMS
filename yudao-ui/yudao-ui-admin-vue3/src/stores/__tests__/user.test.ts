import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { createMemoryStorage } from '../../../tests/browserStorage'

const mocks = vi.hoisted(() => ({
  loginApi: vi.fn(),
  logoutApi: vi.fn(),
  getPermissionInfoApi: vi.fn(),
  routerPush: vi.fn()
}))

// Match the migrated API: login returns accessToken; permission info returns
// user, roles and permissions separately. The real Pinia store is exercised.
vi.mock('@/api/auth', () => ({
  login: mocks.loginApi,
  logout: mocks.logoutApi,
  getPermissionInfo: mocks.getPermissionInfoApi
}))
vi.mock('@/router', () => ({ default: { push: mocks.routerPush } }))
vi.mock('@/utils/request', () => ({ TOKEN_KEY: 'pms_token' }))

import { useUserStore } from '@/stores/user'
import router from '@/router'

describe('useUserStore', () => {
  beforeEach(() => {
    vi.stubGlobal('localStorage', createMemoryStorage())
    setActivePinia(createPinia())
    vi.resetAllMocks()
  })
  afterEach(() => vi.unstubAllGlobals())

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
      const loginResult = { accessToken: 'fake-token', refreshToken: 'refresh-token' }
      const permissionInfo = {
        user: { id: 1, username: 'admin', nickname: 'Admin' },
        permissions: ['sys:user:list', 'sys:role:list'],
        roles: ['admin'],
        menus: []
      }
      mocks.loginApi.mockResolvedValue(loginResult)
      mocks.getPermissionInfoApi.mockResolvedValue(permissionInfo)

      const store = useUserStore()
      const res = await store.login({ username: 'admin', password: '123456' })

      expect(mocks.loginApi).toHaveBeenCalledWith({ username: 'admin', password: '123456' })
      expect(mocks.getPermissionInfoApi).toHaveBeenCalledOnce()
      expect(res).toEqual(loginResult)
      expect(store.token).toBe('fake-token')
      expect(store.userInfo).toEqual(permissionInfo.user)
      expect(store.permissions).toEqual(['sys:user:list', 'sys:role:list'])
      expect(store.roles).toEqual(['admin'])
      expect(localStorage.getItem('pms_token')).toBe('fake-token')
    })

    it('defaults permissions to [] when the permission response omits them', async () => {
      mocks.loginApi.mockResolvedValue({ accessToken: 't' })
      mocks.getPermissionInfoApi.mockResolvedValue({ user: { id: 1, username: 'u', nickname: 'u' } })
      const store = useUserStore()
      await store.login({ username: 'u', password: '123456' })
      expect(store.permissions).toEqual([])
      expect(store.roles).toEqual([])
    })

    it('does not swallow login errors (lets them propagate)', async () => {
      mocks.loginApi.mockRejectedValue(new Error('bad credentials'))
      const store = useUserStore()
      await expect(store.login({ username: 'u', password: 'wrong' })).rejects.toThrow('bad credentials')
      expect(store.token).toBe('')
      expect(localStorage.getItem('pms_token')).toBeNull()
      expect(mocks.getPermissionInfoApi).not.toHaveBeenCalled()
    })

    it('clears old privileges while loading new permissions and rejects incomplete login', async () => {
      const store = useUserStore()
      store.token = 'previous-token'
      store.userInfo = { id: 1, username: 'previous-user', nickname: 'Previous' }
      store.permissions = ['*']
      store.roles = ['admin']
      localStorage.setItem('pms_token', 'previous-token')
      mocks.loginApi.mockResolvedValue({ accessToken: 'new-token' })
      mocks.getPermissionInfoApi.mockImplementation(async () => {
        expect(store.token).toBe('new-token')
        expect(store.userInfo).toBeNull()
        expect(store.permissions).toEqual([])
        expect(store.roles).toEqual([])
        throw new Error('permission bootstrap failed')
      })
      await expect(store.login({ username: 'next-user', password: 'secret' })).rejects.toThrow('permission bootstrap failed')
      expect(store.token).toBe('')
      expect(store.userInfo).toBeNull()
      expect(store.permissions).toEqual([])
      expect(store.roles).toEqual([])
      expect(localStorage.getItem('pms_token')).toBeNull()
    })
  })

  describe('fetchUserInfo', () => {
    it('stores userInfo and permissions', async () => {
      const user = { id: 2, username: 'user2', nickname: 'User Two' }
      mocks.getPermissionInfoApi.mockResolvedValue({ user, permissions: ['a', 'b'], roles: ['user'] })
      const store = useUserStore()
      const info = await store.fetchUserInfo()
      expect(mocks.getPermissionInfoApi).toHaveBeenCalled()
      expect(info).toEqual(user)
      expect(store.userInfo).toEqual(user)
      expect(store.permissions).toEqual(['a', 'b'])
      expect(store.roles).toEqual(['user'])
    })

    it('retains the complete response on the canonical permission-info method', async () => {
      const response = { user: { id: 2, username: 'user2', nickname: 'User Two' }, roles: ['user'], permissions: ['read'], menus: [] }
      mocks.getPermissionInfoApi.mockResolvedValue(response)
      const store = useUserStore()
      expect(await store.fetchPermissionInfo()).toEqual(response)
      expect(store.userInfo).toEqual(response.user)
      expect(store.roles).toEqual(['user'])
      expect(store.hasPermission('read')).toBe(true)
      expect(store.hasPermission('write')).toBe(false)
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
