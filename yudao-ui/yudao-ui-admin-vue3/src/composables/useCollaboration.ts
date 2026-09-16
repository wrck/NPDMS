// src/composables/useCollaboration.ts
/**
 * 协同编辑 composable（批次5-T6，借鉴 Mendix 协同编辑）。
 *
 * <p>基于 HTTP 轮询的简化协同方案：
 * <ul>
 *   <li>join: 进入页面时调用，注册在线状态</li>
 *   <li>heartbeat: 每 10s 心跳保活</li>
 *   <li>getOnlineUsers: 每 5s 轮询在线用户列表</li>
 *   <li>getChanges: 每 3s 轮询增量变更</li>
 *   <li>broadcastChange: 用户编辑时主动上报变更</li>
 *   <li>leave: 离开页面时调用（onUnmounted 自动触发）</li>
 * </ul></p>
 *
 * <p>升级路径：将 HTTP 轮询替换为 WebSocket（y-websocket），接口语义保持不变。</p>
 */
import { ref, onUnmounted } from 'vue'
import {
  joinCollaboration,
  leaveCollaboration,
  heartbeatCollaboration,
  getOnlineUsers,
  broadcastChange,
  getChanges,
  type OnlineUser,
  type CollaborationChange
} from '@/api/lowcode-collaboration'

interface UseCollaborationOptions {
  configType: string
  configId: number
  userId: number
  userName: string
  avatar?: string
  /** 心跳间隔 ms，默认 10000 */
  heartbeatInterval?: number
  /** 在线用户轮询间隔 ms，默认 5000 */
  onlinePollInterval?: number
  /** 变更轮询间隔 ms，默认 3000 */
  changesPollInterval?: number
}

export function useCollaboration(opts: UseCollaborationOptions) {
  const onlineUsers = ref<OnlineUser[]>([])
  const recentChanges = ref<CollaborationChange[]>([])
  const lastSeq = ref(0)
  const joined = ref(false)
  // 防止竞态：组件卸载后不再启动定时器，避免定时器泄漏
  let cancelled = false

  let heartbeatTimer: ReturnType<typeof setInterval> | null = null
  let onlineTimer: ReturnType<typeof setInterval> | null = null
  let changesTimer: ReturnType<typeof setInterval> | null = null

  async function join() {
    try {
      await joinCollaboration(opts.configType, opts.configId, {
        userId: opts.userId,
        userName: opts.userName,
        avatar: opts.avatar
      })
      // 组件已卸载则不再启动定时器（竞态保护）
      if (cancelled) {
        try {
          await leaveCollaboration(opts.configType, opts.configId, opts.userId)
        } catch {
          // Best-effort cleanup after an unmount race.
        }
        return
      }
      joined.value = true
      // 立即拉取一次在线用户
      await refreshOnline()
      // 启动定时器
      if (!cancelled) startTimers()
    } catch (e) {
      console.warn('[useCollaboration] join failed', e)
    }
  }

  async function leave() {
    stopTimers()
    if (!joined.value) return
    try {
      await leaveCollaboration(opts.configType, opts.configId, opts.userId)
      joined.value = false
    } catch (e) {
      console.warn('[useCollaboration] leave failed', e)
    }
  }

  async function heartbeat() {
    if (!joined.value) return
    try {
      await heartbeatCollaboration(opts.configType, opts.configId, opts.userId)
    } catch (e) {
      console.warn('[useCollaboration] heartbeat failed', e)
    }
  }

  async function refreshOnline() {
    try {
      onlineUsers.value = await getOnlineUsers(opts.configType, opts.configId)
    } catch (e) {
      console.warn('[useCollaboration] refresh online failed', e)
    }
  }

  async function refreshChanges() {
    if (!joined.value) return
    try {
      const changes = await getChanges(opts.configType, opts.configId, lastSeq.value)
      if (changes.length > 0) {
        recentChanges.value = changes
        // 更新 lastSeq 为最新
        const maxSeq = changes.reduce((max, c) => Math.max(max, c.seq || 0), lastSeq.value)
        lastSeq.value = maxSeq
      }
    } catch (e) {
      console.warn('[useCollaboration] refresh changes failed', e)
    }
  }

  async function emitChange(change: Omit<CollaborationChange, 'userId' | 'userName'>) {
    if (!joined.value) return
    try {
      await broadcastChange(opts.configType, opts.configId, {
        ...change,
        userId: opts.userId,
        userName: opts.userName
      })
    } catch (e) {
      console.warn('[useCollaboration] emit change failed', e)
    }
  }

  function startTimers() {
    stopTimers()
    const hbInterval = opts.heartbeatInterval ?? 10000
    const onlineInterval = opts.onlinePollInterval ?? 5000
    const changesInterval = opts.changesPollInterval ?? 3000
    heartbeatTimer = setInterval(heartbeat, hbInterval)
    onlineTimer = setInterval(refreshOnline, onlineInterval)
    changesTimer = setInterval(refreshChanges, changesInterval)
  }

  function stopTimers() {
    if (heartbeatTimer) { clearInterval(heartbeatTimer); heartbeatTimer = null }
    if (onlineTimer) { clearInterval(onlineTimer); onlineTimer = null }
    if (changesTimer) { clearInterval(changesTimer); changesTimer = null }
  }

  onUnmounted(() => {
    cancelled = true
    leave()
  })

  return {
    onlineUsers,
    recentChanges,
    joined,
    join,
    leave,
    emitChange,
    refreshOnline,
    refreshChanges
  }
}
