import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

// 首次登录检测 composable 的单元测试。
//
// 该 composable 基于 localStorage 按用户名存储「引导已读」标记，
// 因此测试通过控制 userStore.userInfo.username 与 localStorage 内容来验证行为。

// 通过 vi.hoisted 提升可变状态，使每个测试可动态切换当前用户名。
const userMock = vi.hoisted(() => ({
  userInfo: { username: 'admin' } as { username: string } | null
}))

// mock 用户 store：只暴露 composable 实际访问的 userInfo 字段。
// 使用 getter 保证对 userMock.userInfo 的重新赋值能被实时读取。
vi.mock('@/stores/user', () => ({
  useUserStore: () => ({
    get userInfo() {
      return userMock.userInfo
    }
  })
}))

import { useFirstLogin } from '@/composables/useFirstLogin'

describe('useFirstLogin', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    userMock.userInfo = { username: 'admin' }
  })

  it('首次登录（localStorage 无标记）时 isFirstLogin 应为 true', () => {
    const { isFirstLogin } = useFirstLogin()
    expect(isFirstLogin.value).toBe(true)
  })

  it('markCompleted 后 isFirstLogin 变为 false 并写入 localStorage 标记', () => {
    const { isFirstLogin, markCompleted } = useFirstLogin()
    markCompleted()
    expect(isFirstLogin.value).toBe(false)
    // 标记 key 按用户名区分
    expect(localStorage.getItem('pms_first_login_done_admin')).not.toBeNull()
  })

  it('不同用户名之间相互隔离：admin 已完成时 user2 仍为首次登录', () => {
    // 用户 admin 标记完成
    userMock.userInfo = { username: 'admin' }
    const admin = useFirstLogin()
    admin.markCompleted()
    expect(admin.isFirstLogin.value).toBe(false)
    expect(localStorage.getItem('pms_first_login_done_admin')).not.toBeNull()

    // 切换到 user2：标记不存在，仍为首次登录
    userMock.userInfo = { username: 'user2' }
    const user2 = useFirstLogin()
    expect(user2.isFirstLogin.value).toBe(true)
    expect(localStorage.getItem('pms_first_login_done_user2')).toBeNull()
  })

  it('reset 后恢复为首次登录状态并清除 localStorage 标记', () => {
    const { isFirstLogin, markCompleted, reset } = useFirstLogin()
    markCompleted()
    expect(isFirstLogin.value).toBe(false)

    reset()

    expect(isFirstLogin.value).toBe(true)
    expect(localStorage.getItem('pms_first_login_done_admin')).toBeNull()
  })

  it('未登录时降级使用 anonymous key', () => {
    userMock.userInfo = null
    const { isFirstLogin, markCompleted } = useFirstLogin()
    expect(isFirstLogin.value).toBe(true)

    markCompleted()

    expect(localStorage.getItem('pms_first_login_done_anonymous')).not.toBeNull()
  })
})
