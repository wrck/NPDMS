import { get, post } from '@/utils/request'

/**
 * yudao 底座认证 API。
 *
 * <p>直接调用 yudao 原生 {@code /admin-api/system/auth/*} 接口，
 * 不再通过 PMS 包装层 {@code /api/auth/*}（已 @Deprecated）。</p>
 */

/** 登录请求参数（yudao AuthLoginReqVO） */
export interface LoginParams {
  username: string
  password: string
}

/** 登录响应（yudao AuthLoginRespVO） */
export interface LoginResult {
  userId: number
  accessToken: string
  refreshToken: string
  expiresTime: string
}

/** 用户信息（yudao AuthPermissionInfoRespVO.UserVO） */
export interface UserInfo {
  id: number
  username: string
  nickname: string
  avatar?: string
  email?: string
  deptId?: number
}

/** 权限信息响应（yudao AuthPermissionInfoRespVO） */
export interface PermissionInfo {
  user: UserInfo
  roles: string[]
  permissions: string[]
  menus: unknown[]
}

/** yudao 登录：返回 accessToken + refreshToken，不含用户信息 */
export function login(data: LoginParams): Promise<LoginResult> {
  return post<LoginResult>('/admin-api/system/auth/login', data)
}

/** yudao 登出 */
export function logout(): Promise<boolean> {
  return post<boolean>('/admin-api/system/auth/logout')
}

/** yudao 获取当前用户权限信息（含用户信息、角色、权限、菜单） */
export function getPermissionInfo(): Promise<PermissionInfo> {
  return get<PermissionInfo>('/admin-api/system/auth/get-permission-info')
}

// ============ 兼容旧 API（已弃用，待清理） ============

/**
 * @deprecated 使用 {@link getPermissionInfo} 替代。
 * 旧 PMS 包装层接口，保留仅为过渡兼容。
 */
export function getUserInfo(): Promise<UserInfo> {
  return get<UserInfo>('/api/auth/info')
}
