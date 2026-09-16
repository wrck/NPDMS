import { describe, expect, it } from 'vitest'
import { useUndoRedo } from '@/composables/useUndoRedo'

// useUndoRedo composable 的单元测试。
// 验证三段式历史栈（past/present/future）的 set/undo/redo/reset 行为，
// 以及 canUndo/canRedo/historySize 计算属性与 maxHistory 上限。

describe('useUndoRedo', () => {
  it('初始状态 present 为传入值，canUndo/canRedo 均为 false', () => {
    const { present, canUndo, canRedo, historySize } = useUndoRedo(1)
    expect(present.value).toBe(1)
    expect(canUndo.value).toBe(false)
    expect(canRedo.value).toBe(false)
    expect(historySize.value).toBe(0)
  })

  it('set 推进新值并把旧值压入 past，清空 future', () => {
    const { present, set, canUndo, canRedo, historySize } = useUndoRedo(1)
    set(2)
    set(3)
    expect(present.value).toBe(3)
    expect(canUndo.value).toBe(true)
    expect(canRedo.value).toBe(false)
    expect(historySize.value).toBe(2)
  })

  it('undo 回退到上一值，并把当前值压入 future', () => {
    const { present, set, undo, canRedo } = useUndoRedo(1)
    set(2)
    set(3)
    undo()
    expect(present.value).toBe(2)
    expect(canRedo.value).toBe(true)
    undo()
    expect(present.value).toBe(1)
    // 到达初始状态后 undo 不再变化
    undo()
    expect(present.value).toBe(1)
  })

  it('redo 前进到下一值，并把当前值压回 past', () => {
    const { present, set, undo, redo, canUndo, canRedo } = useUndoRedo('a')
    set('b')
    set('c')
    undo()
    expect(present.value).toBe('b')
    redo()
    expect(present.value).toBe('c')
    expect(canUndo.value).toBe(true)
    expect(canRedo.value).toBe(false)
    // future 为空时 redo 不变化
    redo()
    expect(present.value).toBe('c')
  })

  it('set 在 undo 之后会清空 future（重做链断开）', () => {
    const { present, set, undo, redo, canRedo } = useUndoRedo(1)
    set(2)
    set(3)
    undo()
    expect(canRedo.value).toBe(true)
    set(99)
    expect(canRedo.value).toBe(false)
    redo()
    // future 已清空，redo 无效
    expect(present.value).toBe(99)
  })

  it('reset 清空 past/future 并设置新 present', () => {
    const { present, set, reset, canUndo, canRedo, historySize } = useUndoRedo(1)
    set(2)
    set(3)
    reset(100)
    expect(present.value).toBe(100)
    expect(canUndo.value).toBe(false)
    expect(canRedo.value).toBe(false)
    expect(historySize.value).toBe(0)
  })

  it('超过 maxHistory 时丢弃最旧的历史（FIFO）', () => {
    const { present, set, undo, historySize } = useUndoRedo(0, 3)
    set(1)
    set(2)
    set(3)
    set(4)
    // past 上限 3，应只保留 [2,3,4] 之前的 [1,2,3]... 实际保留最近 3 步
    expect(historySize.value).toBe(3)
    expect(present.value).toBe(4)
    // 连续 undo 三次应回到 1
    undo()
    expect(present.value).toBe(3)
    undo()
    expect(present.value).toBe(2)
    undo()
    expect(present.value).toBe(1)
    // 第四次 undo 无历史
    undo()
    expect(present.value).toBe(1)
  })

  it('支持对象类型快照（调用方负责传入新快照）', () => {
    const { present, set, undo } = useUndoRedo<{ n: number }>({ n: 1 })
    set({ n: 2 })
    set({ n: 3 })
    expect(present.value.n).toBe(3)
    undo()
    expect(present.value.n).toBe(2)
    undo()
    expect(present.value.n).toBe(1)
  })
})
