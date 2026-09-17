import { effectScope, nextTick, reactive } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import { mergeRendererModel } from './modelState'
import { useRendererModel } from './useRendererModel'

describe('Facade renderer model lifecycle', () => {
  it('merges partial models without deleting hidden or business metadata fields', () => {
    const initial = Object.freeze({ hidden: 'keep', businessId: 17, value: 'old' })
    const patch = Object.freeze({ value: '', optional: undefined })
    const merged = mergeRendererModel(initial, patch)
    expect(merged).toEqual({ hidden: 'keep', businessId: 17, value: '', optional: undefined })
    expect(Object.hasOwn(merged, 'optional')).toBe(true)
    expect(initial.value).toBe('old')
    expect(mergeRendererModel(merged, { value: '' })).toBe(merged)
    expect(mergeRendererModel(merged, undefined)).toBe(merged)
  })

  it('preserves uncontrolled edits and pending snapshots through V1 -> V2 -> V1', () => {
    const scope = effectScope()
    try {
      const props = reactive({ version: 'v1' })
      let snapshot = { value: 'default', hidden: 'keep' }
      const readSnapshot = vi.fn(() => snapshot)
      const state = scope.run(() => useRendererModel(() => undefined, () => props.version, readSnapshot))!
      state.updateModel({ value: 'edited', hidden: 'keep' })
      snapshot = { value: 'not-yet-emitted', hidden: 'keep' }
      props.version = 'v2'
      expect(state.model.value).toEqual(snapshot)
      snapshot = { value: 'edited-in-v2', hidden: 'keep' }
      props.version = 'v1'
      expect(state.model.value).toEqual(snapshot)
      expect(readSnapshot).toHaveBeenCalledTimes(2)
    } finally {
      scope.stop()
    }
  })

  it.each(['model-first', 'version-first'])('lets a simultaneous external update win: %s', async (order) => {
    const scope = effectScope()
    try {
      const props = reactive({ version: 'v1', value: { title: 'initial' } as Record<string, unknown> })
      const state = scope.run(() => useRendererModel(
        () => props.value,
        () => props.version,
        () => ({ title: 'pending-edit', hidden: 'keep', businessId: 17 })
      ))!
      if (order === 'model-first') {
        props.value = { title: 'server-value' }
        props.version = 'v2'
      } else {
        props.version = 'v2'
        props.value = { title: 'server-value' }
      }
      await nextTick()
      expect(state.model.value).toEqual({ title: 'server-value', hidden: 'keep', businessId: 17 })
      expect(props.value).toEqual({ title: 'server-value' })
    } finally {
      scope.stop()
    }
  })

  it('keeps an explicit null, false, zero and empty values when consumers reset fields', async () => {
    const scope = effectScope()
    try {
      const props = reactive({ value: { title: 'initial' } as Record<string, unknown> })
      const state = scope.run(() => useRendererModel(() => props.value, () => 'v1', () => undefined))!
      state.updateModel({ title: '', checkbox: [], optional: null, enabled: false, quantity: 0 })
      props.value = { title: 'refilled' }
      await nextTick()
      expect(state.model.value).toEqual({ title: 'refilled', checkbox: [], optional: null, enabled: false, quantity: 0 })
    } finally {
      scope.stop()
    }
  })
})
