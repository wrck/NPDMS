import { defineStore } from 'pinia'
import { ref } from 'vue'
import { ElNotification } from 'element-plus'

/**
 * WebSocket 全局连接 store
 * - 使用原生 WebSocket API 实现（不依赖 sockjs/stompjs）
 * - 自动重连（5 秒间隔）
 * - 收到通知时弹出 ElNotification 并累加未读数
 */
export const useWebSocketStore = defineStore('websocket', () => {
  /** 是否已连接 */
  const connected = ref(false)
  /** WebSocket 实例 */
  const ws = ref<WebSocket | null>(null)
  /** 重连定时器句柄 */
  const reconnectTimer = ref<number | null>(null)
  /** 未读通知数 */
  const unreadCount = ref(0)

  /** 建立 WebSocket 连接 */
  function connect() {
    const token = localStorage.getItem('pms_token') || ''
    if (!token) return

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const host = window.location.hostname
    const port = '8080' // 后端端口
    const url = `${protocol}//${host}:${port}/ws?token=${encodeURIComponent(token)}`

    // 关闭已有连接，避免重复
    disconnect()

    const socket = new WebSocket(url)
    ws.value = socket

    socket.onopen = () => {
      connected.value = true
      console.log('[WS] 已连接')
    }

    socket.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data) as Record<string, unknown>
        // 收到通知：弹出 ElNotification + 增加未读数
        if (data.type === 'notification' || data.title) {
          unreadCount.value++
          ElNotification({
            title: (data.title as string) || '新通知',
            message: (data.content as string) || (data.message as string) || '',
            type: 'info',
            duration: 5000,
            onClick: () => {
              // 点击跳转消息中心
              window.location.href = '/notification'
            }
          })
        }
      } catch (e) {
        console.warn('[WS] 消息解析失败', e)
      }
    }

    socket.onclose = () => {
      connected.value = false
      console.log('[WS] 连接关闭，5秒后重连')
      scheduleReconnect()
    }

    socket.onerror = (error) => {
      console.error('[WS] 连接错误', error)
    }
  }

  /** 排定一次重连任务 */
  function scheduleReconnect() {
    if (reconnectTimer.value) clearTimeout(reconnectTimer.value)
    reconnectTimer.value = window.setTimeout(() => connect(), 5000)
  }

  /** 主动断开连接（不会触发重连） */
  function disconnect() {
    if (reconnectTimer.value) {
      clearTimeout(reconnectTimer.value)
      reconnectTimer.value = null
    }
    if (ws.value) {
      // 置空 onclose 防止触发重连
      ws.value.onclose = null
      ws.value.close()
      ws.value = null
    }
    connected.value = false
  }

  /** 重置未读数 */
  function resetUnread() {
    unreadCount.value = 0
  }

  return { connected, unreadCount, connect, disconnect, resetUnread }
})
