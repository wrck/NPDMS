import { onBeforeUnmount, onMounted } from 'vue'

/**
 * 全局键盘快捷键 composable：注册 Ctrl/Cmd+Z 撤销、Ctrl/Cmd+Shift+Z 或 Ctrl+Y 重做。
 *
 * <p>在组件 onMounted 时注册 window keydown 监听，onBeforeUnmount 时注销，
 * 避免内存泄漏。回调可选，未提供的方向不会绑定监听逻辑。</p>
 *
 * @param callbacks onUndo / onRedo 回调
 */
export function useKeyboardShortcut(callbacks: {
  onUndo?: () => void
  onRedo?: () => void
}) {
  function handler(event: KeyboardEvent) {
    const isMac = navigator.platform.toUpperCase().indexOf('MAC') >= 0
    const ctrlOrCmd = isMac ? event.metaKey : event.ctrlKey
    if (!ctrlOrCmd) return
    // 撤销：Ctrl/Cmd + Z（非 Shift）
    if (event.key.toLowerCase() === 'z' && !event.shiftKey) {
      if (callbacks.onUndo) {
        event.preventDefault()
        callbacks.onUndo()
      }
      return
    }
    // 重做：Ctrl/Cmd + Shift + Z 或 Ctrl/Cmd + Y
    if (
      (event.key.toLowerCase() === 'z' && event.shiftKey) ||
      event.key.toLowerCase() === 'y'
    ) {
      if (callbacks.onRedo) {
        event.preventDefault()
        callbacks.onRedo()
      }
    }
  }

  onMounted(() => {
    window.addEventListener('keydown', handler)
  })

  onBeforeUnmount(() => {
    window.removeEventListener('keydown', handler)
  })
}
