import { computed, isRef, ref } from 'vue'
import type { ComputedRef, Ref } from 'vue'

/**
 * 通用撤销/重做 composable（借鉴 Power Apps Studio 50 步撤销栈）。
 *
 * <p>维护三段式历史：past（已操作栈）、present（当前值）、future（重做栈）。
 * 每次调用 {@link set} 会把当前 present 压入 past（超过 maxHistory 时丢弃最旧），
 * 并清空 future；{@link undo} / {@link redo} 在 past/future 之间移动 present。</p>
 *
 * <p><b>深拷贝保护</b>：set / reset 时对入参做 JSON 深拷贝，避免外部引用污染历史快照
 * （调用方传入同一对象引用后继续 mutate 也不会影响已入栈的快照）。</p>
 *
 * <p><b>键盘快捷键</b>：通过 {@link enableKeyboard} 可选启用 Ctrl/Cmd+Z 撤销、
 * Ctrl+Y 或 Ctrl/Cmd+Shift+Z 重做，返回 cleanup 函数由调用方负责移除监听。</p>
 *
 * @param initial 初始 present 值
 * @param options 配置项（maxHistory 历史栈最大长度，默认 50）；为兼容旧调用也接受数字
 */
export interface UndoRedoOptions {
  /** 历史栈最大长度，默认 50（借鉴 Power Apps Studio） */
  maxHistory?: number
}

/** 深拷贝工具：对象/数组走 JSON 序列化避免引用污染，原始类型直接返回 */
function deepClone<T>(value: T): T {
  if (value === null || typeof value !== 'object') return value
  try {
    return JSON.parse(JSON.stringify(value)) as T
  } catch {
    // 出现循环引用等无法序列化的情况时退回原值
    return value
  }
}

export function useUndoRedo<T>(
  initial: T,
  options: UndoRedoOptions | number = {}
) {
  const maxHistory =
    typeof options === 'number' ? options : (options.maxHistory ?? 50)

  const past = ref<T[]>([]) as Ref<T[]>
  const present = ref<T>(deepClone(initial)) as Ref<T>
  const future = ref<T[]>([]) as Ref<T[]>

  /** 设置新值：当前 present 压入 past（深拷贝保护），超限则丢弃最旧，清空 future */
  function set(newValue: T) {
    past.value.push(present.value)
    if (past.value.length > maxHistory) past.value.shift()
    present.value = deepClone(newValue)
    future.value = []
  }

  /** 撤销：past 非空时把 present 压入 future，取 past 末尾为新 present */
  function undo() {
    if (past.value.length === 0) return
    future.value.push(present.value)
    present.value = past.value.pop()!
  }

  /** 重做：future 非空时把 present 压入 past，取 future 末尾为新 present */
  function redo() {
    if (future.value.length === 0) return
    past.value.push(present.value)
    present.value = future.value.pop()!
  }

  /** 重置：清空 past/future 并深拷贝设置新 present */
  function reset(value: T) {
    past.value = []
    future.value = []
    present.value = deepClone(value)
  }

  const canUndo: ComputedRef<boolean> = computed(() => past.value.length > 0)
  const canRedo: ComputedRef<boolean> = computed(() => future.value.length > 0)
  const historySize: ComputedRef<number> = computed(
    () => past.value.length + future.value.length
  )

  /**
   * 启用键盘快捷键（可选）：Ctrl/Cmd+Z 撤销、Ctrl+Y 或 Ctrl/Cmd+Shift+Z 重做。
   *
   * @param targetEl 目标元素（HTMLElement 或其 ref），缺省监听 window
   * @returns cleanup 函数，调用以移除 keydown 监听
   */
  function enableKeyboard(
    targetEl?: HTMLElement | Ref<HTMLElement | null>
  ): () => void {
    const resolved: HTMLElement | null = targetEl
      ? isRef(targetEl)
        ? targetEl.value
        : targetEl
      : null
    const target: EventTarget = resolved ?? window

    function handler(event: KeyboardEvent) {
      const isMac =
        typeof navigator !== 'undefined' &&
        navigator.platform.toUpperCase().indexOf('MAC') >= 0
      const ctrlOrCmd = isMac ? event.metaKey : event.ctrlKey
      if (!ctrlOrCmd) return
      const key = event.key.toLowerCase()
      // 撤销：Ctrl/Cmd + Z（无 Shift）
      if (key === 'z' && !event.shiftKey) {
        event.preventDefault()
        undo()
        return
      }
      // 重做：Ctrl/Cmd + Shift + Z 或 Ctrl/Cmd + Y
      if ((key === 'z' && event.shiftKey) || key === 'y') {
        event.preventDefault()
        redo()
      }
    }

    target.addEventListener('keydown', handler as EventListener)
    return () => target.removeEventListener('keydown', handler as EventListener)
  }

  return {
    present,
    set,
    undo,
    redo,
    reset,
    canUndo,
    canRedo,
    historySize,
    enableKeyboard
  }
}
