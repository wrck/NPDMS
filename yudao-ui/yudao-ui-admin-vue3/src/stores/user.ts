import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  getPermissionInfo as getPermissionInfoApi,
  login as loginApi,
  logout as logoutApi,
  type LoginParams,
  type UserInfo,
  type PermissionInfo
} from '@/api/auth'
import { TOKEN_KEY } from '@/utils/request'
import router from '@/router'

/**
 * 用户 Store（已适配 yudao 底座认证体系）。
 *
 * <p>yudao 登录返回 {@code accessToken}（不含用户信息），需额外调用
 * {@code getPermissionInfo} 获取用户信息 + 角色 + 权限 + 菜单。</p>
 */
export const useUserStore = defineStore('user', () => {
  // Token is hydrated from localStorage so a refresh keeps the session
  const token = ref<string>(localStorage.getItem(TOKEN_KEY) || '')
  const userInfo = ref<UserInfo | null>(null)
  const permissions = ref<string[]>([])
  const roles = ref<string[]>([])

  /**
   * Check if current user has the given permission code.
   * Super admin (permissions contains '*') or exact match passes.
   */
  function hasPermission(code: string): boolean {
    if (!code) return true
    return permissions.value.includes('*') || permissions.value.includes(code)
  }

  /**
   * Check if current user has any of the given permission codes.
   */
  function hasAnyPermission(codes: string[]): boolean {
    if (!codes || codes.length === 0) return true
    return permissions.value.includes('*') || codes.some((c) => permissions.value.includes(c))
  }

  /**
   * yudao 登录流程：
   * 1. 调用 /admin-api/system/auth/login 获取 accessToken
   * 2. 调用 /admin-api/system/auth/get-permission-info 获取用户信息 + 权限
   */
  async function login(params: LoginParams) {
    const res = await loginApi(params)
    token.value = res.accessToken
    localStorage.setItem(TOKEN_KEY, res.accessToken)

    // 登录成功后立即拉取用户权限信息
    await fetchPermissionInfo()
    return res
  }

  /**
   * 拉取用户权限信息（yudao get-permission-info）。
   * 返回用户信息、角色标识、操作权限、菜单树。
   */
  async function fetchPermissionInfo(): Promise<PermissionInfo> {
    const info = await getPermissionInfoApi()
    userInfo.value = info.user
    permissions.value = info.permissions ?? []
    roles.value = info.roles ?? []
    return info
  }

  /**
   * @deprecated 使用 {@link fetchPermissionInfo} 替代。
   * 旧 PMS 接口兼容方法，过渡期保留。
   */
  async function fetchUserInfo() {
    const info = await getPermissionInfoApi()
    userInfo.value = info.user
    permissions.value = info.permissions ?? []
    roles.value = info.roles ?? []
    return info.user
  }

  async function logout() {
    try {
      await logoutApi()
    } catch {
      // Ignore logout request errors; always clear local state
    } finally {
      reset()
      router.push('/login')
    }
  }

  function reset() {
    token.value = ''
    userInfo.value = null
    permissions.value = []
    roles.value = []
    localStorage.removeItem(TOKEN_KEY)
  }

  return {
    token,
    userInfo,
    permissions,
    roles,
    hasPermission,
    hasAnyPermission,
    login,
    fetchPermissionInfo,
    fetchUserInfo,
    logout,
    reset
  }
})
