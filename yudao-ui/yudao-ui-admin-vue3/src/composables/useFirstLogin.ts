import { computed, ref } from 'vue'
import { useUserStore } from '@/stores/user'

/** localStorage key 前缀：按用户名区分首次登录标记 */
const FIRST_LOGIN_KEY_PREFIX = 'pms_first_login_done_'

/**
 * 首次登录检测 composable。
 *
 * <p>判定策略：基于 localStorage 中按用户名存储的「引导已读」标记。
 * 用户切换或重新登录后，新用户名对应的标记不存在时即视为首次登录。
 * 若用户名不可用（未登录），降级为匿名 key（仅在登录前调用时使用）。</p>
 *
 * <p>本实现为纯前端方案，避免引入额外的后端 user prefs 字段。
 * 如需后端持久化，可在 markCompleted() 中追加调用 user prefs 接口。</p>
 *
 * 用法：
 *   const { isFirstLogin, markCompleted } = useFirstLogin()
 *   onMounted(() => { if (isFirstLogin.value) startGuide() })
 */
export function useFirstLogin() {
  const userStore = useUserStore()

  /** 当前用户名（未登录时为空字符串） */
  const username = computed(() => userStore.userInfo?.username || '')

  /** localStorage key：按用户名区分 */
  const storageKey = computed(
    () => `${FIRST_LOGIN_KEY_PREFIX}${username.value || 'anonymous'}`
  )

  /** 是否首次登录（响应式） */
  const isFirstLogin = ref(!localStorage.getItem(storageKey.value))

  /**
   * 标记当前用户的引导已完成。
   * 写入 localStorage，并将 isFirstLogin 置为 false。
   */
  function markCompleted() {
    try {
      localStorage.setItem(storageKey.value, String(Date.now()))
    } catch {
      // localStorage 不可用时静默失败（隐私模式 / 配额满）
    }
    isFirstLogin.value = false
  }

  /**
   * 重置当前用户的引导标记（用于调试或重新触发引导）。
   */
  function reset() {
    try {
      localStorage.removeItem(storageKey.value)
    } catch {
      // ignore
    }
    isFirstLogin.value = true
  }

  return { isFirstLogin, markCompleted, reset }
}
