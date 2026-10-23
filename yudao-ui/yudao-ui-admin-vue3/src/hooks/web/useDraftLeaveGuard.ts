import { nextTick, onBeforeUnmount, ref } from 'vue'

export type DraftLeaveDecision = 'stay' | 'retry' | 'discard'
export interface DraftLeaveOptions {
  hasPending: () => boolean
  stateKey: () => string
  isBusy: () => boolean
  save: () => Promise<boolean>
  discard: () => void
  warnBusy: () => void
}

export const useDraftLeaveGuard = (options: DraftLeaveOptions) => {
  const leaveDialogVisible = ref(false)
  let decide: ((decision: DraftLeaveDecision) => void) | undefined
  let leaving: Promise<boolean> | undefined
  let discardedState: string | undefined
  let disposed = false
  const hasPendingDraft = () => options.isBusy() || options.hasPending() && options.stateKey() !== discardedState
  const resolveLeaveDecision = (decision: DraftLeaveDecision) => {
    leaveDialogVisible.value = false
    const resolve = decide
    decide = undefined
    resolve?.(decision)
  }
  const request = async () => {
    if (disposed) return false
    if (options.isBusy()) { options.warnBusy(); return false }
    if (!hasPendingDraft()) return true
    while (true) {
      try { if (await options.save() && !options.hasPending()) return true }
      catch { /* 保存错误由原业务显示；仍保留退出选择和重试上下文。 */ }
      if (disposed) return false
      const decision = await new Promise<DraftLeaveDecision>(resolve => {
        decide = resolve
        leaveDialogVisible.value = true
      })
      if (decision === 'stay') return false
      if (options.isBusy()) { options.warnBusy(); return false }
      if (decision === 'discard') {
        options.discard()
        await nextTick()
        // 返回按钮和路由守卫可能连续检查同一内容；新的编辑会产生不同身份。
        discardedState = options.stateKey()
        return true
      }
    }
  }
  const requestLeave = () => {
    if (!leaving) leaving = request().finally(() => { leaving = undefined })
    return leaving
  }
  onBeforeUnmount(() => { disposed = true; resolveLeaveDecision('stay') })
  return { requestLeave, hasPendingDraft, leaveDialogVisible, resolveLeaveDecision }
}
